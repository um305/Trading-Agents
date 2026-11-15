package com.example.domain.daytrader.scanner

import com.example.domain.daytrader.replay.ReplayVerdict
import com.example.domain.model.AssetType
import com.example.domain.model.BestTimeframeSummary
import com.example.domain.model.PositionSide
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.SoftFlag
import com.example.domain.model.Timeframe

enum class ScanRuleType(val label: String, val side: PositionSide) {
    BREAKOUT_20D_HIGH("20-Day Closing High Breakout", PositionSide.LONG),
    BREAKDOWN_20D_LOW("20-Day Closing Low Breakdown", PositionSide.SHORT),
    BULL_FLAG("Bull Flag Consolidation", PositionSide.LONG),
    BEAR_FLAG("Bear Flag Consolidation", PositionSide.SHORT),
    RECLAIM_SMA20("Reclaim of 20-Day Average", PositionSide.LONG),
    LOST_SMA20("Lost 20-Day Average", PositionSide.SHORT),
    OVERSOLD_RSI("Oversold (RSI14 < 30)", PositionSide.LONG),
    OVERBOUGHT_RSI("Overbought (RSI14 > 70)", PositionSide.SHORT),
    OVERSOLD_BOUNCE("Oversold Bounce Reversal", PositionSide.LONG),
    OVERBOUGHT_REJECTION("Overbought Rejection", PositionSide.SHORT),
    OR_BREAKOUT_BULL("Opening Range 15m Bullish Breakout", PositionSide.LONG),
    OR_BREAKDOWN_BEAR("Opening Range 15m Bearish Breakdown", PositionSide.SHORT)
}

data class ScanHitItem(
    val symbol: String,
    val name: String,
    val assetType: AssetType,
    val matchedRule: ScanRuleType,
    val ruleDescription: String,
    val side: PositionSide,
    val currentPrice: Double,
    val change24hPercent: Double,
    val volume24hUsd: Double,
    val rvol: Double,
    val marketRegime: String,
    val familiesAligned: Int,
    val replayVerdict: ReplayVerdict? = null,
    val replayChipLabel: String? = null,
    val sessionBadge: String = "OPEN",
    val triggerLevel: Double = 0.0,
    val stopLevel: Double = 0.0,
    val targetLevel: Double = 0.0,
    val quantitativeSummary: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class ScanSetupItem(
    val symbol: String,
    val name: String,
    val assetType: AssetType,
    val timeframe: Timeframe,
    val verdict: SignalVerdictState,
    val grade: SetupGrade?,
    val modelTitle: String,
    val side: PositionSide,
    val currentPrice: Double,
    val change24hPercent: Double,
    val volume24hUsd: Double,
    val rvol: Double,
    val marketRegime: String,
    val familiesAligned: Int,
    val softFlags: List<SoftFlag> = emptyList(),
    val hardVetoes: List<String> = emptyList(),
    val distanceToTriggerPercent: Double? = null,
    val netRrTp1: Double = 0.0,
    val triggerPrice: Double = 0.0,
    val stopLevel: Double = 0.0,
    val targetLevel: Double = 0.0,
    val needsCondition: String = "",
    val bestTimeframeSummary: BestTimeframeSummary? = null,
    val replayChipLabel: String? = null,
    val sessionBadge: String = "OPEN",
    val barsLoaded: Int = 0
)

data class BlockerReasonCount(
    val reason: String,
    val count: Int
)

data class ScanMarketSummary(
    val universeLabel: String,
    val totalEvaluated: Int,
    val pctBelowEma20: Double,
    val pctBelowEma50: Double,
    val btcRegime: String,
    val gradeACount: Int,
    val gradeBCount: Int,
    val watchCount: Int,
    val noTradeCount: Int,
    val rankedBlockers: List<BlockerReasonCount> = emptyList(),
    val closestWatchSetups: List<ScanSetupItem> = emptyList()
)

data class ScanDiagnosticReport(
    val totalSymbolsScanned: Int,
    val chartsReturnedCount: Int,
    val usableChartsCount: Int,
    val executionTimeMs: Long,
    val dataTimestamp: Long = System.currentTimeMillis(),
    val universeLabel: String = "Watchlist + Top Liquid"
)
