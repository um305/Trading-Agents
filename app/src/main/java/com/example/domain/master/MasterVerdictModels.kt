package com.example.domain.master

import com.example.domain.daytrader.replay.ReplayDistributionPoint
import com.example.domain.model.AssetType
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSessionStatus
import com.example.domain.model.PositionSide
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.SoftFlag
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import com.example.domain.util.PriceFormatter
import java.util.Locale

enum class TradingHorizon(
    val label: String,
    val shortLabel: String,
    val defaultBiasTf: Timeframe,
    val defaultSetupTf: Timeframe,
    val defaultTriggerTf: Timeframe,
    val holdLabel: String,
    val validityDurationMs: Long,
    val aggregationWeight: Double
) {
    SCALP(
        label = "Scalp",
        shortLabel = "Scalp",
        defaultBiasTf = Timeframe.ONE_HOUR,
        defaultSetupTf = Timeframe.FIFTEEN_MIN,
        defaultTriggerTf = Timeframe.FIVE_MIN,
        holdLabel = "~3h (12 setup bars)",
        validityDurationMs = 3L * 3600_000L,
        aggregationWeight = 0.15
    ),
    INTRADAY(
        label = "Intraday",
        shortLabel = "Intraday",
        defaultBiasTf = Timeframe.FOUR_HOURS,
        defaultSetupTf = Timeframe.ONE_HOUR,
        defaultTriggerTf = Timeframe.FIFTEEN_MIN,
        holdLabel = "up to 24h (stocks: flat-by)",
        validityDurationMs = 12L * 3600_000L,
        aggregationWeight = 0.25
    ),
    SWING(
        label = "Swing",
        shortLabel = "Swing",
        defaultBiasTf = Timeframe.ONE_DAY,
        defaultSetupTf = Timeframe.FOUR_HOURS,
        defaultTriggerTf = Timeframe.ONE_HOUR,
        holdLabel = "2–10 days",
        validityDurationMs = 72L * 3600_000L,
        aggregationWeight = 0.30
    ),
    POSITION(
        label = "Position",
        shortLabel = "Position",
        defaultBiasTf = Timeframe.FIVE_DAYS, // Proxy for 1W weekly in Timeframe enum
        defaultSetupTf = Timeframe.ONE_DAY,
        defaultTriggerTf = Timeframe.FOUR_HOURS,
        holdLabel = "2–8 weeks",
        validityDurationMs = 14L * 24L * 3600_000L,
        aggregationWeight = 0.30
    ),
    OUTLOOK(
        label = "Outlook",
        shortLabel = "Outlook",
        defaultBiasTf = Timeframe.ONE_YEAR,
        defaultSetupTf = Timeframe.THREE_MONTHS,
        defaultTriggerTf = Timeframe.ONE_MONTH,
        holdLabel = "Context only (1M / 3M / 1Y)",
        validityDurationMs = 30L * 24L * 3600_000L,
        aggregationWeight = 0.0
    );

    companion object {
        val ACTIONABLE: List<TradingHorizon> = listOf(SCALP, INTRADAY, SWING, POSITION)

        /**
         * Event-driven horizon filter: when a bar of [tf] closes, recompute ONLY the horizons that depend on [tf].
         */
        fun affectedByClosedTimeframe(tf: Timeframe): Set<TradingHorizon> {
            return when (tf) {
                Timeframe.ONE_MIN, Timeframe.FIVE_MIN -> setOf(SCALP)
                Timeframe.FIFTEEN_MIN, Timeframe.THIRTY_MIN -> setOf(SCALP, INTRADAY)
                Timeframe.ONE_HOUR -> setOf(SCALP, INTRADAY)
                Timeframe.FOUR_HOURS -> setOf(SWING)
                Timeframe.ONE_DAY, Timeframe.FIVE_DAYS -> setOf(POSITION, OUTLOOK)
                Timeframe.ONE_MONTH, Timeframe.THREE_MONTHS, Timeframe.ONE_YEAR -> setOf(OUTLOOK)
            }
        }
    }
}

data class HorizonTripletConfig(
    val biasTf: Timeframe,
    val setupTf: Timeframe,
    val triggerTf: Timeframe
)

data class HorizonSettings(
    val scalp: HorizonTripletConfig = HorizonTripletConfig(Timeframe.ONE_HOUR, Timeframe.FIFTEEN_MIN, Timeframe.FIVE_MIN),
    val intraday: HorizonTripletConfig = HorizonTripletConfig(Timeframe.FOUR_HOURS, Timeframe.ONE_HOUR, Timeframe.FIFTEEN_MIN),
    val swing: HorizonTripletConfig = HorizonTripletConfig(Timeframe.ONE_DAY, Timeframe.FOUR_HOURS, Timeframe.ONE_HOUR),
    val position: HorizonTripletConfig = HorizonTripletConfig(Timeframe.FIVE_DAYS, Timeframe.ONE_DAY, Timeframe.FOUR_HOURS)
) {
    fun tripletFor(horizon: TradingHorizon): HorizonTripletConfig = when (horizon) {
        TradingHorizon.SCALP -> scalp
        TradingHorizon.INTRADAY -> intraday
        TradingHorizon.SWING -> swing
        TradingHorizon.POSITION -> position
        TradingHorizon.OUTLOOK -> HorizonTripletConfig(Timeframe.ONE_YEAR, Timeframe.THREE_MONTHS, Timeframe.ONE_MONTH)
    }

    fun toPrefString(): String {
        return listOf(
            "SCALP:${scalp.biasTf.name},${scalp.setupTf.name},${scalp.triggerTf.name}",
            "INTRADAY:${intraday.biasTf.name},${intraday.setupTf.name},${intraday.triggerTf.name}",
            "SWING:${swing.biasTf.name},${swing.setupTf.name},${swing.triggerTf.name}",
            "POSITION:${position.biasTf.name},${position.setupTf.name},${position.triggerTf.name}"
        ).joinToString(";")
    }

    companion object {
        fun fromPrefString(raw: String?): HorizonSettings {
            if (raw.isNullOrBlank()) return HorizonSettings()
            var out = HorizonSettings()
            raw.split(";").forEach { part ->
                val kv = part.split(":")
                if (kv.size == 2) {
                    val tfs = kv[1].split(",").mapNotNull { name ->
                        runCatching { Timeframe.valueOf(name) }.getOrNull()
                    }
                    if (tfs.size == 3) {
                        val cfg = HorizonTripletConfig(tfs[0], tfs[1], tfs[2])
                        out = when (kv[0]) {
                            "SCALP" -> out.copy(scalp = cfg)
                            "INTRADAY" -> out.copy(intraday = cfg)
                            "SWING" -> out.copy(swing = cfg)
                            "POSITION" -> out.copy(position = cfg)
                            else -> out
                        }
                    }
                }
            }
            return out
        }
    }
}

enum class AgentId(val displayName: String, val shortName: String, val stageIndex: Int) {
    DATA_INTEGRITY("Data Integrity", "Data", 0),
    SESSION_EVENT("Session & Event", "Event", 1),
    REGIME("Regime", "Regime", 2),
    MTF_BIAS("MTF Bias", "Bias", 3),
    STRUCTURE_SMC("Structure (SMC)", "SMC", 4),
    VALUE_VOLUME("Value / Volume", "Value", 4),
    DERIVATIVES("Derivatives", "Deriv", 5),
    ORDER_FLOW("Order Flow (DOM)", "Flow", 5),
    INTERMARKET("Intermarket", "Macro", 5),
    TRIGGER("Trigger", "Trig", 6),
    RISK("Risk Sizer", "Risk", 7),
    RED_TEAM("Red Team", "RedTm", 8),
    FUNDAMENTALS("Fundamentals", "Fund", 8),
    NEWS_SENTIMENT("News & Sentiment", "News", 8),
    REPLAY("Replay (Bootstrap)", "Replay", 9),
    CALIBRATION("Calibration (k/n)", "Calib", 10),
    AI_AGENT("AI Synthesis", "AI", 11)
}

enum class AgentStatus { OK, FAILED, UNAVAILABLE }

enum class AgentSourceTag(val label: String) { ENGINE("Engine"), AI("AI") }

data class AgentMatrixCell(
    val agentId: AgentId,
    val timeframe: Timeframe,
    val stance: Double, // -1.0 .. +1.0
    val strength: Double, // 0.0 .. 1.0
    val freshnessMs: Long,
    val reasons: List<String> = emptyList(),
    val vetoes: List<String> = emptyList(),
    val sourceTag: AgentSourceTag = AgentSourceTag.ENGINE,
    val status: AgentStatus = AgentStatus.OK,
    val errorMessage: String? = null,
    val computedAtMs: Long = System.currentTimeMillis()
)

data class TimeframeConsensus(
    val timeframe: Timeframe,
    val tfDisplayLabel: String,
    val biasLabel: String, // "BULL", "BEAR", "NEUTRAL"
    val agreementPercent: Int,
    val netScore: Double
)

data class AgentMatrix(
    val timeframes: List<Timeframe>, // 5m, 15m, 1h, 4h, 1D, 1W (FIVE_DAYS), 1M
    val cells: List<AgentMatrixCell>,
    val consensusByTf: Map<Timeframe, TimeframeConsensus>,
    val failedCount: Int,
    val whatWouldChangeThis: List<String>
) {
    fun cell(agentId: AgentId, timeframe: Timeframe): AgentMatrixCell? =
        cells.firstOrNull { it.agentId == agentId && it.timeframe == timeframe }
}

data class ProjectionFuturesView(
    val side: PositionSide,
    val entryZoneMin: Double,
    val entryZoneMax: Double,
    val limitPrice: Double,
    val stopLoss: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val tp1R: Double,
    val tp2R: Double,
    val tp3R: Double,
    val tp1UnleveragedPct: Double,
    val tp2UnleveragedPct: Double,
    val tp3UnleveragedPct: Double,
    val recommendedLeverage: Int, // liquidation distance >= 3 * stopDistance
    val liquidationPrice: Double,
    val positionSizeContracts: Double,
    val positionSizeUsd: Double,
    val leverageSourceLabel: String // "Engine rec" when engine-only
)

enum class SpotActionMode { BUY, SELL_REDUCE_AND_WAIT_BUY, NO_SPOT_PLAN }

data class ProjectionSpotView(
    val mode: SpotActionMode,
    val headline: String,
    val buyZoneMin: Double? = null,
    val buyZoneMax: Double? = null,
    val buyLimitPrice: Double? = null,
    val invalidationPrice: Double? = null,
    val doNotBuyAbove: Double? = null,
    val sellTargets: List<Double> = emptyList(),
    val reduceZoneMin: Double? = null,
    val reduceZoneMax: Double? = null,
    val waitBuyWhy: String? = null,
    val waitBuyTrigger: String? = null,
    val futuresShortAvailable: Boolean = true,
    val reasonIfNone: String? = null
)

data class HorizonPlan(
    val horizon: TradingHorizon,
    val biasTf: Timeframe,
    val setupTf: Timeframe,
    val triggerTf: Timeframe,
    val side: PositionSide?, // null when NONE
    val state: SignalVerdictState, // TRADE, WAIT, NO_TRADE
    val grade: SetupGrade,
    val entryModel: String,
    val regime: MarketRegime,
    val sessionStatus: MarketSessionStatus,
    val triggerPrice: Double?,
    val triggerCondition: String,
    val entryZoneMin: Double?,
    val entryZoneMax: Double?,
    val limitPrice: Double?,
    val stopLoss: Double?,
    val tp1: Double?,
    val tp2: Double?,
    val tp3: Double?,
    val tp1Rr: Double?,
    val tp2Rr: Double?,
    val tp3Rr: Double?,
    val tp1TouchProb: Double?,
    val tp2TouchProb: Double?,
    val tp3TouchProb: Double?,
    val pWin: Double,
    val winsK: Int,
    val sampleN: Int,
    val wilsonLowPct: Double,
    val wilsonHighPct: Double,
    val evR: Double?,
    val replaySeed: Long,
    val replayResolvedN: Int,
    val replayHeadline: String,
    val replayConservativeMode: String,
    val replayTargetFirstPct: Double,
    val replayStopFirstPct: Double,
    val replayFanBands: List<ReplayDistributionPoint>,
    val validUntilMs: Long,
    val needs: String,
    val conflicts: List<String>,
    val vetoes: List<String>,
    val softFlags: List<SoftFlag>,
    val agentVotes: Map<AgentId, Double>,
    val dataAgeMs: Long,
    val computedAtMs: Long,
    val isCounterTrend: Boolean,
    val sizeMultiplier: Double,
    val isStale: Boolean,
    val farZones: List<String>,
    val bslLevel: Double?,
    val sslLevel: Double?,
    val futuresView: ProjectionFuturesView?,
    val spotView: ProjectionSpotView
) {
    fun formatZoneOrDash(): String {
        val lo = entryZoneMin ?: return "-"
        val hi = entryZoneMax ?: return "-"
        if (lo <= 0.0 || hi <= 0.0) return "-"
        return PriceFormatter.formatZoneRange(lo, hi)
    }

    fun formatTriggerPriceOrDash(): String {
        val p = triggerPrice ?: return "-"
        return if (p > 0.0) PriceFormatter.formatCurrency(p) else "-"
    }

    fun formatStopOrDash(): String {
        val s = stopLoss ?: return "-"
        return if (s > 0.0) PriceFormatter.formatCurrency(s) else "-"
    }

    fun formatPWinWithWilson(): String {
        val pct = (pWin * 100.0).coerceIn(0.0, 100.0)
        return if (sampleN > 0) {
            String.format(
                Locale.US,
                "%.0f%% (%d/%d • 95%% CI %.0f–%.0f%%)",
                pct,
                winsK,
                sampleN,
                wilsonLowPct,
                wilsonHighPct
            )
        } else {
            String.format(Locale.US, "%.0f%% (0/0 uncalibrated)", pct)
        }
    }
}

data class BiasEntry(
    val timeframe: Timeframe,
    val displayLabel: String,
    val bias: String, // "BULL", "BEAR", "NEUTRAL"
    val strength: Double,
    val detail: String
)

data class OutlookZoneLevel(
    val minPrice: Double,
    val maxPrice: Double,
    val label: String,
    val kind: String // "SUPPORT", "RESISTANCE", "ACCUMULATION", "DISTRIBUTION"
)

data class OutlookSummary(
    val timeframesCovered: List<String>, // ["1M", "3M", "1Y"]
    val trendBias: String, // "BULL", "BEAR", "NEUTRAL"
    val biasStrength: Double,
    val keySupports: List<Double>,
    val keyResistances: List<Double>,
    val accumulationZones: List<OutlookZoneLevel>,
    val distributionZones: List<OutlookZoneLevel>,
    val summaryText: String,
    val note: String = "Outlook (1M / 3M / 1Y): long-term context only — no active entries."
)

data class MasterVerdict(
    val symbol: String,
    val assetType: AssetType,
    val marketType: TradingMarketType,
    val livePrice: Double,
    val headline: String, // e.g., "BTC Futures: LONG bias, 75% aligned"
    val weightedBiasSide: PositionSide?,
    val alignmentPercent: Int,
    val biasLadder: List<BiasEntry>,
    val horizonPlans: Map<TradingHorizon, HorizonPlan>,
    val bestHorizon: TradingHorizon,
    val bestPlan: HorizonPlan?,
    val outlook: OutlookSummary,
    val matrix: AgentMatrix,
    val conflicts: List<String>,
    val plainSummaryHeadline: String,
    val plainBestPlanNumbers: String,
    val plainInvalidation: String,
    val whatFlipsBias: String,
    val standardModeNote: String? = null,
    val isStale: Boolean = false,
    val seed: Long = 19L,
    val computedAtMs: Long = System.currentTimeMillis(),
    val aiShiftPoints: Int = 0,
    val aiShiftModelName: String? = null
)
