package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ResearchReportDao {
    @Query("SELECT * FROM saved_research_reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<SavedResearchReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: SavedResearchReport): Long

    @Query("DELETE FROM saved_research_reports WHERE id = :id")
    suspend fun deleteReportById(id: Long)

    @Query("DELETE FROM saved_research_reports")
    suspend fun clearAll()
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist_items ORDER BY addedAt ASC")
    fun getAllWatchlist(): Flow<List<WatchlistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: WatchlistItem)

    @Query("DELETE FROM watchlist_items WHERE symbol = :symbol")
    suspend fun deleteItem(symbol: String)
}

@Dao
interface AiModelDao {
    @Query("SELECT * FROM ai_model_configs")
    fun getAllModels(): Flow<List<AiModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: AiModelEntity)

    @Query("DELETE FROM ai_model_configs WHERE id = :id")
    suspend fun deleteModel(id: String)

    @Query("UPDATE ai_model_configs SET requestsRouted = requestsRouted + 1, lastLatencyMs = :latencyMs, lastStatus = :status, tokensUsed = tokensUsed + :tokens, lastErrorMessage = :errorMsg WHERE id = :id")
    suspend fun recordRequest(id: String, latencyMs: Long, status: String, tokens: Long = 0L, errorMsg: String? = null)

    @Query("UPDATE ai_model_configs SET errorCount = errorCount + 1, lastErrorMessage = :errorMsg, lastStatus = :status WHERE id = :id")
    suspend fun recordError(id: String, status: String, errorMsg: String)

    @Query("UPDATE ai_model_configs SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: String, isEnabled: Boolean)
}

@Dao
interface CryptoPairDao {
    @Query("SELECT * FROM crypto_pairs ORDER BY lastScannedAt DESC")
    fun getAllPairs(): Flow<List<CryptoPair>>

    @Query("SELECT * FROM crypto_pairs WHERE marketType = :marketType ORDER BY volume24hUsd DESC")
    fun getPairsByMarketType(marketType: String): Flow<List<CryptoPair>>

    @Query("SELECT * FROM crypto_pairs WHERE symbol = :symbol LIMIT 1")
    fun getPairBySymbol(symbol: String): Flow<CryptoPair?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPairs(pairs: List<CryptoPair>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPair(pair: CryptoPair)

    @Query("DELETE FROM crypto_pairs WHERE lastScannedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM crypto_pairs")
    suspend fun clearAllPairs()
}

@Dao
interface TradingSignalDao {
    @Query("SELECT * FROM trading_signals ORDER BY timestamp DESC")
    fun getAllSignals(): Flow<List<TradingSignal>>

    @Query("SELECT * FROM trading_signals WHERE marketType = :marketType ORDER BY timestamp DESC")
    fun getSignalsByMarketType(marketType: String): Flow<List<TradingSignal>>

    @Query("SELECT * FROM trading_signals WHERE pairSymbol = :symbol ORDER BY timestamp DESC")
    fun getSignalsForPair(symbol: String): Flow<List<TradingSignal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: TradingSignal): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignals(signals: List<TradingSignal>)

    @Query("DELETE FROM trading_signals WHERE id = :id")
    suspend fun deleteSignal(id: Long)

    @Query("DELETE FROM trading_signals")
    suspend fun clearAllSignals()
}

@Dao
interface TradeOutcomeDao {
    @Query("SELECT * FROM trade_outcomes ORDER BY createdAt DESC")
    fun getAllOutcomes(): Flow<List<TradeOutcomeEntity>>

    @Query("SELECT * FROM trade_outcomes WHERE status NOT IN ('TP3_HIT', 'STOPPED_OUT', 'EXPIRED') ORDER BY createdAt ASC")
    fun getActiveTrades(): Flow<List<TradeOutcomeEntity>>

    @Query("SELECT * FROM trade_outcomes WHERE symbol = :symbol ORDER BY createdAt DESC")
    fun getOutcomesForSymbol(symbol: String): Flow<List<TradeOutcomeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutcome(outcome: TradeOutcomeEntity): Long

    @Query("UPDATE trade_outcomes SET status = :status, filled = :filled, fillPrice = :fillPrice, exitPrice = :exitPrice, realizedR = :realizedR, maxFavorableExcursionR = :mfe, maxAdverseExcursionR = :mae, closedAt = :closedAt WHERE id = :id")
    suspend fun updateOutcome(
        id: Long,
        status: String,
        filled: Boolean,
        fillPrice: Double,
        exitPrice: Double,
        realizedR: Double,
        mfe: Double,
        mae: Double,
        closedAt: Long
    )

    @Query("DELETE FROM trade_outcomes WHERE id = :id")
    suspend fun deleteOutcome(id: Long)

    @Query("DELETE FROM trade_outcomes")
    suspend fun clearAll()
}

@Dao
interface AiCompanyDao {
    @Query("SELECT * FROM ai_company_configs ORDER BY companyId ASC")
    fun getAllCompanies(): Flow<List<AiCompanyEntity>>

    @Query("SELECT * FROM ai_company_configs WHERE companyId = :companyId LIMIT 1")
    suspend fun getCompany(companyId: String): AiCompanyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompany(company: AiCompanyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanies(companies: List<AiCompanyEntity>)

    @Query("UPDATE ai_company_configs SET apiKey = :apiKey, status = :status, statusDetail = :detail, lastErrorMessage = :errorMsg WHERE companyId = :companyId")
    suspend fun updateApiKey(companyId: String, apiKey: String, status: String, detail: String? = null, errorMsg: String? = null)

    @Query("UPDATE ai_company_configs SET status = :status, statusDetail = :detail, lastErrorMessage = :errorMsg WHERE companyId = :companyId")
    suspend fun updateStatus(companyId: String, status: String, detail: String? = null, errorMsg: String? = null)

    @Query("UPDATE ai_company_configs SET status = :status, statusDetail = :detail, quotaResetTimeMs = :resetTimeMs WHERE companyId = :companyId")
    suspend fun setQuotaUsed(companyId: String, status: String, detail: String?, resetTimeMs: Long)

    @Query("UPDATE ai_company_configs SET pinnedModelId = :modelId WHERE companyId = :companyId")
    suspend fun updatePinnedModel(companyId: String, modelId: String?)

    @Query("UPDATE ai_company_configs SET customEndpoint = :endpoint, customAuthHeader = :authHeader WHERE companyId = :companyId")
    suspend fun updateCustomConfig(companyId: String, endpoint: String?, authHeader: String?)

    @Query("UPDATE ai_company_configs SET callsToday = callsToday + 1 WHERE companyId = :companyId")
    suspend fun incrementCallsToday(companyId: String)

    @Query("UPDATE ai_company_configs SET callsToday = 0, lastCallsResetDay = :today WHERE companyId = :companyId")
    suspend fun resetCallsToday(companyId: String, today: Long)

    @Query("UPDATE ai_company_configs SET lastDiscoveryTimeMs = :timeMs WHERE companyId = :companyId")
    suspend fun updateLastDiscoveryTime(companyId: String, timeMs: Long)

    // Discovered models queries
    @Query("SELECT * FROM discovered_models WHERE companyId = :companyId ORDER BY tier ASC, latencyMs ASC")
    fun getDiscoveredModelsForCompany(companyId: String): Flow<List<DiscoveredModelEntity>>

    @Query("SELECT * FROM discovered_models ORDER BY tier ASC, latencyMs ASC")
    fun getAllDiscoveredModels(): Flow<List<DiscoveredModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiscoveredModels(models: List<DiscoveredModelEntity>)

    @Query("DELETE FROM discovered_models WHERE companyId = :companyId")
    suspend fun deleteDiscoveredModelsForCompany(companyId: String)

    @Query("UPDATE discovered_models SET isUsable = :isUsable, errorReason = :errorReason WHERE id = :id")
    suspend fun setModelUsable(id: String, isUsable: Boolean, errorReason: String? = null)

    @Query("UPDATE discovered_models SET callsToday = callsToday + 1 WHERE id = :id")
    suspend fun incrementModelCallsToday(id: String)
}

@Dao
interface CachedCandleDao {
    @Query("SELECT * FROM cached_candles WHERE symbol = :symbol AND marketType = :marketType AND timeframe = :timeframe AND isClosed = 1 ORDER BY openTime ASC")
    suspend fun getClosedCandles(symbol: String, marketType: String, timeframe: String): List<CachedCandleEntity>

    @Query("SELECT MAX(closeTime) FROM cached_candles WHERE symbol = :symbol AND marketType = :marketType AND timeframe = :timeframe AND isClosed = 1")
    suspend fun getLastClosedTime(symbol: String, marketType: String, timeframe: String): Long?

    @Query("SELECT COUNT(*) FROM cached_candles WHERE symbol = :symbol AND marketType = :marketType AND timeframe = :timeframe AND isClosed = 1")
    suspend fun getClosedCandleCount(symbol: String, marketType: String, timeframe: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCandles(candles: List<CachedCandleEntity>)

    @Query("UPDATE cached_candles SET lastAccessedAt = :accessedAt WHERE symbol = :symbol AND marketType = :marketType AND timeframe = :timeframe")
    suspend fun touchCandles(symbol: String, marketType: String, timeframe: String, accessedAt: Long)

    @Query("SELECT COUNT(*) FROM cached_candles")
    suspend fun getTotalCandleCount(): Int

    @Query("DELETE FROM cached_candles WHERE rowid IN (SELECT rowid FROM cached_candles ORDER BY lastAccessedAt ASC LIMIT :limit)")
    suspend fun deleteOldestByLru(limit: Int)

    @Query("DELETE FROM cached_candles WHERE symbol = :symbol AND marketType = :marketType AND timeframe = :timeframe AND openTime < :cutoffOpenTime")
    suspend fun pruneOlderThan(symbol: String, marketType: String, timeframe: String, cutoffOpenTime: Long)
}

@Dao
interface MasterVerdictDao {
    @Query("SELECT * FROM cached_master_verdicts ORDER BY computedAt DESC")
    fun observeAllVerdicts(): Flow<List<MasterVerdictEntity>>

    @Query("SELECT * FROM cached_master_verdicts WHERE symbol = :symbol AND marketType = :marketType LIMIT 1")
    suspend fun getVerdict(symbol: String, marketType: String): MasterVerdictEntity?

    @Query("SELECT * FROM cached_master_verdicts WHERE symbol = :symbol ORDER BY computedAt DESC LIMIT 1")
    suspend fun getLatestVerdictForSymbol(symbol: String): MasterVerdictEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVerdict(entity: MasterVerdictEntity)
}

@Dao
interface ArmedPlanDao {
    @Query("SELECT * FROM armed_plans ORDER BY createdAt DESC")
    fun observeArmedPlans(): Flow<List<ArmedPlanEntity>>

    @Query("SELECT * FROM armed_plans ORDER BY createdAt DESC")
    suspend fun getAllArmedPlans(): List<ArmedPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArmedPlan(plan: ArmedPlanEntity)

    @Query("DELETE FROM armed_plans WHERE id = :id")
    suspend fun deleteArmedPlan(id: String)
}

@Dao
interface PlanOutcomeHistoryDao {
    @Query("SELECT * FROM plan_outcome_history ORDER BY resolvedAt DESC")
    fun observeAllOutcomes(): Flow<List<PlanOutcomeHistoryEntity>>

    @Query("SELECT * FROM plan_outcome_history ORDER BY resolvedAt ASC")
    suspend fun getAllOutcomes(): List<PlanOutcomeHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutcome(outcome: PlanOutcomeHistoryEntity): Long
}


