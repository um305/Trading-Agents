package com.example.domain.master

import com.example.domain.analytics.QuantitativeEngine
import com.example.domain.daytrader.fundamentals.StockFundamentalsResult
import com.example.domain.daytrader.news.EventRiskAssessment
import com.example.domain.daytrader.replay.ReplayResult
import com.example.domain.engine.SignalCalibrationEngine
import com.example.domain.engine.SignalVetoDetector
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.CryptoFuturesData
import com.example.domain.model.EntryModel
import com.example.domain.model.LiquidityType
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSessionStatus
import com.example.domain.model.NewsItem
import com.example.domain.model.OffHoursMode
import com.example.domain.model.OrderBookSnapshot
import com.example.domain.model.TechnicalIndicators
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import com.example.domain.util.PriceFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.math.abs

data class MatrixEvaluationInput(
    val symbol: String,
    val assetType: AssetType,
    val marketType: TradingMarketType,
    val livePrice: Double,
    val candlesByTf: Map<Timeframe, List<CandlePoint>>,
    val dataAgeByTf: Map<Timeframe, Long> = emptyMap(),
    val indicatorsByTf: Map<Timeframe, TechnicalIndicators> = emptyMap(),
    val orderBook: OrderBookSnapshot? = null,
    val futuresData: CryptoFuturesData? = null,
    val news: List<NewsItem> = emptyList(),
    val eventRisk: EventRiskAssessment? = null,
    val fundamentals: StockFundamentalsResult? = null,
    val replayByTf: Map<Timeframe, ReplayResult> = emptyMap(),
    val offHoursMode: OffHoursMode = OffHoursMode.CAUTION,
    val spreadPercent: Double = 0.02,
    val accountConfigured: Boolean = false,
    val dailyTradesTaken: Int = 0,
    val maxTradesPerDay: Int = 3,
    val realizedRToday: Double = 0.0,
    val dailyLossLimitR: Double = 3.0,
    val nowMs: Long = System.currentTimeMillis(),
    val aiShiftPoints: Int = 0,
    val aiSummary: String? = null,
    val aiCallback: (suspend (tf: Timeframe) -> Pair<Double, String>)? = null
)

object AgentMatrixEvaluator {

    val MATRIX_TIMEFRAMES: List<Timeframe> = listOf(
        Timeframe.FIVE_MIN,
        Timeframe.FIFTEEN_MIN,
        Timeframe.ONE_HOUR,
        Timeframe.FOUR_HOURS,
        Timeframe.ONE_DAY,
        Timeframe.FIVE_DAYS, // 1W weekly slot
        Timeframe.ONE_MONTH
    )

    fun displayLabel(tf: Timeframe): String = when (tf) {
        Timeframe.FIVE_DAYS -> "1W"
        else -> tf.label
    }

    suspend fun evaluateMatrix(input: MatrixEvaluationInput): AgentMatrix = coroutineScope {
        val cells = mutableListOf<AgentMatrixCell>()
        val precomputedIndicators = input.indicatorsByTf.toMutableMap()

        for (tf in MATRIX_TIMEFRAMES) {
            val bars = input.candlesByTf[tf].orEmpty().filter { it.isClosed && it.close > 0.0 }
            if (!precomputedIndicators.containsKey(tf) && bars.isNotEmpty()) {
                precomputedIndicators[tf] = QuantitativeEngine.computeIndicators(bars)
            }
        }

        // Execute Stage 0..10 rule agents (3s timeout per agent) and Stage 11 AI agent (12s timeout)
        val stageGroups = AgentId.values().groupBy { it.stageIndex }.toSortedMap()
        for ((stage, agents) in stageGroups) {
            val timeoutMs = if (stage == 11) 12_000L else 3_000L
            val stageCells = agents.map { agent ->
                async {
                    MATRIX_TIMEFRAMES.map { tf ->
                        evaluateSingleCellSafe(
                            agentId = agent,
                            timeframe = tf,
                            input = input,
                            indicators = precomputedIndicators[tf],
                            allIndicators = precomputedIndicators,
                            timeoutMs = timeoutMs
                        )
                    }
                }
            }.awaitAll().flatten()
            cells.addAll(stageCells)
        }

        val consensusByTf = linkedMapOf<Timeframe, TimeframeConsensus>()
        for (tf in MATRIX_TIMEFRAMES) {
            val tfCells = cells.filter { it.timeframe == tf && it.status == AgentStatus.OK }
            val activeCells = tfCells.filter { abs(it.stance) > 0.05 }
            val net = if (tfCells.isNotEmpty()) {
                val weightedSum = tfCells.sumOf { it.stance * (0.4 + 0.6 * it.strength) }
                (weightedSum / tfCells.size.toDouble()).coerceIn(-1.0, 1.0)
            } else 0.0

            val biasLabel = when {
                net >= 0.12 -> "BULL"
                net <= -0.12 -> "BEAR"
                else -> "NEUTRAL"
            }
            val agreeing = when (biasLabel) {
                "BULL" -> activeCells.count { it.stance > 0.05 }
                "BEAR" -> activeCells.count { it.stance < -0.05 }
                else -> tfCells.count { abs(it.stance) <= 0.15 }
            }
            val totalForPct = activeCells.size.coerceAtLeast(1)
            val pct = if (biasLabel == "NEUTRAL") {
                ((tfCells.count { abs(it.stance) <= 0.15 }.toDouble() / tfCells.size.coerceAtLeast(1)) * 100.0).toInt().coerceIn(0, 100)
            } else {
                ((agreeing.toDouble() / totalForPct.toDouble()) * 100.0).toInt().coerceIn(0, 100)
            }
            consensusByTf[tf] = TimeframeConsensus(
                timeframe = tf,
                tfDisplayLabel = displayLabel(tf),
                biasLabel = biasLabel,
                agreementPercent = pct,
                netScore = net
            )
        }

        val failedCount = cells.count { it.status == AgentStatus.FAILED }
        val flips = mutableListOf<String>()
        val c1h = consensusByTf[Timeframe.ONE_HOUR]
        val c4h = consensusByTf[Timeframe.FOUR_HOURS]
        val c1d = consensusByTf[Timeframe.ONE_DAY]
        if (c1h != null && c4h != null && c1h.biasLabel != "NEUTRAL" && c4h.biasLabel != "NEUTRAL" && c1h.biasLabel != c4h.biasLabel) {
            flips.add("1h (${c1h.biasLabel}) opposes 4h (${c4h.biasLabel}): close on 1h reclaiming 4h EMA50 aligns intraday + swing.")
        }
        if (c1d != null && c4h != null && c1d.biasLabel != c4h.biasLabel && c1d.biasLabel != "NEUTRAL") {
            flips.add("4h (${c4h.biasLabel}) conflicts with 1D (${c1d.biasLabel}): shift on 4h structure flips multi-day alignment.")
        }
        val evCell = cells.firstOrNull { it.agentId == AgentId.SESSION_EVENT && it.vetoes.isEmpty() && it.reasons.any { r -> r.contains("CAUTION", true) } }
        if (evCell != null) {
            flips.add("News/event caution active: headline decay >2h or spreads normalizing restores 1.0x sizing.")
        }
        if (failedCount > 0) {
            val failedNames = cells.filter { it.status == AgentStatus.FAILED }.map { it.agentId.shortName }.distinct().take(3)
            flips.add("$failedCount matrix cells failed (${failedNames.joinToString(", ")}); retry feed refresh to restore coverage.")
        }
        if (flips.isEmpty()) {
            val ind1h = precomputedIndicators[Timeframe.ONE_HOUR]
            if (ind1h != null && ind1h.ema50 > 0.0) {
                flips.add("Decisive 1h close across EMA50 (${PriceFormatter.formatCurrency(ind1h.ema50)}) with RVOL >= 1.2 flips intraday bias.")
            } else {
                flips.add("Fresh BOS/CHoCH on 1h or 4h with volume expansion reverses current ladder consensus.")
            }
        }

        AgentMatrix(
            timeframes = MATRIX_TIMEFRAMES,
            cells = cells,
            consensusByTf = consensusByTf,
            failedCount = failedCount,
            whatWouldChangeThis = flips.distinct()
        )
    }

    private suspend fun evaluateSingleCellSafe(
        agentId: AgentId,
        timeframe: Timeframe,
        input: MatrixEvaluationInput,
        indicators: TechnicalIndicators?,
        allIndicators: Map<Timeframe, TechnicalIndicators>,
        timeoutMs: Long
    ): AgentMatrixCell {
        val now = input.nowMs
        val ageMs = input.dataAgeByTf[timeframe] ?: 0L
        return try {
            withTimeout(timeoutMs) {
                evaluateSingleCell(agentId, timeframe, input, indicators, allIndicators, ageMs, now)
            }
        } catch (t: Throwable) {
            AgentMatrixCell(
                agentId = agentId,
                timeframe = timeframe,
                stance = 0.0,
                strength = 0.0,
                freshnessMs = ageMs,
                reasons = emptyList(),
                vetoes = emptyList(),
                sourceTag = if (agentId == AgentId.AI_AGENT) AgentSourceTag.AI else AgentSourceTag.ENGINE,
                status = AgentStatus.FAILED,
                errorMessage = "${agentId.displayName} failed: ${t.message ?: t::class.java.simpleName}",
                computedAtMs = now
            )
        }
    }

    private suspend fun evaluateSingleCell(
        agentId: AgentId,
        timeframe: Timeframe,
        input: MatrixEvaluationInput,
        ind: TechnicalIndicators?,
        allInd: Map<Timeframe, TechnicalIndicators>,
        ageMs: Long,
        nowMs: Long
    ): AgentMatrixCell {
        val bars = input.candlesByTf[timeframe].orEmpty().filter { it.isClosed && it.close > 0.0 }
        val price = input.livePrice.takeIf { it > 0.0 } ?: bars.lastOrNull()?.close ?: 0.0
        val effStep = if (timeframe == Timeframe.FIVE_DAYS) 7L * 86_400_000L else timeframe.stepMillis
        val session = SignalVetoDetector.determineSessionStatus(input.symbol, input.assetType, nowMs)

        return when (agentId) {
            AgentId.DATA_INTEGRITY -> {
                val staleLimit = (effStep * 2L) + 15_000L
                val isStale = bars.isNotEmpty() && ageMs > staleLimit && session == MarketSessionStatus.OPEN
                val minReq = if (timeframe == Timeframe.ONE_MONTH || timeframe == Timeframe.FIVE_DAYS) 12 else 100
                val tooFew = bars.size < minReq
                val vetoes = buildList {
                    if (price <= 0.0) add("Invalid price")
                    if (isStale) add("Stale bar age ${ageMs / 60_000L}m")
                    if (tooFew && timeframe in listOf(Timeframe.FIVE_MIN, Timeframe.FIFTEEN_MIN, Timeframe.ONE_HOUR)) {
                        add("${bars.size}/$minReq closed bars")
                    }
                }
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = 0.0,
                    strength = if (vetoes.isEmpty()) 0.9 else 0.0,
                    freshnessMs = ageMs,
                    reasons = listOf("${bars.size} closed bars • age ${ageMs / 1000L}s"),
                    vetoes = vetoes,
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.SESSION_EVENT -> {
                val er = input.eventRisk
                val isEquity = SignalVetoDetector.isEquitySymbol(input.symbol, input.assetType)
                val vetoes = mutableListOf<String>()
                val reasons = mutableListOf("Session: ${session.label}")
                if (isEquity && session != MarketSessionStatus.OPEN && input.offHoursMode == OffHoursMode.BLOCK) {
                    vetoes.add("Off-hours blocked")
                }
                if (er?.hasNewsShock == true) {
                    reasons.add("CAUTION: ${er.shockSourceType ?: "news shock"} (0.5x size)")
                } else if (er != null && er.summaryText.isNotBlank()) {
                    reasons.add(er.summaryText.take(72))
                }
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = 0.0,
                    strength = if (er?.hasNewsShock == true) 0.5 else 0.85,
                    freshnessMs = ageMs,
                    reasons = reasons,
                    vetoes = vetoes,
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.REGIME -> {
                if (ind == null) return unavailableCell(agentId, timeframe, ageMs, nowMs, "No candle history")
                val regime = SignalVetoDetector.classifyMarketRegime(ind)
                val stance = when {
                    regime == MarketRegime.DEAD -> 0.0
                    ind.ema12 > ind.ema26 -> 0.55
                    ind.ema12 < ind.ema26 -> -0.55
                    else -> 0.0
                }
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = stance,
                    strength = if (regime == MarketRegime.DEAD) 0.25 else (ind.adx / 40.0).coerceIn(0.3, 1.0),
                    freshnessMs = ageMs,
                    reasons = listOf("${regime.label} • ADX ${fmt1(ind.adx)}, ER ${fmt2(ind.efficiencyRatio)}, BW ${fmt0(ind.bandwidthPercentile)}p"),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.MTF_BIAS -> {
                if (ind == null || price <= 0.0) return unavailableCell(agentId, timeframe, ageMs, nowMs, "Missing bars")
                val ema50 = ind.ema50.takeIf { it > 0.0 } ?: price
                val ema200 = ind.ema200.takeIf { it > 0.0 }
                val above50 = price > ema50
                val above200 = ema200?.let { price > it } ?: above50
                val macdBull = ind.macd.macd >= ind.macd.signal
                var score = 0.0
                score += if (above50) 0.45 else -0.45
                score += if (above200) 0.30 else -0.30
                score += if (macdBull) 0.25 else -0.25
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = score.coerceIn(-1.0, 1.0),
                    strength = abs(score).coerceIn(0.2, 1.0),
                    freshnessMs = ageMs,
                    reasons = listOf(
                        "Price ${if (above50) ">" else "<"} EMA50 (${PriceFormatter.formatCurrency(ema50)})",
                        "EMA200: ${ema200?.let { PriceFormatter.formatCurrency(it) } ?: "N/A"}"
                    ),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.STRUCTURE_SMC -> {
                val smc = ind?.smc ?: return unavailableCell(agentId, timeframe, ageMs, nowMs, "Insufficient SMC bars")
                val trendBull = smc.trend.contains("Bull", true) || smc.lastBOS != null
                val trendBear = smc.trend.contains("Bear", true) || smc.lastCHoCH != null
                val sweep = smc.recentLiquiditySweep
                var stance = when {
                    trendBull && !trendBear -> 0.65
                    trendBear && !trendBull -> -0.65
                    else -> 0.0
                }
                if (sweep?.type == LiquidityType.SELL_SIDE_LIQUIDITY_SSL) stance = (stance + 0.30).coerceIn(-1.0, 1.0)
                if (sweep?.type == LiquidityType.BUY_SIDE_LIQUIDITY_BSL) stance = (stance - 0.30).coerceIn(-1.0, 1.0)
                val bslAbove = smc.liquidityPools.firstOrNull { it.type == LiquidityType.BUY_SIDE_LIQUIDITY_BSL && it.priceLevel > price }?.priceLevel
                val sslBelow = smc.liquidityPools.firstOrNull { it.type == LiquidityType.SELL_SIDE_LIQUIDITY_SSL && it.priceLevel < price }?.priceLevel
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = stance,
                    strength = (0.45 + smc.activeOrderBlocks.size.coerceAtMost(4) * 0.1).coerceIn(0.3, 0.95),
                    freshnessMs = ageMs,
                    reasons = listOf(
                        smc.trend,
                        "BSL ${bslAbove?.let { PriceFormatter.formatCurrency(it) } ?: "-"} • SSL ${sslBelow?.let { PriceFormatter.formatCurrency(it) } ?: "-"}"
                    ),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.VALUE_VOLUME -> {
                if (ind == null || price <= 0.0) return unavailableCell(agentId, timeframe, ageMs, nowMs, "Missing bars")
                val vwap = ind.sessionVwap.takeIf { it > 0.0 } ?: ind.sma20.takeIf { it > 0.0 } ?: price
                val poc = ind.volumeProfile?.poc ?: vwap
                val distPct = if (vwap > 0.0) ((price - vwap) / vwap) * 100.0 else 0.0
                val stance = when {
                    price > vwap && ind.rvol >= 1.1 -> 0.60
                    price < vwap && ind.rvol >= 1.1 -> -0.60
                    price > poc -> 0.25
                    price < poc -> -0.25
                    else -> 0.0
                }
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = stance,
                    strength = (ind.rvol / 1.8).coerceIn(0.25, 0.95),
                    freshnessMs = ageMs,
                    reasons = listOf("VWAP ${PriceFormatter.formatCurrency(vwap)} (${fmtSigned(distPct)}%) • RVOL ${fmt2(ind.rvol)}x • POC ${PriceFormatter.formatCurrency(poc)}"),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.DERIVATIVES -> {
                val fut = input.futuresData
                if (fut == null) {
                    return unavailableCell(agentId, timeframe, ageMs, nowMs, "No perp funding/OI telemetry (Spot/Equity)")
                }
                val funding = fut.fundingRatePercent
                val lsDiff = (fut.longRatioPercent - fut.shortRatioPercent) / 100.0
                // Extreme positive funding warns crowded longs (mild contrarian negative); moderate positive = constructive
                val stance = when {
                    funding > 0.05 -> -0.35
                    funding < -0.02 -> 0.45
                    else -> (lsDiff * 0.8).coerceIn(-0.6, 0.6)
                }
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = stance,
                    strength = 0.70,
                    freshnessMs = ageMs,
                    reasons = listOf("Funding ${String.format(Locale.US, "%+.4f%%", funding)} • L/S ${fmt1(fut.longRatioPercent)}/${fmt1(fut.shortRatioPercent)} • Mark ${PriceFormatter.formatCurrency(fut.markPrice)}"),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.ORDER_FLOW -> {
                val ob = input.orderBook
                if (ob == null || ob.bids.isEmpty() || ob.asks.isEmpty()) {
                    // Explicit requirement: NEUTRAL / UNAVAILABLE when DOM missing — never fabricate
                    return AgentMatrixCell(
                        agentId = agentId,
                        timeframe = timeframe,
                        stance = 0.0,
                        strength = 0.0,
                        freshnessMs = ageMs,
                        reasons = listOf("DOM depth unavailable for ${input.symbol}"),
                        status = AgentStatus.UNAVAILABLE,
                        computedAtMs = nowMs
                    )
                }
                val topBids = ob.bids.take(20)
                val topAsks = ob.asks.take(20)
                val bidNotional = topBids.sumOf { it.notional }
                val askNotional = topAsks.sumOf { it.notional }
                val total = (bidNotional + askNotional).coerceAtLeast(1.0)
                val imb = ((bidNotional - askNotional) / total).coerceIn(-1.0, 1.0)
                val bestBid = topBids.first().price
                val bestAsk = topAsks.first().price
                val mid = (bestBid + bestAsk) / 2.0
                val spreadBps = if (mid > 0.0) ((bestAsk - bestBid) / mid) * 10_000.0 else 0.0
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = imb,
                    strength = abs(imb).coerceIn(0.15, 0.95),
                    freshnessMs = (nowMs - ob.timestamp).coerceAtLeast(0L),
                    reasons = listOf("DOM L20 imbalance ${fmtSigned(imb * 100.0)}% • spread ${fmt1(spreadBps)} bps"),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.INTERMARKET -> {
                val d1Ind = allInd[Timeframe.ONE_DAY]
                val fut = input.futuresData
                val stance = when {
                    d1Ind != null && price > (d1Ind.ema50.takeIf { it > 0.0 } ?: price) -> 0.35
                    d1Ind != null && price < (d1Ind.ema50.takeIf { it > 0.0 } ?: price) -> -0.35
                    else -> 0.0
                }
                val basisBps = if (fut != null && fut.indexPrice > 0.0) ((fut.markPrice - fut.indexPrice) / fut.indexPrice) * 10_000.0 else 0.0
                val reason = if (fut != null) {
                    "Daily anchor aligned • Perp basis ${fmtSigned(basisBps)} bps"
                } else {
                    "Daily regime & 24h range context: ${ind?.let { fmt1(it.rsi14) } ?: "N/A"} RSI"
                }
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = stance,
                    strength = 0.50,
                    freshnessMs = ageMs,
                    reasons = listOf(reason),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.TRIGGER -> {
                if (ind == null || bars.isEmpty()) return unavailableCell(agentId, timeframe, ageMs, nowMs, "Missing bars")
                val last = bars.last()
                val atr = ind.averageTrueRange.coerceAtLeast(price * 0.001)
                val body = last.close - last.open
                val stance = when {
                    body > atr * 0.35 && ind.rvol >= 1.1 -> 0.75
                    body < -atr * 0.35 && ind.rvol >= 1.1 -> -0.75
                    last.close > ind.sma20 && ind.rsi14 in 48.0..68.0 -> 0.40
                    last.close < ind.sma20 && ind.rsi14 in 32.0..52.0 -> -0.40
                    else -> 0.0
                }
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = stance,
                    strength = abs(stance).coerceIn(0.2, 0.95),
                    freshnessMs = ageMs,
                    reasons = listOf("Last closed bar body ${PriceFormatter.formatCurrency(abs(body))} • RSI ${fmt1(ind.rsi14)} • RVOL ${fmt2(ind.rvol)}x"),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.RISK -> {
                val dailyTripped = input.dailyTradesTaken >= input.maxTradesPerDay || input.realizedRToday <= -input.dailyLossLimitR
                val vetoes = if (dailyTripped) listOf("Daily risk limit hit (${input.dailyTradesTaken}/${input.maxTradesPerDay} trades, ${fmt1(input.realizedRToday)}R)") else emptyList()
                val acctLabel = if (input.accountConfigured) "Account configured • ${input.dailyTradesTaken}/${input.maxTradesPerDay} trades today" else "Account size unset (uncalibrated USD size)"
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = 0.0,
                    strength = if (dailyTripped) 0.0 else if (input.accountConfigured) 0.9 else 0.5,
                    freshnessMs = ageMs,
                    reasons = listOf(acctLabel),
                    vetoes = vetoes,
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.RED_TEAM -> {
                if (ind == null) return unavailableCell(agentId, timeframe, ageMs, nowMs, "No indicators")
                val conflicts = mutableListOf<String>()
                if (ind.rsi14 > 74.0) conflicts.add("Overbought RSI ${fmt1(ind.rsi14)}")
                if (ind.rsi14 < 26.0) conflicts.add("Oversold RSI ${fmt1(ind.rsi14)}")
                val sma20 = ind.sma20.takeIf { it > 0.0 } ?: price
                val distAtr = if (ind.averageTrueRange > 0.0) abs(price - sma20) / ind.averageTrueRange else 0.0
                if (distAtr > 2.5) conflicts.add("Stretched ${fmt1(distAtr)}x ATR from SMA20")
                val antiStance = when {
                    ind.rsi14 > 74.0 || (price > sma20 && distAtr > 2.5) -> -0.55
                    ind.rsi14 < 26.0 || (price < sma20 && distAtr > 2.5) -> 0.55
                    else -> 0.0
                }
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = antiStance,
                    strength = if (conflicts.isEmpty()) 0.35 else 0.80,
                    freshnessMs = ageMs,
                    reasons = listOf(if (conflicts.isEmpty()) "No extreme counter-risk (dist ${fmt1(distAtr)}x ATR)" else conflicts.joinToString("; ")),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.FUNDAMENTALS -> {
                if (input.assetType == AssetType.CRYPTO) {
                    return unavailableCell(agentId, timeframe, ageMs, nowMs, "SEC XBRL N/A for native crypto")
                }
                val facts = input.fundamentals?.facts
                if (facts == null || facts.quarters.isEmpty()) {
                    return unavailableCell(agentId, timeframe, ageMs, nowMs, input.fundamentals?.unavailableReason ?: "SEC fundamentals unavailable")
                }
                val growth = facts.latestYoYGrowthPercent ?: 0.0
                val stance = (growth / 30.0).coerceIn(-0.7, 0.7)
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = stance,
                    strength = 0.65,
                    freshnessMs = (nowMs - input.fundamentals.lastUpdatedMs).coerceAtLeast(0L),
                    reasons = listOf("${facts.quarters.size}Q SEC XBRL • YoY Rev ${fmtSigned(growth)}% • EPS ${facts.latestEpsUsd?.let { fmt2(it) } ?: "N/A"}"),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.NEWS_SENTIMENT -> {
                if (input.news.isEmpty()) {
                    return unavailableCell(agentId, timeframe, ageMs, nowMs, "No recent symbol headlines")
                }
                val posCount = input.news.count { it.sentiment.name.contains("BULL") || it.sentiment.name.contains("POSITIVE") }
                val negCount = input.news.count { it.sentiment.name.contains("BEAR") || it.sentiment.name.contains("NEGATIVE") }
                val total = input.news.size.toDouble().coerceAtLeast(1.0)
                val net = ((posCount - negCount) / total).coerceIn(-1.0, 1.0)
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = net * 0.7,
                    strength = 0.55,
                    freshnessMs = ageMs,
                    reasons = listOf("${input.news.size} headlines (+$posCount / -$negCount)${if (input.eventRisk?.hasNewsShock == true) " • CAUTION shock" else ""}"),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.REPLAY -> {
                val rep = input.replayByTf[timeframe] ?: input.replayByTf.values.firstOrNull()
                    ?: return unavailableCell(agentId, timeframe, ageMs, nowMs, "Replay computed per actionable horizon")
                if (rep.refusalReason != null || rep.resolvedCount <= 0) {
                    return unavailableCell(agentId, timeframe, ageMs, nowMs, rep.refusalReason ?: "0 resolved paths")
                }
                val netEdge = (rep.effectivePWin - 0.5) * 1.5
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = netEdge.coerceIn(-0.85, 0.85),
                    strength = (rep.resolvedCount / rep.numReplays.toDouble().coerceAtLeast(1.0)).coerceIn(0.3, 0.95),
                    freshnessMs = ageMs,
                    reasons = listOf("TP1 ${fmt1(rep.targetFirstPct)}% vs SL ${fmt1(rep.stopFirstPct)}% (${rep.conservativeModeLabel}, seed=${rep.seed}, n=${rep.resolvedCount})"),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.CALIBRATION -> {
                val regime = ind?.let { SignalVetoDetector.classifyMarketRegime(it) } ?: MarketRegime.TREND
                val ev = SignalCalibrationEngine.getEvidenceForModel(
                    symbol = input.symbol,
                    model = EntryModel.A_TREND_PULLBACK,
                    regime = regime,
                    isWithTrend = true,
                    rMultiple = 1.8,
                    sessionStatus = session
                )
                val (ciLo, ciHi) = ev.confidenceInterval95
                val evRSign = if (ev.expectancyR > 0) 0.4 else -0.2
                AgentMatrixCell(
                    agentId = agentId,
                    timeframe = timeframe,
                    stance = if (ev.sampleSize >= 30) evRSign else 0.0,
                    strength = if (ev.sampleSize >= 30) 0.75 else 0.30,
                    freshnessMs = ageMs,
                    reasons = listOf("${ev.winsK}/${ev.sampleSize} (${fmt0(ev.hitRatePercent)}%, CI ${fmt0(ciLo)}-${fmt0(ciHi)}%) • EV ${fmtSigned(ev.expectancyR)}R"),
                    status = AgentStatus.OK,
                    computedAtMs = nowMs
                )
            }

            AgentId.AI_AGENT -> {
                val cb = input.aiCallback
                if (cb != null) {
                    val (stance, msg) = cb(timeframe)
                    AgentMatrixCell(
                        agentId = agentId,
                        timeframe = timeframe,
                        stance = stance.coerceIn(-1.0, 1.0),
                        strength = 0.60,
                        freshnessMs = ageMs,
                        reasons = listOf(msg),
                        sourceTag = AgentSourceTag.AI,
                        status = AgentStatus.OK,
                        computedAtMs = nowMs
                    )
                } else if (input.aiSummary != null && input.aiShiftPoints != 0) {
                    val norm = (input.aiShiftPoints / 15.0).coerceIn(-1.0, 1.0)
                    AgentMatrixCell(
                        agentId = agentId,
                        timeframe = timeframe,
                        stance = norm,
                        strength = abs(norm).coerceIn(0.2, 0.75),
                        freshnessMs = ageMs,
                        reasons = listOf("AI shift ${if (input.aiShiftPoints >= 0) "+${input.aiShiftPoints}" else "${input.aiShiftPoints}"} pts: ${input.aiSummary.take(64)}"),
                        sourceTag = AgentSourceTag.AI,
                        status = AgentStatus.OK,
                        computedAtMs = nowMs
                    )
                } else {
                    AgentMatrixCell(
                        agentId = agentId,
                        timeframe = timeframe,
                        stance = 0.0,
                        strength = 0.0,
                        freshnessMs = ageMs,
                        reasons = listOf("Engine-only mode (optional AI inactive)"),
                        sourceTag = AgentSourceTag.AI,
                        status = AgentStatus.UNAVAILABLE,
                        computedAtMs = nowMs
                    )
                }
            }
        }
    }

    private fun unavailableCell(
        agentId: AgentId,
        tf: Timeframe,
        ageMs: Long,
        nowMs: Long,
        reason: String
    ): AgentMatrixCell = AgentMatrixCell(
        agentId = agentId,
        timeframe = tf,
        stance = 0.0,
        strength = 0.0,
        freshnessMs = ageMs,
        reasons = listOf(reason),
        sourceTag = if (agentId == AgentId.AI_AGENT) AgentSourceTag.AI else AgentSourceTag.ENGINE,
        status = AgentStatus.UNAVAILABLE,
        computedAtMs = nowMs
    )

    private fun fmt0(v: Double): String = String.format(Locale.US, "%.0f", v)
    private fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)
    private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)
    private fun fmtSigned(v: Double): String = String.format(Locale.US, "%+.2f", v)
}
