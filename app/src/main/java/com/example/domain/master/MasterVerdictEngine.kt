package com.example.domain.master

import com.example.domain.analytics.QuantitativeEngine
import com.example.domain.daytrader.fundamentals.StockFundamentalsResult
import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.model.MarketProfileType
import com.example.domain.daytrader.model.TimeWindow
import com.example.domain.daytrader.news.EventRiskAssessment
import com.example.domain.daytrader.replay.ReplayEngine
import com.example.domain.daytrader.replay.ReplayPlan
import com.example.domain.daytrader.replay.ReplayResult
import com.example.domain.daytrader.replay.SessionPool
import com.example.domain.daytrader.risk.RiskSettings
import com.example.domain.daytrader.session.MarketCalendar
import com.example.domain.engine.SignalCalibrationEngine
import com.example.domain.engine.SignalEngineV2
import com.example.domain.engine.SignalVetoDetector
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.CryptoFuturesData
import com.example.domain.model.LiquidityType
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSessionStatus
import com.example.domain.model.NewsItem
import com.example.domain.model.OffHoursMode
import com.example.domain.model.OrderBookSnapshot
import com.example.domain.model.OrderBlockType
import com.example.domain.model.PositionSide
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.SoftFlag
import com.example.domain.model.SoftFlagType
import com.example.domain.model.TechnicalIndicators
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import com.example.domain.util.PriceFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

data class MasterVerdictInput(
    val symbol: String,
    val assetType: AssetType,
    val marketType: TradingMarketType,
    val livePrice: Double,
    val candlesByTf: Map<Timeframe, List<CandlePoint>>,
    val dataAgeByTf: Map<Timeframe, Long> = emptyMap(),
    val horizonSettings: HorizonSettings = HorizonSettings(),
    val replayPool: SessionPool? = null,
    val replayIterations: Int = 2_000,
    val seed: Long = ReplayEngine.DEFAULT_SEED,
    val replayProvider: ((planHash: String, seed: Long, iterations: Int, runner: () -> ReplayResult) -> ReplayResult)? = null,
    val orderBook: OrderBookSnapshot? = null,
    val futuresData: CryptoFuturesData? = null,
    val news: List<NewsItem> = emptyList(),
    val eventRisk: EventRiskAssessment? = null,
    val fundamentals: StockFundamentalsResult? = null,
    val offHoursMode: OffHoursMode = OffHoursMode.CAUTION,
    val spreadPercent: Double = 0.02,
    val riskSettings: RiskSettings = RiskSettings(),
    val dailyTradesTaken: Int = 0,
    val realizedRToday: Double = 0.0,
    val futuresPerpAvailable: Boolean = (assetType == AssetType.CRYPTO),
    val aiShiftPoints: Int = 0,
    val aiShiftModelName: String? = null,
    val aiSummary: String? = null,
    val aiCallback: (suspend (tf: Timeframe) -> Pair<Double, String>)? = null,
    val isEngineOnly: Boolean = true,
    val standardBackgroundMode: Boolean = false,
    val customStockEntryWindows: List<TimeWindow>? = null,
    val nowMs: Long = System.currentTimeMillis()
)

object MasterVerdictEngine {

    suspend fun evaluate(
        input: MasterVerdictInput,
        existingVerdict: MasterVerdict? = null,
        horizonsToRecompute: Set<TradingHorizon> = TradingHorizon.values().toSet()
    ): MasterVerdict {
        val cleanSym = input.symbol.trim().uppercase(Locale.US)
        val closedByTf = input.candlesByTf.mapValues { (_, list) ->
            list.filter { it.isClosed && it.open > 0.0 && it.close > 0.0 && it.high >= it.low }.sortedBy { it.timestamp }
        }
        val safeLivePrice = input.livePrice.takeIf { it > 0.0 && !it.isNaN() && !it.isInfinite() }
            ?: closedByTf[Timeframe.FIVE_MIN]?.lastOrNull()?.close
            ?: closedByTf[Timeframe.FIFTEEN_MIN]?.lastOrNull()?.close
            ?: closedByTf.values.firstOrNull { it.isNotEmpty() }?.lastOrNull()?.close
            ?: 0.0

        val indicatorsByTf = linkedMapOf<Timeframe, TechnicalIndicators>()
        for ((tf, bars) in closedByTf) {
            if (bars.isNotEmpty()) {
                indicatorsByTf[tf] = QuantitativeEngine.computeIndicators(bars)
            }
        }

        // First evaluate initial horizon plans & replay cache to feed Matrix Replay row
        val replayByTf = linkedMapOf<Timeframe, ReplayResult>()
        val rawHorizonPlans = linkedMapOf<TradingHorizon, HorizonPlan>()

        // Establish preliminary macro directional bias from 4h / 1D / 1W so counter-trend plans are properly tagged
        val prelimMacroScore = computeMacroDirectionScore(safeLivePrice, indicatorsByTf, closedByTf)
        val macroBiasSide = when {
            prelimMacroScore > 0.08 -> PositionSide.LONG
            prelimMacroScore < -0.08 -> PositionSide.SHORT
            else -> null
        }

        for (horizon in TradingHorizon.ACTIONABLE) {
            val prevPlan = existingVerdict?.horizonPlans?.get(horizon)
            if (prevPlan != null && horizon !in horizonsToRecompute) {
                rawHorizonPlans[horizon] = prevPlan
                continue
            }
            val built = evaluateActionableHorizon(
                horizon = horizon,
                cleanSymbol = cleanSym,
                safeLivePrice = safeLivePrice,
                input = input,
                closedByTf = closedByTf,
                indicatorsByTf = indicatorsByTf,
                macroBiasSide = macroBiasSide
            )
            rawHorizonPlans[horizon] = built.first
            built.second?.let { rep ->
                replayByTf[built.first.setupTf] = rep
                replayByTf[built.first.triggerTf] = rep
            }
        }

        // Evaluate 17 agents x 7 timeframes DAG matrix
        val matrix = AgentMatrixEvaluator.evaluateMatrix(
            MatrixEvaluationInput(
                symbol = cleanSym,
                assetType = input.assetType,
                marketType = input.marketType,
                livePrice = safeLivePrice,
                candlesByTf = closedByTf,
                dataAgeByTf = input.dataAgeByTf,
                indicatorsByTf = indicatorsByTf,
                orderBook = input.orderBook,
                futuresData = input.futuresData,
                news = input.news,
                eventRisk = input.eventRisk,
                fundamentals = input.fundamentals,
                replayByTf = replayByTf,
                offHoursMode = input.offHoursMode,
                spreadPercent = input.spreadPercent,
                accountConfigured = input.riskSettings.isConfigured,
                dailyTradesTaken = input.dailyTradesTaken,
                maxTradesPerDay = input.riskSettings.maxTradesPerDay,
                realizedRToday = input.realizedRToday,
                dailyLossLimitR = input.riskSettings.dailyLossLimitR,
                nowMs = input.nowMs,
                aiShiftPoints = input.aiShiftPoints,
                aiSummary = input.aiSummary,
                aiCallback = input.aiCallback
            )
        )

        // Attach AI matrix vote if present into each horizon's agentVotes map
        val horizonPlans = rawHorizonPlans.mapValues { (_, hp) ->
            val matrixVotesForSetup = matrix.cells
                .filter { it.timeframe == hp.setupTf }
                .associate { it.agentId to it.stance }
            val mergedVotes = AgentId.values().associateWith { id ->
                matrixVotesForSetup[id] ?: hp.agentVotes[id] ?: 0.0
            }
            val failedAgents = matrix.cells.filter { it.timeframe == hp.setupTf && it.status == AgentStatus.FAILED }
                .map { "${it.agentId.shortName} FAILED" }
            hp.copy(
                agentVotes = mergedVotes,
                conflicts = (hp.conflicts + failedAgents).distinct()
            )
        }

        // Weighted bias aggregation: POSITION 0.30, SWING 0.30, INTRADAY 0.25, SCALP 0.15
        val clampedAiShiftPts = input.aiShiftPoints.coerceIn(-15, 15)
        var signedWeightSum = 0.0
        var totalActiveWeight = 0.0
        for (h in TradingHorizon.ACTIONABLE) {
            val hp = horizonPlans[h] ?: continue
            val w = h.aggregationWeight
            totalActiveWeight += w
            val dirSign = when (hp.side) {
                PositionSide.LONG -> 1.0
                PositionSide.SHORT -> -1.0
                null -> 0.0
            }
            val confidenceWeight = when (hp.grade) {
                SetupGrade.A -> 1.0
                SetupGrade.B -> 0.85
                SetupGrade.C_WATCH -> 0.65
            }
            signedWeightSum += dirSign * w * confidenceWeight
        }

        // Optional AI synthesis shifts weighted alignment at most ±15 pts without touching numbers or vetoes
        val rawNorm = if (totalActiveWeight > 0.0) signedWeightSum / totalActiveWeight else 0.0
        val aiDeltaNorm = (clampedAiShiftPts / 100.0).coerceIn(-0.15, 0.15)
        val combinedNorm = (rawNorm + aiDeltaNorm).coerceIn(-1.0, 1.0)

        val weightedSide = when {
            combinedNorm > 0.08 -> PositionSide.LONG
            combinedNorm < -0.08 -> PositionSide.SHORT
            else -> null
        }

        val alignedWeight = TradingHorizon.ACTIONABLE.sumOf { h ->
            val hp = horizonPlans[h]
            if (hp != null && hp.side != null && hp.side == weightedSide) h.aggregationWeight else 0.0
        }
        val baseAlignPct = (alignedWeight * 100.0).roundToInt()
        val alignmentPercent = (baseAlignPct + if (weightedSide == PositionSide.LONG) clampedAiShiftPts else -clampedAiShiftPts)
            .coerceIn(0, 100)

        // Conflicts across horizons
        val conflicts = mutableListOf<String>()
        val scalpSide = horizonPlans[TradingHorizon.SCALP]?.side
        val intradaySide = horizonPlans[TradingHorizon.INTRADAY]?.side
        val swingSide = horizonPlans[TradingHorizon.SWING]?.side
        val positionSide = horizonPlans[TradingHorizon.POSITION]?.side
        if (scalpSide != null && swingSide != null && scalpSide != swingSide) {
            conflicts.add("Scalp ${scalpSide.name} opposes Swing ${swingSide.name}")
        }
        if (scalpSide != null && intradaySide != null && scalpSide != intradaySide) {
            conflicts.add("Scalp ${scalpSide.name} opposes Intraday ${intradaySide.name}")
        }
        if (intradaySide != null && positionSide != null && intradaySide != positionSide) {
            conflicts.add("Intraday ${intradaySide.name} opposes Position ${positionSide.name}")
        }
        if (matrix.failedCount > 0) {
            conflicts.add("${matrix.failedCount} matrix cells FAILED (timeout/unavailable)")
        }

        // Tag counter-trend plans against weightedSide if needed
        val finalPlans = horizonPlans.mapValues { (h, hp) ->
            val opposesWeighted = weightedSide != null && hp.side != null && hp.side != weightedSide
            if (opposesWeighted && !hp.isCounterTrend) {
                val ctFlag = SoftFlag(
                    type = SoftFlagType.HTF_OPPOSED,
                    label = "COUNTER-TREND (0.5x)",
                    detail = "${h.label} ${hp.side?.name} opposes higher-timeframe ${weightedSide?.name} bias.",
                    actionHint = "Reduce size 0.5x or wait for pullback on trend-aligned horizon"
                )
                hp.copy(
                    isCounterTrend = true,
                    sizeMultiplier = minOf(hp.sizeMultiplier, 0.5),
                    softFlags = (hp.softFlags + ctFlag).distinctBy { it.type }
                )
            } else {
                hp
            }
        }

        // Best action now: highest EV TRADE, else best WAIT; NO_TRADE only when all hard-vetoed
        val viablePlans = finalPlans.values.filter { it.state != SignalVerdictState.NO_TRADE }
        val tradePlans = viablePlans.filter { it.state == SignalVerdictState.TRADE }
        val rankedPool = tradePlans.ifEmpty { viablePlans }
        val bestPlan = rankedPool.sortedWith(
            compareByDescending<HorizonPlan> { if (!it.isCounterTrend) 1 else 0 }
                .thenBy {
                    when (it.grade) {
                        SetupGrade.A -> 0
                        SetupGrade.B -> 1
                        SetupGrade.C_WATCH -> 2
                    }
                }
                .thenByDescending { (it.evR ?: -99.0) * (if (it.isCounterTrend) 0.5 else 1.0) }
                .thenBy {
                    when (it.horizon) {
                        TradingHorizon.INTRADAY -> 0
                        TradingHorizon.SWING -> 1
                        TradingHorizon.POSITION -> 2
                        TradingHorizon.SCALP -> 3
                        TradingHorizon.OUTLOOK -> 4
                    }
                }
        ).firstOrNull() ?: finalPlans[TradingHorizon.INTRADAY] ?: finalPlans.values.firstOrNull()

        val bestHorizon = bestPlan?.horizon ?: TradingHorizon.INTRADAY
        val marketLabel = if (input.marketType == TradingMarketType.FUTURES) "Futures" else "Spot"
        val sideHeadline = when {
            input.marketType == TradingMarketType.SPOT && weightedSide == PositionSide.SHORT ->
                "SELL / REDUCE (SHORT on Futures)"
            weightedSide != null -> "${weightedSide.name} bias, ${alignmentPercent}% aligned"
            else -> "NEUTRAL / MIXED, ${alignmentPercent}% aligned"
        }
        val conflictShort = conflicts.firstOrNull()?.let { " • $it" } ?: ""
        val headline = "$cleanSym $marketLabel: $sideHeadline$conflictShort"

        val biasLadder = AgentMatrixEvaluator.MATRIX_TIMEFRAMES.map { tf ->
            val consensus = matrix.consensusByTf[tf]
            val ind = indicatorsByTf[tf]
            BiasEntry(
                timeframe = tf,
                displayLabel = AgentMatrixEvaluator.displayLabel(tf),
                bias = consensus?.biasLabel ?: "NEUTRAL",
                strength = consensus?.netScore?.let { abs(it) } ?: 0.0,
                detail = ind?.let { "EMA50 ${PriceFormatter.formatCurrency(it.ema50)} • RSI ${String.format(Locale.US, "%.1f", it.rsi14)}" } ?: "Insufficient bars"
            )
        }

        val outlook = buildOutlookSummary(safeLivePrice, closedByTf, indicatorsByTf)

        val plainBestNumbers = if (bestPlan != null && bestPlan.state != SignalVerdictState.NO_TRADE) {
            if (input.marketType == TradingMarketType.SPOT) {
                val sv = bestPlan.spotView
                when (sv.mode) {
                    SpotActionMode.BUY -> "${bestPlan.horizon.label} Spot BUY zone ${sv.buyZoneMin?.let { PriceFormatter.formatCurrency(it) } ?: "-"}-${sv.buyZoneMax?.let { PriceFormatter.formatCurrency(it) } ?: "-"}, limit ${sv.buyLimitPrice?.let { PriceFormatter.formatCurrency(it) } ?: "-"}, invalidation ${sv.invalidationPrice?.let { PriceFormatter.formatCurrency(it) } ?: "-"}, sell targets ${sv.sellTargets.joinToString("/") { PriceFormatter.formatCurrency(it) }}"
                    SpotActionMode.SELL_REDUCE_AND_WAIT_BUY -> "${sv.headline}. Wait for BUY at ${sv.buyLimitPrice?.let { PriceFormatter.formatCurrency(it) } ?: "-"} (Futures short also available)"
                    SpotActionMode.NO_SPOT_PLAN -> sv.reasonIfNone ?: "No active Spot plan"
                }
            } else {
                val dirLabel = bestPlan.side?.name ?: "WAIT"
                val ctTag = if (bestPlan.isCounterTrend) " [COUNTER-TREND 0.5x]" else ""
                "${bestPlan.horizon.label} $dirLabel$ctTag (${bestPlan.grade.label}): Zone ${bestPlan.formatZoneOrDash()}, Stop ${bestPlan.formatStopOrDash()}, TP1 ${bestPlan.tp1?.let { PriceFormatter.formatCurrency(it) } ?: "-"} (${bestPlan.tp1Rr?.let { String.format(Locale.US, "1:%.1f", it) } ?: "-"}), ${bestPlan.formatPWinWithWilson()}"
            }
        } else {
            bestPlan?.vetoes?.firstOrNull() ?: "NO TRADE across actionable horizons (hard safety veto active)."
        }

        val plainInvalidation = if (bestPlan?.stopLoss != null && bestPlan.stopLoss > 0.0 && bestPlan.side != null) {
            val stopStr = PriceFormatter.formatCurrency(bestPlan.stopLoss)
            val distPct = if (safeLivePrice > 0.0) abs(safeLivePrice - bestPlan.stopLoss) / safeLivePrice * 100.0 else 0.0
            val dirTxt = if (bestPlan.side == PositionSide.LONG) "below" else "above"
            "Close $dirTxt $stopStr (${String.format(Locale.US, "%.2f%%", distPct)}) invalidates the ${bestPlan.horizon.label} ${bestPlan.side.name} plan."
        } else {
            "No live stop active — waiting for setup confirmation."
        }

        val whatFlips = matrix.whatWouldChangeThis.firstOrNull()
            ?: "Decisive break of higher-timeframe key S/R flips weighted ladder bias."

        val anyStale = finalPlans.values.any { it.isStale }
        val stdNote = if (input.standardBackgroundMode) {
            "1m/5m triggers update every 15 min in Standard background mode (switch to Live mode for real-time ticks)."
        } else null

        return MasterVerdict(
            symbol = cleanSym,
            assetType = input.assetType,
            marketType = input.marketType,
            livePrice = safeLivePrice,
            headline = headline,
            weightedBiasSide = weightedSide,
            alignmentPercent = alignmentPercent,
            biasLadder = biasLadder,
            horizonPlans = finalPlans,
            bestHorizon = bestHorizon,
            bestPlan = bestPlan,
            outlook = outlook,
            matrix = matrix,
            conflicts = conflicts.distinct(),
            plainSummaryHeadline = headline,
            plainBestPlanNumbers = plainBestNumbers,
            plainInvalidation = plainInvalidation,
            whatFlipsBias = whatFlips,
            standardModeNote = stdNote,
            isStale = anyStale,
            seed = input.seed,
            computedAtMs = input.nowMs,
            aiShiftPoints = clampedAiShiftPts,
            aiShiftModelName = input.aiShiftModelName
        )
    }

    private fun computeMacroDirectionScore(
        livePrice: Double,
        indicatorsByTf: Map<Timeframe, TechnicalIndicators>,
        closedByTf: Map<Timeframe, List<CandlePoint>>
    ): Double {
        if (livePrice <= 0.0) return 0.0
        val macroTfs = listOf(Timeframe.ONE_HOUR to 0.20, Timeframe.FOUR_HOURS to 0.30, Timeframe.ONE_DAY to 0.30, Timeframe.FIVE_DAYS to 0.20)
        var score = 0.0
        var wSum = 0.0
        for ((tf, w) in macroTfs) {
            val ind = indicatorsByTf[tf] ?: continue
            val bars = closedByTf[tf].orEmpty()
            val emaRef = ind.ema50.takeIf { it > 0.0 } ?: ind.sma20
            val dir = when {
                emaRef > 0.0 && livePrice > emaRef && ind.ema12 >= ind.ema26 -> 1.0
                emaRef > 0.0 && livePrice < emaRef && ind.ema12 <= ind.ema26 -> -1.0
                emaRef > 0.0 && livePrice > emaRef -> 0.5
                emaRef > 0.0 && livePrice < emaRef -> -0.5
                bars.size >= 4 && bars.last().close > bars[bars.size - 4].close -> 0.4
                bars.size >= 4 && bars.last().close < bars[bars.size - 4].close -> -0.4
                else -> 0.0
            }
            score += dir * w
            wSum += w
        }
        return if (wSum > 0.0) score / wSum else 0.0
    }

    private fun evaluateActionableHorizon(
        horizon: TradingHorizon,
        cleanSymbol: String,
        safeLivePrice: Double,
        input: MasterVerdictInput,
        closedByTf: Map<Timeframe, List<CandlePoint>>,
        indicatorsByTf: Map<Timeframe, TechnicalIndicators>,
        macroBiasSide: PositionSide?
    ): Pair<HorizonPlan, ReplayResult?> {
        val triplet = input.horizonSettings.tripletFor(horizon)
        val rawTriggerBars = closedByTf[triplet.triggerTf].orEmpty()
        val rawSetupBars = closedByTf[triplet.setupTf].orEmpty()
        val biasBars = closedByTf[triplet.biasTf].orEmpty()

        // Ensure >= 100 bars for SignalEngineV2 using setupTf fallback only if triggerTf has fewer (e.g. 1W/4h edge)
        val engineBars = if (rawTriggerBars.size >= 100) rawTriggerBars else rawSetupBars.ifEmpty { rawTriggerBars }
        val setupInd = indicatorsByTf[triplet.setupTf] ?: indicatorsByTf[triplet.triggerTf]
        val triggerInd = indicatorsByTf[triplet.triggerTf] ?: setupInd
        val setupAtr = setupInd?.averageTrueRange?.takeIf { it > 0.0 } ?: (safeLivePrice * 0.015).coerceAtLeast(1e-6)

        val effTriggerStep = if (triplet.triggerTf == Timeframe.FIVE_DAYS) 7L * 86_400_000L else triplet.triggerTf.stepMillis
        val dataAgeMs = input.dataAgeByTf[triplet.triggerTf] ?: input.dataAgeByTf[triplet.setupTf] ?: 0L
        val session = SignalVetoDetector.determineSessionStatus(cleanSymbol, input.assetType, input.nowMs)
        val staleLimit = (effTriggerStep * 2L) + 15_000L
        val isStale = engineBars.isEmpty() || (dataAgeMs > staleLimit && (input.assetType == AssetType.CRYPTO || session == MarketSessionStatus.OPEN))

        // Determine if trigger TF shows an unmistakable counter-trend micro-direction (e.g. bearish 5m on bullish 1h/4h)
        val triggerBearish = triggerInd != null && safeLivePrice > 0.0 &&
            safeLivePrice < (triggerInd.ema50.takeIf { it > 0.0 } ?: safeLivePrice) &&
            triggerInd.ema12 < triggerInd.ema26 &&
            triggerInd.rsi14 < 48.0
        val triggerBullish = triggerInd != null && safeLivePrice > 0.0 &&
            safeLivePrice > (triggerInd.ema50.takeIf { it > 0.0 } ?: safeLivePrice) &&
            triggerInd.ema12 > triggerInd.ema26 &&
            triggerInd.rsi14 > 52.0

        val preferredSide: PositionSide? = when {
            horizon == TradingHorizon.SCALP && triggerBearish && macroBiasSide == PositionSide.LONG && (input.futuresPerpAvailable || input.marketType == TradingMarketType.FUTURES) ->
                PositionSide.SHORT
            horizon == TradingHorizon.SCALP && triggerBullish && macroBiasSide == PositionSide.SHORT ->
                PositionSide.LONG
            macroBiasSide != null && ((macroBiasSide == PositionSide.LONG && !triggerBearish) || (macroBiasSide == PositionSide.SHORT && !triggerBullish)) ->
                macroBiasSide
            else -> null
        }

        // Daily risk limit hard veto
        val riskBlocked = input.dailyTradesTaken >= input.riskSettings.maxTradesPerDay ||
            input.realizedRToday <= -input.riskSettings.dailyLossLimitR

        val signalOut = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = cleanSymbol,
                currentPrice = safeLivePrice,
                timeframe = triplet.triggerTf,
                marketType = if (preferredSide == PositionSide.SHORT && input.futuresPerpAvailable) TradingMarketType.FUTURES else input.marketType,
                assetType = input.assetType,
                leverage = input.riskSettings.maxLeverage,
                candles = engineBars,
                higherTimeframeCandles = biasBars.ifEmpty { rawSetupBars },
                futuresData = input.futuresData,
                news = input.news,
                offHoursMode = input.offHoursMode,
                spreadPercent = input.spreadPercent,
                perpMarkPrice = input.futuresData?.markPrice,
                spotPrice = safeLivePrice,
                highImpactEventWithin30Min = false, // News shock handled as CAUTION soft flag
                newsShockCaution = input.eventRisk?.hasNewsShock == true,
                futuresPerpAvailable = input.futuresPerpAvailable,
                currentTimeMillis = input.nowMs,
                customStockEntryWindows = input.customStockEntryWindows,
                preferredSide = preferredSide,
                aiShiftPoints = input.aiShiftPoints,
                aiShiftModelName = input.aiShiftModelName
            )
        )

        val setup = signalOut.primarySetup
        val smc = setupInd?.smc
        val bslAbove = smc?.liquidityPools
            ?.filter { it.type == LiquidityType.BUY_SIDE_LIQUIDITY_BSL && it.priceLevel > safeLivePrice }
            ?.minByOrNull { it.priceLevel }?.priceLevel
        val sslBelow = smc?.liquidityPools
            ?.filter { it.type == LiquidityType.SELL_SIDE_LIQUIDITY_SSL && it.priceLevel < safeLivePrice }
            ?.maxByOrNull { it.priceLevel }?.priceLevel

        val allHardVetoes = buildList {
            addAll(signalOut.vetoReasons)
            if (isStale && signalOut.vetoReasons.none { it.contains("Stale", true) }) {
                add("Data Integrity Veto: ${triplet.triggerTf.label} closed bar stale (${dataAgeMs / 60_000L}m old).")
            }
            if (riskBlocked) {
                add("Risk Budget Block: Daily limit reached (${input.dailyTradesTaken}/${input.riskSettings.maxTradesPerDay} trades, ${String.format(Locale.US, "%.1f", input.realizedRToday)}R).")
            }
        }

        if (setup == null || allHardVetoes.isNotEmpty() || safeLivePrice <= 0.0) {
            val emptySpot = buildSpotView(
                horizon = horizon,
                side = null,
                safeLivePrice = safeLivePrice,
                setupInd = setupInd,
                clippedMin = null,
                clippedMax = null,
                limitPrice = null,
                stopLoss = null,
                tp1 = null,
                tp2 = null,
                tp3 = null,
                triggerCondition = allHardVetoes.firstOrNull() ?: "Blocked",
                futuresPerpAvailable = input.futuresPerpAvailable,
                isNoTrade = true,
                noTradeReason = allHardVetoes.firstOrNull() ?: signalOut.oneLineWhy
            )
            return HorizonPlan(
                horizon = horizon,
                biasTf = triplet.biasTf,
                setupTf = triplet.setupTf,
                triggerTf = triplet.triggerTf,
                side = null,
                state = SignalVerdictState.NO_TRADE,
                grade = SetupGrade.C_WATCH,
                entryModel = "None",
                regime = signalOut.marketRegime,
                sessionStatus = session,
                triggerPrice = null,
                triggerCondition = allHardVetoes.firstOrNull() ?: signalOut.oneLineWhy,
                entryZoneMin = null,
                entryZoneMax = null,
                limitPrice = null,
                stopLoss = null,
                tp1 = null,
                tp2 = null,
                tp3 = null,
                tp1Rr = null,
                tp2Rr = null,
                tp3Rr = null,
                tp1TouchProb = null,
                tp2TouchProb = null,
                tp3TouchProb = null,
                pWin = 0.0,
                winsK = 0,
                sampleN = 0,
                wilsonLowPct = 0.0,
                wilsonHighPct = 0.0,
                evR = null,
                replaySeed = input.seed,
                replayResolvedN = 0,
                replayHeadline = "Replay skipped (NO TRADE)",
                replayConservativeMode = "-",
                replayTargetFirstPct = 0.0,
                replayStopFirstPct = 0.0,
                replayFanBands = emptyList(),
                validUntilMs = input.nowMs + horizon.validityDurationMs,
                needs = allHardVetoes.firstOrNull() ?: "Valid closed bars & risk budget",
                conflicts = signalOut.conflicts,
                vetoes = allHardVetoes,
                softFlags = signalOut.softFlags,
                agentVotes = emptyMap(),
                dataAgeMs = dataAgeMs,
                computedAtMs = input.nowMs,
                isCounterTrend = false,
                sizeMultiplier = 0.0,
                isStale = isStale,
                farZones = emptyList(),
                bslLevel = bslAbove,
                sslLevel = sslBelow,
                futuresView = null,
                spotView = emptySpot
            ) to null
        }

        val isLong = setup.direction == PositionSide.LONG
        // Strictly enforce <= 0.75 * ATR(setup TF) & stop strictly outside zone's far edge
        val (zoneMin, zoneMax) = QuantitativeEngine.clipZoneToDisplacementEdge(
            rawMin = setup.entryZoneMin,
            rawMax = setup.entryZoneMax,
            isLongDemand = isLong,
            atr = setupAtr
        )
        val limitPrice = setup.entryPrice.coerceIn(minOf(zoneMin, zoneMax), maxOf(zoneMin, zoneMax))
        val minStopGap = maxOf(setupAtr * 0.20, safeLivePrice * 0.0015)
        val rawStopLoss = if (isLong) {
            minOf(setup.stopLossPrice, zoneMin - minStopGap).coerceAtLeast(safeLivePrice * 0.05)
        } else {
            maxOf(setup.stopLossPrice, zoneMax + minStopGap)
        }
        // SANITY CAP: a stop can never be absurdly far from the entry (bad or old data produced 35% stops
        // on 15-minute scalps). If it is too far, the stop is pulled in and the targets are rebuilt from it.
        val maxStopFraction = when (horizon) {
            TradingHorizon.SCALP -> 0.015
            TradingHorizon.INTRADAY -> 0.03
            TradingHorizon.SWING -> 0.08
            else -> 0.15
        }
        val maxStopDistance = safeLivePrice * maxStopFraction
        val stopWasTooFar = abs(limitPrice - rawStopLoss) > maxStopDistance
        val stopLoss = when {
            !stopWasTooFar -> rawStopLoss
            isLong -> limitPrice - maxStopDistance
            else -> limitPrice + maxStopDistance
        }
        val stopDist = abs(limitPrice - stopLoss).coerceAtLeast(safeLivePrice * 0.0005)
        val tp1 = if (stopWasTooFar) (if (isLong) limitPrice + stopDist * 1.5 else limitPrice - stopDist * 1.5) else setup.takeProfit1
        val tp2 = if (stopWasTooFar) (if (isLong) limitPrice + stopDist * 2.5 else limitPrice - stopDist * 2.5) else setup.takeProfit2
        val tp3 = if (stopWasTooFar) (if (isLong) limitPrice + stopDist * 4.0 else limitPrice - stopDist * 4.0) else setup.takeProfit3
        val tp1Rr = abs(tp1 - limitPrice) / stopDist
        val tp2Rr = abs(tp2 - limitPrice) / stopDist
        val tp3Rr = abs(tp3 - limitPrice) / stopDist

        // Seeded Replay simulation when 5m pool is available
        val replayResult = runHorizonReplayIfPossible(
            cleanSymbol = cleanSymbol,
            assetType = input.assetType,
            horizon = horizon,
            side = setup.direction,
            entryPrice = limitPrice,
            stopLoss = stopLoss,
            tp1 = tp1,
            pool = input.replayPool,
            seed = input.seed,
            numReplays = input.replayIterations,
            isFutures = input.marketType == TradingMarketType.FUTURES || !isLong,
            nowMs = input.nowMs,
            replayProvider = input.replayProvider
        )

        val evInfo = signalOut.evidence ?: SignalCalibrationEngine.getEvidenceForModel(
            symbol = cleanSymbol,
            model = setup.model,
            regime = signalOut.marketRegime,
            isWithTrend = !setup.isAgainstHtfTrend,
            rMultiple = tp1Rr,
            sessionStatus = session
        )
        val sampleN = evInfo.sampleSize
        val winsK = evInfo.winsK
        val (ciLow, ciHigh) = if (sampleN > 0) evInfo.confidenceInterval95 else Pair(0.0, 0.0)
        val calibratedP = replayResult?.effectivePWin?.takeIf { replayResult.refusalReason == null && replayResult.resolvedCount > 0 }
            ?.let { minOf(it, evInfo.hitRatePercent / 100.0) }
            ?: (evInfo.hitRatePercent / 100.0)
        val pWin = calibratedP.coerceIn(0.05, 0.95)
        val evR = (pWin * tp1Rr) - (1.0 - pWin) - 0.03

        val isCounterTrend = setup.isAgainstHtfTrend || (macroBiasSide != null && setup.direction != macroBiasSide)
        val baseFlags = setup.softFlags.toMutableList()
        if (isCounterTrend && baseFlags.none { it.type == SoftFlagType.HTF_OPPOSED }) {
            baseFlags.add(
                SoftFlag(
                    type = SoftFlagType.HTF_OPPOSED,
                    label = "COUNTER-TREND (0.5x)",
                    detail = "${horizon.label} ${setup.direction.name} opposes HTF bias.",
                    actionHint = "Trade 0.5x size or wait for trend-aligned horizon"
                )
            )
        }
        val sizeMult = when {
            isCounterTrend -> 0.5
            setup.setupGrade == SetupGrade.B -> 0.5
            setup.setupGrade == SetupGrade.C_WATCH -> 0.0
            else -> 1.0
        }

        // Futures view
        val maxSafeLev = floor(limitPrice / (3.0 * stopDist)).toInt()
            .coerceIn(1, input.riskSettings.maxLeverage.coerceAtLeast(1))
        val liqPrice = if (isLong) {
            limitPrice * (1.0 - (1.0 / maxSafeLev) + 0.005)
        } else {
            limitPrice * (1.0 + (1.0 / maxSafeLev) - 0.005)
        }
        val riskDollars = if (input.riskSettings.isConfigured && input.riskSettings.accountBalance > 0.0) {
            (input.riskSettings.maxRiskPerTradeDollars.takeIf { it > 0.0 }
                ?: input.riskSettings.accountBalance * (input.riskSettings.riskPercent / 100.0)) *
                sizeMult.coerceAtLeast(if (signalOut.verdictState == SignalVerdictState.TRADE) 0.5 else 0.5)
        } else 0.0
        val contracts = if (riskDollars > 0.0 && stopDist > 0.0) riskDollars / stopDist else 0.0
        val posUsd = contracts * limitPrice
        val levLabel = if (input.isEngineOnly) "Engine rec" else "Engine/AI rec"
        val futuresView = ProjectionFuturesView(
            side = setup.direction,
            entryZoneMin = zoneMin,
            entryZoneMax = zoneMax,
            limitPrice = limitPrice,
            stopLoss = stopLoss,
            tp1 = tp1,
            tp2 = tp2,
            tp3 = tp3,
            tp1R = tp1Rr,
            tp2R = tp2Rr,
            tp3R = tp3Rr,
            tp1UnleveragedPct = abs(tp1 - limitPrice) / limitPrice * 100.0,
            tp2UnleveragedPct = abs(tp2 - limitPrice) / limitPrice * 100.0,
            tp3UnleveragedPct = abs(tp3 - limitPrice) / limitPrice * 100.0,
            recommendedLeverage = maxSafeLev,
            liquidationPrice = liqPrice,
            positionSizeContracts = contracts,
            positionSizeUsd = posUsd,
            leverageSourceLabel = levLabel
        )

        val spotView = buildSpotView(
            horizon = horizon,
            side = setup.direction,
            safeLivePrice = safeLivePrice,
            setupInd = setupInd,
            clippedMin = zoneMin,
            clippedMax = zoneMax,
            limitPrice = limitPrice,
            stopLoss = stopLoss,
            tp1 = tp1,
            tp2 = tp2,
            tp3 = tp3,
            triggerCondition = setup.triggerCondition,
            futuresPerpAvailable = input.futuresPerpAvailable,
            isNoTrade = false,
            noTradeReason = null
        )

        val flatByMs = if (horizon == TradingHorizon.INTRADAY && input.assetType == AssetType.STOCK) {
            computeStockFlatByMs(input.nowMs)
        } else {
            input.nowMs + horizon.validityDurationMs
        }

        val plan = HorizonPlan(
            horizon = horizon,
            biasTf = triplet.biasTf,
            setupTf = triplet.setupTf,
            triggerTf = triplet.triggerTf,
            side = setup.direction,
            state = signalOut.verdictState,
            grade = setup.setupGrade,
            entryModel = setup.model.title,
            regime = signalOut.marketRegime,
            sessionStatus = session,
            triggerPrice = limitPrice,
            triggerCondition = setup.triggerCondition,
            entryZoneMin = zoneMin,
            entryZoneMax = zoneMax,
            limitPrice = limitPrice,
            stopLoss = stopLoss,
            tp1 = tp1,
            tp2 = tp2,
            tp3 = tp3,
            tp1Rr = tp1Rr,
            tp2Rr = tp2Rr,
            tp3Rr = tp3Rr,
            tp1TouchProb = setup.probabilityOfTouchTp1,
            tp2TouchProb = setup.probabilityOfTouchTp2,
            tp3TouchProb = setup.probabilityOfTouchTp3,
            pWin = pWin,
            winsK = winsK,
            sampleN = sampleN,
            wilsonLowPct = ciLow,
            wilsonHighPct = ciHigh,
            evR = evR,
            replaySeed = input.seed,
            replayResolvedN = replayResult?.resolvedCount ?: 0,
            replayHeadline = replayResult?.verdict?.headline ?: "Replay pool warming (<20 full days)",
            replayConservativeMode = replayResult?.conservativeModeLabel ?: "-",
            replayTargetFirstPct = replayResult?.targetFirstPct ?: 0.0,
            replayStopFirstPct = replayResult?.stopFirstPct ?: 0.0,
            replayFanBands = replayResult?.fanChartBands ?: emptyList(),
            validUntilMs = flatByMs,
            needs = setup.needsCondition.ifBlank { signalOut.needsCondition },
            conflicts = signalOut.conflicts,
            vetoes = emptyList(),
            softFlags = baseFlags.distinctBy { it.type },
            agentVotes = emptyMap(),
            dataAgeMs = dataAgeMs,
            computedAtMs = input.nowMs,
            isCounterTrend = isCounterTrend,
            sizeMultiplier = sizeMult,
            isStale = isStale,
            farZones = setup.farZones,
            bslLevel = bslAbove,
            sslLevel = sslBelow,
            futuresView = futuresView,
            spotView = spotView
        )
        return plan to replayResult
    }

    private fun buildSpotView(
        horizon: TradingHorizon,
        side: PositionSide?,
        safeLivePrice: Double,
        setupInd: TechnicalIndicators?,
        clippedMin: Double?,
        clippedMax: Double?,
        limitPrice: Double?,
        stopLoss: Double?,
        tp1: Double?,
        tp2: Double?,
        tp3: Double?,
        triggerCondition: String,
        futuresPerpAvailable: Boolean,
        isNoTrade: Boolean,
        noTradeReason: String?
    ): ProjectionSpotView {
        if (isNoTrade || safeLivePrice <= 0.0 || side == null) {
            return ProjectionSpotView(
                mode = SpotActionMode.NO_SPOT_PLAN,
                headline = "No active Spot plan",
                futuresShortAvailable = futuresPerpAvailable,
                reasonIfNone = noTradeReason ?: "Hard veto active"
            )
        }
        val atr = setupInd?.averageTrueRange?.takeIf { it > 0.0 } ?: (safeLivePrice * 0.015)
        if (side == PositionSide.LONG && clippedMin != null && clippedMax != null && limitPrice != null) {
            return ProjectionSpotView(
                mode = SpotActionMode.BUY,
                headline = "Spot ${horizon.label} BUY plan (do not chase above ${PriceFormatter.formatCurrency(clippedMax)})",
                buyZoneMin = clippedMin,
                buyZoneMax = clippedMax,
                buyLimitPrice = limitPrice,
                invalidationPrice = stopLoss,
                doNotBuyAbove = clippedMax,
                sellTargets = listOfNotNull(tp1, tp2, tp3),
                futuresShortAvailable = futuresPerpAvailable
            )
        }
        // Bearish view in Spot: NO shorting! Emit SELL / REDUCE zone + WAIT for next BUY demand zone below price
        val reduceMin = clippedMin ?: (safeLivePrice - atr * 0.2)
        val reduceMax = clippedMax ?: (safeLivePrice + atr * 0.2)
        val demandOb = setupInd?.smc?.activeOrderBlocks
            ?.filter { it.type == OrderBlockType.BULLISH_DEMAND_BLOCK && !it.isMitigated && it.top < safeLivePrice }
            ?.maxByOrNull { it.top }
        val ssl = setupInd?.smc?.liquidityPools
            ?.filter { it.type == LiquidityType.SELL_SIDE_LIQUIDITY_SSL && it.priceLevel < safeLivePrice }
            ?.maxByOrNull { it.priceLevel }?.priceLevel
        val rawBuyMin: Double
        val rawBuyMax: Double
        val whyBuy: String
        if (demandOb != null) {
            rawBuyMin = demandOb.bottom
            rawBuyMax = demandOb.top
            whyBuy = "Unmitigated demand block below current supply rejection"
        } else if (ssl != null && ssl > 0.0) {
            rawBuyMin = ssl - atr * 0.25
            rawBuyMax = ssl + atr * 0.25
            whyBuy = "Major SSL liquidity sweep shelf below current price"
        } else {
            val anchor = (safeLivePrice - atr * 2.2).coerceAtLeast(safeLivePrice * 0.75)
            rawBuyMin = anchor - atr * 0.25
            rawBuyMax = anchor + atr * 0.25
            whyBuy = "2.2x ATR demand value shelf below current bearish structure"
        }
        val (buyMin, buyMax) = QuantitativeEngine.clipZoneToDisplacementEdge(rawBuyMin, rawBuyMax, isLongDemand = true, atr = atr)
        val buyLimit = (buyMin + buyMax) / 2.0
        val inv = buyMin - maxOf(atr * 0.6, safeLivePrice * 0.005)
        return ProjectionSpotView(
            mode = SpotActionMode.SELL_REDUCE_AND_WAIT_BUY,
            headline = "Bearish ${horizon.label} — Spot SELL / REDUCE & Wait for Demand BUY",
            buyZoneMin = buyMin,
            buyZoneMax = buyMax,
            buyLimitPrice = buyLimit,
            invalidationPrice = inv,
            doNotBuyAbove = buyMax,
            sellTargets = listOfNotNull(tp1, tp2, tp3),
            reduceZoneMin = reduceMin,
            reduceZoneMax = reduceMax,
            waitBuyWhy = whyBuy,
            waitBuyTrigger = "Reclaim above ${PriceFormatter.formatCurrency(buyMax)} on closed setup bar after liquidity sweep",
            futuresShortAvailable = futuresPerpAvailable
        )
    }

    private fun runHorizonReplayIfPossible(
        cleanSymbol: String,
        assetType: AssetType,
        horizon: TradingHorizon,
        side: PositionSide,
        entryPrice: Double,
        stopLoss: Double,
        tp1: Double,
        pool: SessionPool?,
        seed: Long,
        numReplays: Int,
        isFutures: Boolean,
        nowMs: Long,
        replayProvider: ((planHash: String, seed: Long, iterations: Int, runner: () -> ReplayResult) -> ReplayResult)? = null
    ): ReplayResult? {
        if (pool == null || !pool.isSufficient || entryPrice <= 0.0 || stopLoss <= 0.0 || tp1 <= 0.0) return null
        val pType = if (assetType == AssetType.STOCK) MarketProfileType.US_EQUITY else MarketProfileType.CRYPTO
        val tz = if (pType == MarketProfileType.CRYPTO) MarketCalendar.UTC_TZ else MarketCalendar.NY_TZ
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        val timeIdx = if (pType == MarketProfileType.CRYPTO) {
            ((cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) / 5).coerceIn(0, 287)
        } else {
            (((cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) - (9 * 60 + 30)) / 5).coerceIn(0, 77)
        }
        val barsHorizon = when (horizon) {
            TradingHorizon.SCALP -> 36
            TradingHorizon.INTRADAY -> if (pType == MarketProfileType.US_EQUITY) (77 - timeIdx).coerceIn(1, 77) else 72
            TradingHorizon.SWING -> if (pType == MarketProfileType.US_EQUITY) 77 else 144
            TradingHorizon.POSITION -> if (pType == MarketProfileType.US_EQUITY) 77 else 216
            TradingHorizon.OUTLOOK -> 72
        }
        val rPlan = ReplayPlan(
            symbol = cleanSymbol,
            side = side,
            entryPrice = entryPrice,
            stopLossPrice = stopLoss,
            takeProfitPrice = tp1,
            entryBarTimestamp = nowMs - 300_000L,
            timeOfDayIndex = timeIdx,
            horizonBars = barsHorizon,
            profileType = pType,
            fundingBpsPer8h = if (isFutures) 1.0 else 0.0,
            isEntryBarClosed = true
        )
        val runner = {
            ReplayEngine.runReplay(
                plan = rPlan,
                pool = pool,
                seed = seed,
                numReplays = numReplays,
                useBlockBootstrap = true,
                useVolatilityScaling = true
            )
        }
        val planHash = "${cleanSymbol}_${horizon.name}_${side.name}_${String.format(Locale.US, "%.4f_%.4f_%.4f_%d", entryPrice, stopLoss, tp1, numReplays)}"
        return replayProvider?.invoke(planHash, seed, numReplays, runner) ?: runner()
    }

    private fun buildOutlookSummary(
        livePrice: Double,
        closedByTf: Map<Timeframe, List<CandlePoint>>,
        indicatorsByTf: Map<Timeframe, TechnicalIndicators>
    ): OutlookSummary {
        val m1Bars = closedByTf[Timeframe.ONE_MONTH].orEmpty()
        val d1Bars = closedByTf[Timeframe.ONE_DAY].orEmpty()
        val refBars = m1Bars.ifEmpty { d1Bars }
        val ind1M = indicatorsByTf[Timeframe.ONE_MONTH] ?: indicatorsByTf[Timeframe.FIVE_DAYS] ?: indicatorsByTf[Timeframe.ONE_DAY]

        val emaRef = ind1M?.ema50?.takeIf { it > 0.0 } ?: ind1M?.sma20?.takeIf { it > 0.0 } ?: livePrice
        val bias = when {
            livePrice > emaRef * 1.01 -> "BULL"
            livePrice < emaRef * 0.99 -> "BEAR"
            else -> "NEUTRAL"
        }
        val strength = if (emaRef > 0.0) (abs(livePrice - emaRef) / emaRef * 6.0).coerceIn(0.15, 0.95) else 0.3

        val highAll = refBars.takeLast(120).maxOfOrNull { it.high } ?: (livePrice * 1.15)
        val lowAll = refBars.takeLast(120).minOfOrNull { it.low } ?: (livePrice * 0.85)
        val supports = listOfNotNull(
            ind1M?.supportLevel?.takeIf { it > 0.0 && it < livePrice },
            lowAll.takeIf { it > 0.0 && it < livePrice }
        ).distinct().sortedDescending().take(3)
        val resistances = listOfNotNull(
            ind1M?.resistanceLevel?.takeIf { it > livePrice },
            highAll.takeIf { it > livePrice }
        ).distinct().sorted().take(3)

        val bandW = ((highAll - lowAll) * 0.08).coerceAtLeast(livePrice * 0.02)
        val accZones = if (lowAll > 0.0) {
            listOf(
                OutlookZoneLevel(
                    minPrice = lowAll,
                    maxPrice = minOf(lowAll + bandW, livePrice * 0.99),
                    label = "Macro Accumulation ${PriceFormatter.formatZoneRange(lowAll, lowAll + bandW)}",
                    kind = "ACCUMULATION"
                )
            )
        } else emptyList()
        val distZones = if (highAll > livePrice) {
            listOf(
                OutlookZoneLevel(
                    minPrice = maxOf(highAll - bandW, livePrice * 1.01),
                    maxPrice = highAll,
                    label = "Macro Distribution ${PriceFormatter.formatZoneRange(highAll - bandW, highAll)}",
                    kind = "DISTRIBUTION"
                )
            )
        } else emptyList()

        return OutlookSummary(
            timeframesCovered = listOf("1M", "3M", "1Y"),
            trendBias = bias,
            biasStrength = strength,
            keySupports = supports,
            keyResistances = resistances,
            accumulationZones = accZones,
            distributionZones = distZones,
            summaryText = "$bias macro bias vs 1M/1W EMA50 (${PriceFormatter.formatCurrency(emaRef)}). Support ${supports.firstOrNull()?.let { PriceFormatter.formatCurrency(it) } ?: "-"} • Resistance ${resistances.firstOrNull()?.let { PriceFormatter.formatCurrency(it) } ?: "-"}."
        )
    }

    private fun computeStockFlatByMs(nowMs: Long): Long {
        val candidate = MarketCalendar.getStockFlatByTimestamp(nowMs)
        return if (candidate > nowMs) candidate else nowMs + TradingHorizon.INTRADAY.validityDurationMs
    }
}
