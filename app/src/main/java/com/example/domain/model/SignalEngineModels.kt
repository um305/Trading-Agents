package com.example.domain.model

/**
 * Signal Engine v2 Core Data Models
 * Designed for selective, explainable trading with positive expectancy.
 */

enum class SignalVerdictState(val label: String) {
    TRADE("TRADE"),
    WAIT("WAIT"),
    NO_TRADE("NO TRADE")
}

enum class SetupGrade(val label: String, val badgeText: String, val sizeMultiplier: Double) {
    A("Grade A", "GRADE A • 1.0x SIZE", 1.0),
    B("Grade B", "GRADE B • 0.5x SIZE", 0.5),
    C_WATCH("C / WATCH", "C / WATCH • ALERT ONLY", 0.0)
}

enum class SoftFlagType(val label: String) {
    DEAD_REGIME("Dead regime"),
    OVER_EXTENDED("Over-extended"),
    MIXED_STRUCTURE("Mixed structure"),
    HTF_OPPOSED("HTF opposed"),
    LOW_RVOL("Low RVOL"),
    UNPROVEN_MODEL("Unproven model (n < 30)"),
    REPLAY_BREAKEVEN("Replay close to break-even"),
    OFF_HOURS_CAUTION("Off-hours / thin book"),
    LOW_ALIGNMENT("Below-threshold family alignment"),
    MIXED_DATA_EXECUTION("Spot data mixed with perp execution"),
    RR_BELOW_1_5("Needs entry price for 1.5R"),
    OUTSIDE_ENTRY_WINDOW("Outside permitted entry window"),
    NEWS_SHOCK_CAUTION("News shock CAUTION (size x0.5, buffer x1.5)")
}

data class SoftFlag(
    val type: SoftFlagType,
    val label: String,
    val detail: String,
    val actionHint: String
)

data class BestTimeframeSummary(
    val bestTf: Timeframe,
    val side: PositionSide,
    val modelTitle: String,
    val grade: SetupGrade,
    val headline: String,
    val whyNotChartTf: String
)

enum class SignalFamily(val displayName: String, val weight: Double) {
    TREND("Trend & Market Structure", 1.0),
    STRUCTURE("Structure & Location (SMC)", 1.0),
    VALUE_VOLUME("Value & Volume Profile", 1.0),
    VOLATILITY("Volatility Regime", 1.0),
    MOMENTUM_TIMING("Momentum & Timing", 1.0),
    FLOW_POSITIONING("Derivatives & Order Flow", 1.0),
    CONTEXT("Macro & Sentiment Context", 1.0)
}

data class FamilyVote(
    val family: SignalFamily,
    val stance: AgentStance,
    val signedStrength: Double, // -1.0 (strong short) to +1.0 (strong long)
    val freshness: Double = 1.0, // 0.0 to 1.0
    val weight: Double = 1.0,
    val reasons: List<String> = emptyList(),
    val rawValues: Map<String, String> = emptyMap(),
    val isAlignedWithCandidate: Boolean = false
)

enum class MarketRegime(val label: String, val description: String) {
    TREND("Trend", "Directional expansion with aligned moving averages and ADX >= 20"),
    RANGE("Range", "Consolidation within defined value extremes with ADX < 20"),
    EXPANSION("Expansion", "Volatility breakout with high relative volume (RVOL >= 1.5)"),
    DEAD("Dead / Low Volatility", "Compressed volume, tight bandwidth, negative expectancy")
}

enum class EntryModel(
    val code: String,
    val title: String,
    val allowedRegimes: List<MarketRegime>,
    val description: String
) {
    A_TREND_PULLBACK(
        "A",
        "Trend Pullback",
        listOf(MarketRegime.TREND, MarketRegime.EXPANSION),
        "Pullback into value (EMA 20-50, VWAP/POC, 0.5-0.786 Fib, fresh OB/FVG) with trend-aligned trigger"
    ),
    B_SWEEP_RECLAIM(
        "B",
        "Sweep + Reclaim",
        listOf(MarketRegime.TREND, MarketRegime.RANGE, MarketRegime.EXPANSION),
        "Liquidity sweep of key extreme followed by reclaim displacement and LTF MSS confirmation"
    ),
    C_BREAKOUT_RETEST(
        "C",
        "Breakout + Retest",
        listOf(MarketRegime.EXPANSION, MarketRegime.TREND),
        "Post-squeeze range expansion with RVOL >= 1.5, entering on retest of the broken level"
    ),
    D_RANGE_FADE(
        "D",
        "Range Fade",
        listOf(MarketRegime.RANGE),
        "Fade of value-area or range extremes back toward POC/midline when ADX < 20"
    )
}

data class TimeframeTriplet(
    val biasTf: Timeframe,
    val setupTf: Timeframe,
    val triggerTf: Timeframe
) {
    companion object {
        fun fromTriggerTimeframe(trigger: Timeframe): TimeframeTriplet {
            return when (trigger) {
                Timeframe.ONE_MIN, Timeframe.FIVE_MIN -> TimeframeTriplet(
                    biasTf = Timeframe.ONE_HOUR,
                    setupTf = Timeframe.FIFTEEN_MIN,
                    triggerTf = Timeframe.FIVE_MIN
                )
                Timeframe.FIFTEEN_MIN, Timeframe.THIRTY_MIN -> TimeframeTriplet(
                    biasTf = Timeframe.FOUR_HOURS,
                    setupTf = Timeframe.ONE_HOUR,
                    triggerTf = Timeframe.FIFTEEN_MIN
                )
                Timeframe.ONE_HOUR, Timeframe.FOUR_HOURS -> TimeframeTriplet(
                    biasTf = Timeframe.ONE_DAY,
                    setupTf = Timeframe.FOUR_HOURS,
                    triggerTf = Timeframe.ONE_HOUR
                )
                else -> TimeframeTriplet(
                    biasTf = Timeframe.ONE_DAY,
                    setupTf = Timeframe.FOUR_HOURS,
                    triggerTf = Timeframe.ONE_HOUR
                )
            }
        }
    }
}

enum class MarketSessionStatus(val label: String, val isOpen: Boolean, val warningNotice: String? = null) {
    OPEN("OPEN", true, null),
    EXTENDED("EXTENDED", true, "Extended hours (pre/post-market). Lower volume & wider spreads."),
    OFF_HOURS("OFF-HOURS", false, "Cash market closed. High gap risk on reopening."),
    WEEKEND("WEEKEND", false, "Weekend thin liquidity window. Wide spreads possible.")
}

enum class OffHoursMode(val label: String, val description: String) {
    BLOCK("Block", "Strict veto to NO TRADE when underlying equity cash market is closed"),
    CAUTION("Caution", "Allowed only if order flow agrees; stop buffer x1.5, size x0.5, sweep/pullback models only"),
    TREAT_AS_24_7("Treat as 24/7", "No adjustments, session badge only")
}

data class ScenarioPlan(
    val isBull: Boolean,
    val isActive: Boolean,
    val triggerCondition: String,
    val invalidationLevel: Double,
    val targetLevel: Double,
    val narrative: String
)

data class SignalEvidence(
    val modelName: String,
    val regime: String,
    val sessionState: MarketSessionStatus = MarketSessionStatus.OPEN,
    val sampleSize: Int,
    val winsK: Int = 0,
    val hitRatePercent: Double,
    val baselinePercent: Double = 50.0,
    val learnedEdgePercent: Double = 0.0,
    val confidenceInterval95: Pair<Double, Double> = Pair(0.0, 0.0),
    val expectancyR: Double,
    val maxDrawdownPercent: Double,
    val isUnproven: Boolean
)

data class CandidateSetup(
    val direction: PositionSide,
    val model: EntryModel,
    val regimeNeeded: MarketRegime,
    val entryPrice: Double,
    val entryZoneMin: Double,
    val entryZoneMax: Double,
    val stopLossPrice: Double,
    val stopDistancePercent: Double,
    val stopBufferAmount: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double,
    val netRiskRewardTp1: Double,
    val netRiskRewardTp2: Double,
    val netRiskRewardTp3: Double,
    val probabilityOfTouchTp1: Double,
    val probabilityOfTouchTp2: Double,
    val probabilityOfTouchTp3: Double,
    val expectedValueR: Double,
    val pWin: Double,
    val isUnproven: Boolean,
    val sampleSize: Int,
    val recommendedPositionSizePercent: Double,
    val recommendedLeverage: Int,
    val expiryCandles: Int = 6,
    val timeStopCandles: Int = 12,
    val triggerCondition: String,
    val invalidationDescription: String,
    val whyThisEntry: String,
    val alignedFamiliesCount: Int,
    val alignedFamilies: List<SignalFamily>,
    val familyVotes: List<FamilyVote> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val redTeamConflictScore: Double = 0.0,
    val isAgainstHtfTrend: Boolean = false,
    val gapRiskWarning: String? = null,
    val setupGrade: SetupGrade = SetupGrade.A,
    val softFlags: List<SoftFlag> = emptyList(),
    val needsCondition: String = "",
    val routedViaFuturesShort: Boolean = false,
    val farZones: List<String> = emptyList()
)

data class SignalEngineOutput(
    val verdictState: SignalVerdictState,
    val symbol: String,
    val currentLivePrice: Double,
    val primarySetup: CandidateSetup?,
    val bullScenario: ScenarioPlan,
    val bearScenario: ScenarioPlan,
    val vetoReasons: List<String> = emptyList(),
    val oneLineWhy: String,
    val familiesAlignedCount: Int = 0,
    val totalFamiliesCount: Int = 7,
    val alignedFamilies: List<SignalFamily> = emptyList(),
    val allFamilyVotes: List<FamilyVote> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val evidence: SignalEvidence? = null,
    val sessionStatus: MarketSessionStatus = MarketSessionStatus.OPEN,
    val calibratedConfidence: Int = 50,
    val redTeamConflictScore: Double = 0.0,
    val marketRegime: MarketRegime = MarketRegime.TREND,
    val generatedAt: Long = System.currentTimeMillis(),
    val setupGrade: SetupGrade? = null,
    val softFlags: List<SoftFlag> = emptyList(),
    val needsCondition: String = "",
    val barsLoaded: Int = 0,
    val routedViaFuturesShort: Boolean = false,
    val isContextOnly: Boolean = false,
    val offHoursMode: OffHoursMode = OffHoursMode.CAUTION,
    val aiShiftPoints: Int = 0,
    val aiShiftModelName: String? = null
)
