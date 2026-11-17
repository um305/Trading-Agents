package com.example.data.background

import android.content.Context
import com.example.data.db.ArmedPlanEntity
import com.example.data.db.PlanOutcomeHistoryEntity
import com.example.data.notification.MasterAlertTrigger
import com.example.data.notification.PriceAlertNotificationManager
import com.example.data.repository.MarketRepository
import com.example.domain.daytrader.fundamentals.StockFundamentalsResult
import com.example.domain.daytrader.news.DayTraderNewsEventEngine
import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.replay.ReplayEngine
import com.example.domain.daytrader.risk.RiskSettings
import com.example.domain.master.MasterVerdict
import com.example.domain.master.MasterVerdictEngine
import com.example.domain.master.MasterVerdictInput
import com.example.domain.master.TradingHorizon
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.OffHoursMode
import com.example.domain.model.PositionSide
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import com.example.domain.util.PriceFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class CoordinatorRunSummary(
    val evaluatedCount: Int,
    val skippedUnchangedCount: Int,
    val alertsDispatchedCount: Int,
    val errors: List<String>
)

/**
 * Orchestrates MasterVerdict DAG evaluation with:
 * - Event-driven closed-bar filtering (only affected horizons recompute)
 * - Unchanged bar-timestamp skipping (zero redundant compute / replay)
 * - Per-symbol 20-second hard timeout budget
 * - 2,000 background vs 10,000 on-demand Replay iteration budgeting + 15m replay cache
 * - ArmedPlan monitoring + Room outcome calibration feedback
 */
class AnalysisCoordinator(
    private val context: Context,
    private val repository: MarketRepository,
    private val notificationManager: PriceAlertNotificationManager = PriceAlertNotificationManager(context)
) {
    private val lastEvaluatedBarTs = ConcurrentHashMap<String, Long>()

    companion object {
        const val PER_SYMBOL_BUDGET_MS = 20_000L
        const val BACKGROUND_REPLAY_ITERATIONS = 2_000
        const val ON_DEMAND_REPLAY_ITERATIONS = 10_000
        val MATRIX_TIMEFRAMES: List<Timeframe> = listOf(
            Timeframe.FIVE_MIN,
            Timeframe.FIFTEEN_MIN,
            Timeframe.ONE_HOUR,
            Timeframe.FOUR_HOURS,
            Timeframe.ONE_DAY,
            Timeframe.FIVE_DAYS,
            Timeframe.ONE_MONTH
        )
    }

    /**
     * Full or selective MasterVerdict evaluation for a symbol.
     * When [closedTimeframe] is provided, only horizons in [TradingHorizon.affectedByClosedTimeframe] recompute
     * and unchanged bar timestamps are skipped unless [forceRefresh] is true.
     */
    suspend fun evaluateSymbol(
        symbol: String,
        assetType: AssetType,
        marketType: TradingMarketType,
        livePriceHint: Double = 0.0,
        isBackground: Boolean = false,
        closedTimeframe: Timeframe? = null,
        forceRefresh: Boolean = false,
        offHoursMode: OffHoursMode = OffHoursMode.CAUTION,
        riskSettings: RiskSettings = RiskSettings(),
        aiShiftPoints: Int = 0,
        aiShiftModelName: String? = null,
        aiSummary: String? = null,
        seedCandlesByTf: Map<Timeframe, List<CandlePoint>> = emptyMap(),
        nowMs: Long = System.currentTimeMillis()
    ): MasterVerdict? {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        if (cleanSym.isBlank()) return null

        val result = withTimeoutOrNull(PER_SYMBOL_BUDGET_MS) {
            coroutineScope {
                val cachedEntry = repository.getCachedMasterVerdict(cleanSym, marketType)?.second
                val horizonsToCompute = if (closedTimeframe != null && cachedEntry != null && !forceRefresh) {
                    TradingHorizon.affectedByClosedTimeframe(closedTimeframe)
                } else {
                    TradingHorizon.values().toSet()
                }

                // Determine which timeframes need incremental cache fetch
                val tfsToFetch = if (closedTimeframe != null && cachedEntry != null && !forceRefresh) {
                    val needed = linkedSetOf(closedTimeframe)
                    horizonsToCompute.forEach { h ->
                        val cfg = BackgroundPreferences.getHorizonSettings(context).tripletFor(h)
                        needed.add(cfg.biasTf)
                        needed.add(cfg.setupTf)
                        needed.add(cfg.triggerTf)
                    }
                    needed.toList()
                } else {
                    MATRIX_TIMEFRAMES
                }

                // Parallel incremental closed-candle fetches
                val batchesDeferred = tfsToFetch.associateWith { tf ->
                    async(Dispatchers.IO) {
                        runCatching {
                            repository.getClosedCandlesIncremental(
                                symbol = cleanSym,
                                assetType = assetType,
                                marketType = marketType,
                                timeframe = tf,
                                minBars = 1000,
                                forceRefresh = forceRefresh
                            )
                        }.getOrNull()
                    }
                }

                val orderBookDef = async(Dispatchers.IO) {
                    runCatching { repository.fetchRealOrderBookSnapshot(cleanSym, assetType, marketType) }.getOrNull()
                }
                val futuresDef = async(Dispatchers.IO) {
                    if (assetType == AssetType.CRYPTO && marketType == TradingMarketType.FUTURES) {
                        runCatching { repository.fetchFuturesDataSafe(cleanSym, livePriceHint) }.getOrNull()
                    } else null
                }
                val newsDef = async(Dispatchers.IO) {
                    runCatching { repository.fetchNewsSafe(cleanSym, assetType) }.getOrElse { emptyList() }
                }
                val poolDef = async(Dispatchers.IO) {
                    runCatching {
                        repository.getOrBuildSessionPool(cleanSym, assetType, isFutures = marketType == TradingMarketType.FUTURES)
                    }.getOrNull()
                }
                val perpAvailDef = async(Dispatchers.IO) {
                    runCatching { repository.hasFuturesPerpMarket(cleanSym, assetType) }.getOrDefault(assetType == AssetType.CRYPTO)
                }

                val candlesByTf = linkedMapOf<Timeframe, List<CandlePoint>>()
                val ageByTf = linkedMapOf<Timeframe, Long>()
                for ((tf, def) in batchesDeferred) {
                    val batch = def.await()
                    if (batch != null && batch.candles.isNotEmpty()) {
                        candlesByTf[tf] = batch.candles
                        ageByTf[tf] = batch.dataAgeMs
                    }
                }
                for ((tf, seedList) in seedCandlesByTf) {
                    if (candlesByTf[tf].isNullOrEmpty() && seedList.isNotEmpty()) {
                        val effStep = if (tf == Timeframe.FIVE_DAYS) 7L * 86_400_000L else tf.stepMillis
                        val closedSeed = seedList.filter { it.isClosed && it.open > 0.0 && it.close > 0.0 && it.high >= it.low }
                            .ifEmpty { seedList.dropLast(1).filter { it.open > 0.0 && it.close > 0.0 && it.high >= it.low } }
                        if (closedSeed.isNotEmpty()) {
                            candlesByTf[tf] = closedSeed
                            val lastClose = closedSeed.maxOf { it.effectiveCloseTimestamp(effStep) }
                            ageByTf[tf] = (nowMs - lastClose).coerceAtLeast(0L)
                        }
                    }
                }

                // Unchanged-bar skip optimization (if not forced & closedTimeframe timestamp matches previous)
                val checkTf = closedTimeframe ?: Timeframe.FIVE_MIN
                val latestBarTs = candlesByTf[checkTf]?.lastOrNull()?.effectiveCloseTimestamp(checkTf.stepMillis) ?: 0L
                val evalKey = "${cleanSym}_${marketType.name}_${checkTf.name}"
                val prevTs = lastEvaluatedBarTs[evalKey]
                if (!forceRefresh && cachedEntry != null && latestBarTs > 0L && prevTs == latestBarTs) {
                    return@coroutineScope cachedEntry
                }

                val inferredPrice = livePriceHint.takeIf { it > 0.0 }
                    ?: candlesByTf[Timeframe.FIVE_MIN]?.lastOrNull()?.close
                    ?: candlesByTf[Timeframe.FIFTEEN_MIN]?.lastOrNull()?.close
                    ?: candlesByTf.values.firstOrNull { it.isNotEmpty() }?.lastOrNull()?.close
                    ?: return@coroutineScope cachedEntry

                val news = newsDef.await()
                val profile = if (assetType == AssetType.STOCK) MarketProfile.defaultUsStock() else MarketProfile.defaultCrypto()
                val taggedNews = DayTraderNewsEventEngine.processHeadlines(
                    rawNews = news,
                    profile = profile,
                    dayChangePercent = 0.0,
                    targetSymbol = cleanSym,
                    assetType = assetType
                )
                val eventRisk = DayTraderNewsEventEngine.assessEventRisk(cleanSym, profile, taggedNews)
                val isStdBg = isBackground && BackgroundPreferences.getMode(context) == BackgroundMonitorMode.STANDARD

                val verdict = MasterVerdictEngine.evaluate(
                    input = MasterVerdictInput(
                        symbol = cleanSym,
                        assetType = assetType,
                        marketType = marketType,
                        livePrice = inferredPrice,
                        candlesByTf = candlesByTf,
                        dataAgeByTf = ageByTf,
                        horizonSettings = BackgroundPreferences.getHorizonSettings(context),
                        replayPool = poolDef.await(),
                        replayIterations = if (isBackground) BACKGROUND_REPLAY_ITERATIONS else ON_DEMAND_REPLAY_ITERATIONS,
                        seed = ReplayEngine.DEFAULT_SEED,
                        replayProvider = { planHash, seed, _, runner ->
                            repository.cacheRepository.getCachedReplay(planHash, seed, nowMs)
                                ?: runner().also { res ->
                                    repository.cacheRepository.putCachedReplay(planHash, seed, res, nowMs)
                                }
                        },
                        orderBook = orderBookDef.await(),
                        futuresData = futuresDef.await(),
                        news = news,
                        eventRisk = eventRisk,
                        offHoursMode = offHoursMode,
                        riskSettings = riskSettings,
                        futuresPerpAvailable = perpAvailDef.await(),
                        aiShiftPoints = aiShiftPoints,
                        aiShiftModelName = aiShiftModelName,
                        aiSummary = aiSummary,
                        isEngineOnly = aiShiftPoints == 0 && aiSummary.isNullOrBlank(),
                        standardBackgroundMode = isStdBg,
                        nowMs = nowMs
                    ),
                    existingVerdict = cachedEntry,
                    horizonsToRecompute = horizonsToCompute
                )

                if (latestBarTs > 0L) {
                    lastEvaluatedBarTs[evalKey] = latestBarTs
                }

                repository.saveMasterVerdict(
                    verdict = verdict,
                    statusFlag = if (verdict.isStale) "STALE" else "FRESH"
                )
                evaluateAlertTriggers(verdict, cachedEntry, nowMs)
                evaluateArmedPlansForSymbol(cleanSym, marketType, inferredPrice, candlesByTf, nowMs)
                verdict
            }
        }

        if (result == null) {
            val prev = repository.getCachedMasterVerdict(cleanSym, marketType)?.second
            if (prev != null) {
                repository.saveMasterVerdict(
                    verdict = prev.copy(isStale = true),
                    statusFlag = "FAILED",
                    error = "Timed out (>20s per-symbol budget)"
                )
            }
        }
        return result
    }

    /**
     * Round-robin background pass covering Watchlist (max 10) + Scanner top symbols.
     */
    suspend fun runBackgroundWatchlistCycle(
        maxSymbolsPerRun: Int = 10,
        nowMs: Long = System.currentTimeMillis()
    ): CoordinatorRunSummary {
        val startMs = System.currentTimeMillis()
        val watchlist = runCatching { repository.getWatchlist().first() }.getOrElse { emptyList() }
        val armed = runCatching { repository.getArmedPlans() }.getOrElse { emptyList() }
        val defaultPairs = listOf("BTC" to AssetType.CRYPTO, "ETH" to AssetType.CRYPTO, "SOL" to AssetType.CRYPTO, "NVDA" to AssetType.STOCK, "SPY" to AssetType.STOCK)

        val candidates = (
            watchlist.map { it.symbol.uppercase(Locale.US) to (runCatching { AssetType.valueOf(it.assetType) }.getOrDefault(AssetType.CRYPTO)) } +
                armed.map { it.symbol.uppercase(Locale.US) to AssetType.CRYPTO } +
                defaultPairs
            ).distinctBy { it.first }

        if (candidates.isEmpty()) {
            return CoordinatorRunSummary(0, 0, 0, emptyList())
        }

        val cursor = BackgroundPreferences.getWatchlistCursor(context) % candidates.size
        val ordered = (0 until minOf(maxSymbolsPerRun, candidates.size)).map { offset ->
            candidates[(cursor + offset) % candidates.size]
        }
        BackgroundPreferences.setWatchlistCursor(context, (cursor + ordered.size) % candidates.size)

        // Batch 24h ticker prefetch in 1 REST call for all crypto candidates
        val cryptoSyms = ordered.filter { it.second == AssetType.CRYPTO }.map { it.first }
        val batchPrices = runCatching {
            if (cryptoSyms.isNotEmpty()) repository.fetchBinance24hTickersBatch(cryptoSyms, isFutures = true) else emptyMap()
        }.getOrElse { emptyMap() }

        var evaluated = 0
        var skipped = 0
        var alerts = 0
        val errors = mutableListOf<String>()

        for ((sym, aType) in ordered) {
            val prev = repository.getCachedMasterVerdict(sym, TradingMarketType.FUTURES)?.second
            val prevComputed = prev?.computedAtMs ?: 0L
            val priceHint = batchPrices[sym]?.first ?: 0.0
            try {
                val updated = evaluateSymbol(
                    symbol = sym,
                    assetType = aType,
                    marketType = if (aType == AssetType.CRYPTO) TradingMarketType.FUTURES else TradingMarketType.SPOT,
                    livePriceHint = priceHint,
                    isBackground = true,
                    nowMs = nowMs
                )
                if (updated != null && updated.computedAtMs == prevComputed) {
                    skipped++
                } else {
                    evaluated++
                }
            } catch (e: Exception) {
                errors.add("$sym: ${e.message ?: "error"}")
            }
        }

        val duration = System.currentTimeMillis() - startMs
        val nextRun = nowMs + 15L * 60_000L
        BackgroundPreferences.recordRunDiagnostics(
            context = context,
            lastRunMs = nowMs,
            nextRunMs = nextRun,
            symbolsCovered = ordered.size,
            durationMs = duration,
            errors = errors
        )
        return CoordinatorRunSummary(evaluated, skipped, alerts, errors)
    }

    private suspend fun evaluateAlertTriggers(
        verdict: MasterVerdict,
        previous: MasterVerdict?,
        nowMs: Long
    ) {
        val armedForSym = repository.getArmedPlans().filter { it.symbol.equals(verdict.symbol, true) }
        val highImpactNews = verdict.matrix.cells.firstOrNull {
            it.agentId == com.example.domain.master.AgentId.SESSION_EVENT &&
                it.reasons.any { r -> r.contains("NEWS SHOCK", true) }
        }
        if (highImpactNews != null && armedForSym.isNotEmpty()) {
            notificationManager.sendGroupedMasterAlert(
                symbol = verdict.symbol,
                horizon = null,
                trigger = MasterAlertTrigger.NEWS_EVENT_CAUTION,
                title = "${verdict.symbol} High-Impact Event Caution",
                message = highImpactNews.reasons.firstOrNull() ?: "Elevated news flow detected",
                nowMs = nowMs
            )
        }

        if (previous != null && previous.weightedBiasSide != null && verdict.weightedBiasSide != null &&
            previous.weightedBiasSide != verdict.weightedBiasSide && armedForSym.isNotEmpty()
        ) {
            notificationManager.sendGroupedMasterAlert(
                symbol = verdict.symbol,
                horizon = TradingHorizon.SWING,
                trigger = MasterAlertTrigger.HIGHER_TF_BIAS_FLIP,
                title = "${verdict.symbol} Bias Flipped to ${verdict.weightedBiasSide.name}",
                message = verdict.whatFlipsBias,
                nowMs = nowMs
            )
        }

        for ((horizon, plan) in verdict.horizonPlans) {
            val prevPlan = previous?.horizonPlans?.get(horizon)
            val isArmed = armedForSym.any { it.horizon == horizon.name }
            if (isArmed && plan.state == SignalVerdictState.TRADE && prevPlan?.state != SignalVerdictState.TRADE && plan.side != null) {
                notificationManager.sendGroupedMasterAlert(
                    symbol = verdict.symbol,
                    horizon = horizon,
                    trigger = MasterAlertTrigger.HORIZON_ARMED_TRADE,
                    title = "${verdict.symbol} ${horizon.label} ARMED (${plan.side.name})",
                    message = "Entry ${PriceFormatter.formatCurrency(plan.limitPrice ?: verdict.livePrice)} • SL ${plan.formatStopOrDash()}",
                    nowMs = nowMs
                )
            }
        }
    }

    private suspend fun evaluateArmedPlansForSymbol(
        symbol: String,
        marketType: TradingMarketType,
        livePrice: Double,
        candlesByTf: Map<Timeframe, List<CandlePoint>>,
        nowMs: Long
    ) {
        val armedPlans = repository.getArmedPlans().filter {
            it.symbol.equals(symbol, true) && it.marketType == marketType.name
        }
        if (armedPlans.isEmpty()) return

        for (armed in armedPlans) {
            val horizon = runCatching { TradingHorizon.valueOf(armed.horizon) }.getOrNull() ?: continue
            val triggerBars = candlesByTf[horizon.defaultTriggerTf].orEmpty()
            val lastClosed = triggerBars.lastOrNull() ?: continue
            val isLong = armed.side.equals("LONG", true)
            val risk = kotlin.math.abs(armed.triggerPrice - armed.stopLoss).coerceAtLeast(armed.triggerPrice * 0.002)

            // 1. Price entered zone check
            if (!armed.isEnteredZone && livePrice in armed.entryZoneMin..armed.entryZoneMax) {
                repository.upsertArmedPlan(armed.copy(isEnteredZone = true))
                notificationManager.sendGroupedMasterAlert(
                    symbol = symbol,
                    horizon = horizon,
                    trigger = MasterAlertTrigger.PRICE_IN_ARMED_ZONE,
                    title = "$symbol ${horizon.label} Price In Zone",
                    message = "Live ${PriceFormatter.formatCurrency(livePrice)} entered [${PriceFormatter.formatZoneRange(armed.entryZoneMin, armed.entryZoneMax)}]",
                    nowMs = nowMs
                )
            }

            // 2. Closed-bar TP / SL resolution & Room Outcome Calibration sync
            val hitSl = if (isLong) lastClosed.low <= armed.stopLoss else lastClosed.high >= armed.stopLoss
            val hitTp1 = if (isLong) lastClosed.high >= armed.tp1 else lastClosed.low <= armed.tp1

            if (hitSl && armed.stopLoss > 0.0) {
                notificationManager.sendGroupedMasterAlert(
                    symbol = symbol,
                    horizon = horizon,
                    trigger = MasterAlertTrigger.STOP_LOSS_HIT,
                    title = "$symbol ${horizon.label} STOP HIT",
                    message = "Invalidation breached at ${PriceFormatter.formatCurrency(armed.stopLoss)}",
                    nowMs = nowMs
                )
                repository.recordPlanOutcomeAndCalibrate(
                    PlanOutcomeHistoryEntity(
                        symbol = symbol,
                        marketType = marketType.name,
                        horizon = horizon.name,
                        entryModel = armed.entryModel,
                        regime = "TREND",
                        isWithTrend = true,
                        sessionStatus = "OPEN",
                        outcomeType = "SL",
                        isWin = false,
                        realizedR = -1.0,
                        resolvedAt = nowMs
                    )
                )
                repository.deleteArmedPlan(armed.id)
            } else if (hitTp1 && armed.tp1 > 0.0) {
                val tpR = kotlin.math.abs(armed.tp1 - armed.triggerPrice) / risk
                notificationManager.sendGroupedMasterAlert(
                    symbol = symbol,
                    horizon = horizon,
                    trigger = MasterAlertTrigger.TP_HIT,
                    title = "$symbol ${horizon.label} TP1 HIT (+${String.format(Locale.US, "%.1f", tpR)}R)",
                    message = "Target ${PriceFormatter.formatCurrency(armed.tp1)} reached.",
                    nowMs = nowMs
                )
                repository.recordPlanOutcomeAndCalibrate(
                    PlanOutcomeHistoryEntity(
                        symbol = symbol,
                        marketType = marketType.name,
                        horizon = horizon.name,
                        entryModel = armed.entryModel,
                        regime = "TREND",
                        isWithTrend = true,
                        sessionStatus = "OPEN",
                        outcomeType = "TP1",
                        isWin = true,
                        realizedR = tpR,
                        resolvedAt = nowMs
                    )
                )
                repository.deleteArmedPlan(armed.id)
            }
        }
    }
}
