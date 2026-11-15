package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_research_reports")
data class SavedResearchReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val companyName: String,
    val assetType: String,
    val price: Double,
    val changePercent: Double,
    val action: String,
    val confidenceScore: Int,
    val targetPrice: Double,
    val stopLossPrice: Double,
    val executiveSummary: String,
    val riskLevel: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "watchlist_items")
data class WatchlistItem(
    @PrimaryKey val symbol: String,
    val name: String,
    val assetType: String,
    val defaultPrice: Double = 0.0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_model_configs")
data class AiModelEntity(
    @PrimaryKey val id: String,
    val provider: String,
    val modelName: String,
    val apiKey: String,
    val assignedRole: String?, // null = round robin pool
    val isEnabled: Boolean = true,
    val requestsRouted: Int = 0,
    val lastLatencyMs: Long = 0,
    val lastStatus: String = "Ready",
    val customEndpoint: String? = null,
    val customDisplayName: String? = null,
    val customAuthHeader: String? = null,
    val tokensUsed: Long = 0L,
    val errorCount: Int = 0,
    val lastErrorMessage: String? = null
)

@Entity(tableName = "ai_company_configs")
data class AiCompanyEntity(
    @PrimaryKey val companyId: String,
    val displayName: String,
    val apiKey: String, // encrypted via KeyEncryptionManager
    val status: String = "NO_KEY",
    val statusDetail: String? = null,
    val quotaResetTimeMs: Long = 0L,
    val lastDiscoveryTimeMs: Long = 0L,
    val pinnedModelId: String? = null,
    val customEndpoint: String? = null,
    val customAuthHeader: String? = null,
    val callsToday: Int = 0,
    val lastCallsResetDay: Long = 0L,
    val lastErrorMessage: String? = null
)

@Entity(tableName = "discovered_models")
data class DiscoveredModelEntity(
    @PrimaryKey val id: String, // e.g. "gemini:gemini-2.5-flash"
    val companyId: String,
    val modelName: String,
    val tier: String, // "TOP", "STANDARD", "FAST"
    val isUsable: Boolean = true,
    val isFree: Boolean = false,
    val latencyMs: Long = 0L,
    val contextWindow: Int = 0,
    val callsToday: Int = 0,
    val lastTestedMs: Long = 0L,
    val errorReason: String? = null
)

@Entity(tableName = "crypto_pairs")
data class CryptoPair(
    @PrimaryKey val symbol: String,
    val name: String,
    val baseAsset: String,
    val quoteAsset: String,
    val marketType: String, // "SPOT" or "FUTURES"
    val price: Double,
    val change24hPercent: Double,
    val high24h: Double,
    val low24h: Double,
    val volume24hUsd: Double,
    val rsi14: Double,
    val macdSignal: Double,
    val fundingRatePercent: Double,
    val openInterestUsd: Double,
    val longRatioPercent: Double,
    val shortRatioPercent: Double,
    val primaryVerdict: String,
    val isBullish: Boolean,
    val lastScannedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "trading_signals")
data class TradingSignal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pairSymbol: String,
    val marketType: String,
    val signalType: String, // e.g. "BULLISH_BREAKOUT", "OVERSOLD_REVERSAL", "FUNDING_SQUEEZE"
    val verdict: String,    // "STRONG BUY", "BUY", "SELL", "HOLD"
    val confidenceScore: Int,
    val entryPrice: Double,
    val targetPrice: Double,
    val stopLossPrice: Double,
    val riskRewardRatio: String,
    val timeframe: String,
    val reasoning: String,
    val generatedBy: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "trade_outcomes")
data class TradeOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val side: String,
    val timeframe: String,
    val entryPrice: Double,
    val stopLoss: Double,
    val takeProfit1: Double,
    val takeProfit2: Double = 0.0,
    val takeProfit3: Double = 0.0,
    val confidenceScore: Int = 50,
    val setupType: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING_ENTRY",
    val filled: Boolean = false,
    val fillPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val realizedR: Double = 0.0,
    val maxFavorableExcursionR: Double = 0.0,
    val maxAdverseExcursionR: Double = 0.0,
    val closedAt: Long = 0L,
    val isRealTrade: Boolean = false,
    val sessionName: String = "OPEN",
    val assetType: String = "CRYPTO",
    val entryModel: String = "A_TREND_PULLBACK",
    val exitReason: String = ""
)

@Entity(
    tableName = "cached_candles",
    primaryKeys = ["symbol", "marketType", "timeframe", "openTime"]
)
data class CachedCandleEntity(
    val symbol: String,
    val marketType: String,
    val timeframe: String,
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long,
    val isClosed: Boolean,
    val fetchedAt: Long,
    val lastAccessedAt: Long
)

@Entity(
    tableName = "cached_master_verdicts",
    primaryKeys = ["symbol", "marketType"]
)
data class MasterVerdictEntity(
    val symbol: String,
    val marketType: String,
    val verdictJson: String,
    val headline: String,
    val primarySide: String,
    val primaryGrade: String,
    val alignmentPercent: Int,
    val statusFlag: String, // "FRESH", "STALE", "FAILED"
    val errorSummary: String? = null,
    val seed: Long,
    val computedAt: Long
)

@Entity(tableName = "armed_plans")
data class ArmedPlanEntity(
    @PrimaryKey val id: String, // "${symbol}_${marketType}_${horizon}"
    val symbol: String,
    val marketType: String,
    val horizon: String,
    val side: String,
    val mode: String, // "ARMED_ALERT" or "PAPER_FOLLOW"
    val entryZoneMin: Double,
    val entryZoneMax: Double,
    val triggerPrice: Double,
    val triggerCondition: String,
    val stopLoss: Double,
    val tp1: Double,
    val tp2: Double,
    val tp3: Double,
    val entryModel: String,
    val grade: String,
    val validUntilMs: Long,
    val isTriggered: Boolean = false,
    val isEnteredZone: Boolean = false,
    val lastEvaluatedBarTs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "plan_outcome_history")
data class PlanOutcomeHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val marketType: String,
    val horizon: String,
    val entryModel: String,
    val regime: String,
    val isWithTrend: Boolean,
    val sessionStatus: String,
    val outcomeType: String, // "TP1", "TP2", "TP3", "SL", "EXPIRED"
    val isWin: Boolean,
    val realizedR: Double,
    val resolvedAt: Long = System.currentTimeMillis()
)


