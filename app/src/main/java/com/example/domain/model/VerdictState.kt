package com.example.domain.model

import com.example.domain.daytrader.news.EventRiskAssessment

/**
 * Single immutable canonical state for the active symbol + market + timeframe.
 * Every UI banner, level card, Replay card, Risk Sizer, and execution action reads
 * strictly from VerdictState. No card computes its own plan or contradictions.
 */
data class VerdictState(
    val symbol: String,
    val marketType: TradingMarketType,
    val timeframe: Timeframe,
    val verdict: SignalVerdictState,
    val reasons: List<String> = emptyList(),
    val vetoes: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val hasStructuralConflicts: Boolean = false,
    val plan: TradeSetupPlan? = null,
    val bullScenario: ScenarioPlan,
    val bearScenario: ScenarioPlan,
    val waitingForCondition: String = "",
    val distanceToTriggerPercent: Double? = null,
    val familyVotes: List<FamilyVote> = emptyList(),
    val familiesAlignedCount: Int = 0,
    val totalFamiliesCount: Int = 7,
    val marketRegime: MarketRegime = MarketRegime.RANGE,
    val regimeMetricsSummary: String = "",
    val evidence: SignalEvidence? = null,
    val eventRisk: EventRiskAssessment? = null,
    val oneLineWhy: String = "",
    val sessionStatus: MarketSessionStatus = MarketSessionStatus.OPEN,
    val livePrice: Double = 0.0,
    val generatedAt: Long = System.currentTimeMillis(),
    val setupGrade: SetupGrade? = null,
    val softFlags: List<SoftFlag> = emptyList(),
    val needsCondition: String = "",
    val bestTimeframeSummary: BestTimeframeSummary? = null,
    val barsLoaded: Int = 0,
    val lastClosedBarTimestamp: Long = 0L,
    val candleAgeMillis: Long = 0L,
    val staleDataWarning: String? = null,
    val offHoursMode: OffHoursMode = OffHoursMode.CAUTION,
    val routedViaFuturesShort: Boolean = false,
    val isContextOnly: Boolean = false,
    val aiShiftPoints: Int = 0,
    val aiShiftModelName: String? = null
) {
    val isTradeActionable: Boolean
        get() = verdict == SignalVerdictState.TRADE && plan != null && plan.isAvailable && plan.recommendedEntry > 0.0 && plan.stopLoss > 0.0

    val hasValidPlan: Boolean
        get() = verdict != SignalVerdictState.NO_TRADE && plan != null && plan.isAvailable && plan.recommendedEntry > 0.0 && plan.stopLoss > 0.0
}
