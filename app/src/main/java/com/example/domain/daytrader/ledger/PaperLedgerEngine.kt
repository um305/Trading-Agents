package com.example.domain.daytrader.ledger

import com.example.data.db.TradeOutcomeEntity
import com.example.domain.engine.SignalCalibrationEngine
import com.example.domain.model.EntryModel
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSessionStatus
import java.util.Locale

data class LedgerGroupStats(
    val groupLabel: String,
    val totalTrades: Int,
    val winRatePercent: Double,
    val expectancyR: Double,
    val totalRealizedR: Double,
    val maxDrawdownR: Double
)

data class PaperLedgerSummary(
    val totalTrades: Int,
    val realTradesCount: Int,
    val paperTradesCount: Int,
    val winsCount: Int,
    val lossesCount: Int,
    val overallWinRatePercent: Double,
    val overallExpectancyR: Double,
    val overallRealizedR: Double,
    val maxDrawdownR: Double,
    val statsByEntryModel: List<LedgerGroupStats>,
    val statsBySession: List<LedgerGroupStats>,
    val statsByAssetType: List<LedgerGroupStats>
)

object PaperLedgerEngine {

    /**
     * Aggregates trade outcomes from the database into institutional day-trader ledger analytics,
     * broken down by entry model, session state, and asset type.
     */
    fun computeLedgerSummary(outcomes: List<TradeOutcomeEntity>): PaperLedgerSummary {
        val decidedOutcomes = outcomes.filter { it.filled && it.status in listOf("TP1_HIT", "TP2_HIT", "TP3_HIT", "STOPPED_OUT", "FLAT_BY_CLOSED", "CLOSED") }

        if (decidedOutcomes.isEmpty()) {
            return PaperLedgerSummary(
                totalTrades = 0,
                realTradesCount = 0,
                paperTradesCount = 0,
                winsCount = 0,
                lossesCount = 0,
                overallWinRatePercent = 0.0,
                overallExpectancyR = 0.0,
                overallRealizedR = 0.0,
                maxDrawdownR = 0.0,
                statsByEntryModel = emptyList(),
                statsBySession = emptyList(),
                statsByAssetType = emptyList()
            )
        }

        val totalDecided = decidedOutcomes.size
        val realCount = decidedOutcomes.count { it.isRealTrade }
        val paperCount = decidedOutcomes.count { !it.isRealTrade }
        val wins = decidedOutcomes.count { it.realizedR > 0.0 }
        val losses = decidedOutcomes.count { it.realizedR <= 0.0 }
        val winRate = (wins.toDouble() / totalDecided) * 100.0
        val totalR = decidedOutcomes.sumOf { it.realizedR }
        val expectancyR = totalR / totalDecided

        // Max drawdown calculation in R
        var peakR = 0.0
        var cumulativeR = 0.0
        var maxDd = 0.0
        decidedOutcomes.sortedBy { it.createdAt }.forEach { t ->
            cumulativeR += t.realizedR
            if (cumulativeR > peakR) peakR = cumulativeR
            val dd = peakR - cumulativeR
            if (dd > maxDd) maxDd = dd
        }

        // Group by Entry Model
        val byModel = decidedOutcomes.groupBy { it.entryModel.ifBlank { it.setupType } }.map { (modelName, list) ->
            computeGroupStats(modelName, list)
        }.sortedByDescending { it.expectancyR }

        // Group by Session
        val bySession = decidedOutcomes.groupBy { it.sessionName.ifBlank { "OPEN" } }.map { (sessionName, list) ->
            computeGroupStats(sessionName, list)
        }.sortedByDescending { it.expectancyR }

        // Group by Asset Type
        val byAsset = decidedOutcomes.groupBy { it.assetType.ifBlank { "CRYPTO" } }.map { (assetName, list) ->
            computeGroupStats(assetName, list)
        }.sortedByDescending { it.expectancyR }

        return PaperLedgerSummary(
            totalTrades = totalDecided,
            realTradesCount = realCount,
            paperTradesCount = paperCount,
            winsCount = wins,
            lossesCount = losses,
            overallWinRatePercent = winRate,
            overallExpectancyR = expectancyR,
            overallRealizedR = totalR,
            maxDrawdownR = maxDd,
            statsByEntryModel = byModel,
            statsBySession = bySession,
            statsByAssetType = byAsset
        )
    }

    private fun computeGroupStats(label: String, list: List<TradeOutcomeEntity>): LedgerGroupStats {
        val count = list.size
        val wins = list.count { it.realizedR > 0.0 }
        val winRate = if (count > 0) (wins.toDouble() / count) * 100.0 else 0.0
        val sumR = list.sumOf { it.realizedR }
        val expR = if (count > 0) sumR / count else 0.0

        var peak = 0.0
        var cum = 0.0
        var ddMax = 0.0
        list.sortedBy { it.createdAt }.forEach { item ->
            cum += item.realizedR
            if (cum > peak) peak = cum
            val dd = peak - cum
            if (dd > ddMax) ddMax = dd
        }

        return LedgerGroupStats(
            groupLabel = label,
            totalTrades = count,
            winRatePercent = winRate,
            expectancyR = expR,
            totalRealizedR = sumR,
            maxDrawdownR = ddMax
        )
    }

    /**
     * Feeds decided paper and real trade outcomes back into the Calibration Agent store.
     */
    fun feedOutcomesToCalibrationEngine(outcomes: List<TradeOutcomeEntity>) {
        outcomes.filter { it.filled && it.closedAt > 0 }.forEach { outcome ->
            val model = when {
                outcome.entryModel.contains("SWEEP", ignoreCase = true) -> EntryModel.B_SWEEP_RECLAIM
                outcome.entryModel.contains("BREAKOUT", ignoreCase = true) -> EntryModel.C_BREAKOUT_RETEST
                outcome.entryModel.contains("RANGE", ignoreCase = true) -> EntryModel.D_RANGE_FADE
                else -> EntryModel.A_TREND_PULLBACK
            }
            val session = when {
                outcome.sessionName.contains("EXTENDED", ignoreCase = true) -> MarketSessionStatus.EXTENDED
                outcome.sessionName.contains("OFF", ignoreCase = true) -> MarketSessionStatus.OFF_HOURS
                outcome.sessionName.contains("WEEKEND", ignoreCase = true) -> MarketSessionStatus.WEEKEND
                else -> MarketSessionStatus.OPEN
            }
            SignalCalibrationEngine.recordOutcome(
                symbol = outcome.symbol,
                model = model,
                regime = MarketRegime.TREND,
                isWithTrend = true,
                sessionStatus = session,
                isWin = outcome.realizedR > 0.0,
                realizedR = outcome.realizedR
            )
        }
    }
}
