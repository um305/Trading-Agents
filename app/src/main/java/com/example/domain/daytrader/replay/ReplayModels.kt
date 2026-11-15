package com.example.domain.daytrader.replay

import com.example.domain.daytrader.model.MarketProfileType
import com.example.domain.model.PositionSide

/**
 * Standard Day-Trader Plan passed into the Replay Engine, Exit Planner, and Risk Sizer.
 */
data class ReplayPlan(
    val symbol: String,
    val side: PositionSide,
    val entryPrice: Double,
    val stopLossPrice: Double,
    val takeProfitPrice: Double,
    val entryBarTimestamp: Long,
    val timeOfDayIndex: Int,
    val horizonBars: Int,
    val profileType: MarketProfileType = MarketProfileType.US_EQUITY,
    val takerFeeBps: Double = 4.0,       // 0.04% each way = 0.08% total
    val slippageBps: Double = 2.0,       // 0.02%
    val fundingBpsPer8h: Double = 1.0,   // for perps
    val isEntryBarClosed: Boolean = true
) {
    val riskPerUnit: Double get() = kotlin.math.abs(entryPrice - stopLossPrice)
    val rewardPerUnit: Double get() = kotlin.math.abs(takeProfitPrice - entryPrice)
    val nominalR: Double get() = if (riskPerUnit > 0.0) rewardPerUnit / riskPerUnit else 1.0

    /**
     * Net R:R after deducting round-trip fees, slippage, and hold funding.
     */
    val netR: Double get() {
        if (riskPerUnit <= 0.0) return 1.0
        val costDollars = entryPrice * ((takerFeeBps * 2 + slippageBps + fundingBpsPer8h) / 10000.0)
        val netReward = (rewardPerUnit - costDollars).coerceAtLeast(0.0)
        val grossRisk = riskPerUnit + costDollars
        return netReward / grossRisk
    }

    /**
     * Validates whether the plan is mathematically consistent.
     */
    fun validate(): String? {
        if (entryPrice <= 0.0) return "Invalid plan: entry price must be positive."
        if (stopLossPrice <= 0.0) return "Invalid plan: stop loss must be positive."
        if (takeProfitPrice <= 0.0) return "Invalid plan: target price must be positive."
        if (side == PositionSide.LONG) {
            if (stopLossPrice >= entryPrice) return "Invalid plan: LONG stop loss ($stopLossPrice) must be below entry ($entryPrice)."
            if (takeProfitPrice <= entryPrice) return "Invalid plan: LONG target ($takeProfitPrice) must be above entry ($entryPrice)."
        } else {
            if (stopLossPrice <= entryPrice) return "Invalid plan: SHORT stop loss ($stopLossPrice) must be above entry ($entryPrice)."
            if (takeProfitPrice >= entryPrice) return "Invalid plan: SHORT target ($takeProfitPrice) must be below entry ($entryPrice)."
        }
        if (!isEntryBarClosed) return "Invalid plan: entry bar is not closed (completed bars only)."
        return null
    }
}

data class ReplayDistributionPoint(
    val barIndex: Int,
    val p5: Double,
    val p25: Double,
    val p50: Double,
    val p75: Double,
    val p95: Double
)

data class PriceHistogramBin(
    val binMin: Double,
    val binMax: Double,
    val count: Int,
    val percent: Double
)

enum class ReplayLean(val label: String) {
    STRONGLY_TARGET("Strongly toward the target"),
    LEANS_TARGET("Leans toward the target"),
    CLOSE_TO_BREAKEVEN("Close to break-even"),
    LEANS_STOP("Leans toward the stop"),
    STRONGLY_STOP("Strongly toward the stop"),
    INSUFFICIENT_RESOLVED("Not enough resolved replays"),
    REFUSED("Replay Refused")
}

data class ReplayVerdict(
    val lean: ReplayLean,
    val headline: String,
    val details: List<String>,
    val breakEvenTargetShare: Double,
    val observedTargetShare: Double,
    val seed: Long
)

data class HistoricalBacktestSummary(
    val firedCount: Int,
    val targetCount: Int,
    val stopCount: Int,
    val neitherCount: Int,
    val averageR: Double
)

data class ReplayPassMetrics(
    val modeLabel: String,
    val targetFirstPct: Double,
    val stopFirstPct: Double,
    val neitherPct: Double,
    val greenPct: Double,
    val medianPrice: Double,
    val p5Price: Double,
    val p95Price: Double,
    val averageR: Double,
    val netAverageR: Double,
    val observedTargetShare: Double,
    val fanChartBands: List<ReplayDistributionPoint>,
    val histogramBins: List<PriceHistogramBin>,
    val history: HistoricalBacktestSummary? = null,
    val resolvedCount: Int = 0,
    val sessionsCount: Int = 0
)

data class ReplayResult(
    val symbol: String,
    val seed: Long,
    val numReplays: Int,
    val targetFirstPct: Double,
    val stopFirstPct: Double,
    val neitherPct: Double,
    val greenPct: Double,
    val medianPrice: Double,
    val p5Price: Double,
    val p95Price: Double,
    val averageR: Double,
    val netAverageR: Double,
    val fanChartBands: List<ReplayDistributionPoint>,
    val histogramBins: List<PriceHistogramBin>,
    val verdict: ReplayVerdict,
    val history: HistoricalBacktestSummary?,
    val plainTargetFirstPct: Double = 0.0,
    val plainStopFirstPct: Double = 0.0,
    val refusalReason: String? = null,
    val disclaimerNotice: String = "Built from the last 60 days; events and news can break it; not a prediction",
    val asIs: ReplayPassMetrics? = null,
    val driftNeutral: ReplayPassMetrics? = null,
    val effectivePWin: Double = 0.0,
    val conservativeModeLabel: String = "Drift-Neutral",
    val oddsDiffPoints: Double = 0.0,
    val driftDivergenceNote: String? = null,
    val resolvedCount: Int = 0,
    val sessionsCount: Int = 0,
    val isWeekendFallbackCombined: Boolean = false,
    val poolNotice: String? = null
)
