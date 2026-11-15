package com.example.data.cache

import android.content.Context
import com.example.data.api.MarketDataService
import com.example.data.db.AppDatabase
import com.example.data.db.ArmedPlanEntity
import com.example.data.db.CachedCandleDao
import com.example.data.db.CachedCandleEntity
import com.example.data.db.MasterVerdictDao
import com.example.data.db.MasterVerdictEntity
import com.example.data.db.PlanOutcomeHistoryDao
import com.example.data.db.PlanOutcomeHistoryEntity
import com.example.domain.daytrader.replay.ReplayResult
import com.example.domain.engine.SignalCalibrationEngine
import com.example.domain.master.MasterVerdict
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class CachedCandleBatch(
    val candles: List<CandlePoint>,
    val dataAgeMs: Long,
    val isStale: Boolean,
    val fetchedNewBarsCount: Int,
    val requestedStartTimeMs: Long?
)

data class ReplayCacheKey(
    val planHash: String,
    val seed: Long
)

class MarketCacheRepository(
    private val database: AppDatabase,
    private val apiService: MarketDataService,
    private val dbFile: File? = null
) {
    constructor(context: Context, apiService: MarketDataService = MarketDataService()) : this(
        database = AppDatabase.getDatabase(context),
        apiService = apiService,
        dbFile = context.getDatabasePath("trading_agents_database")
    )

    private val candleDao: CachedCandleDao = database.cachedCandleDao()
    private val verdictDao: MasterVerdictDao = database.masterVerdictDao()
    private val armedPlanDao = database.armedPlanDao()
    private val outcomeHistoryDao: PlanOutcomeHistoryDao = database.planOutcomeHistoryDao()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val verdictAdapter = moshi.adapter(MasterVerdict::class.java)

    // Cost control §4: Replay 15-minute cache keyed by (planHash, seed)
    private val replayCache = ConcurrentHashMap<ReplayCacheKey, Pair<Long, ReplayResult>>()
    private val replayCacheTtlMs = 15L * 60_000L

    companion object {
        const val DB_SIZE_CAP_BYTES = 50L * 1024L * 1024L
        const val APPROX_MAX_CANDLE_ROWS = 320_000 // ~50MB including indices
        const val MAX_BARS_PER_BUCKET = 1200
        const val SEVEN_DAYS_MS = 7L * 86_400_000L
    }

    suspend fun getClosedCandlesIncremental(
        symbol: String,
        assetType: AssetType,
        marketType: TradingMarketType,
        timeframe: Timeframe,
        minBars: Int = 1000,
        forceRefresh: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
        remoteFetcher: (suspend (sym: String, tf: Timeframe, startTimeMs: Long?, isFutures: Boolean, limit: Int) -> List<CandlePoint>)? = null
    ): CachedCandleBatch {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        val marketKey = marketType.name
        val tfKey = timeframe.name
        val isFutures = marketType == TradingMarketType.FUTURES

        val cachedEntities = candleDao.getClosedCandles(cleanSym, marketKey, tfKey)
        if (cachedEntities.isNotEmpty()) {
            candleDao.touchCandles(cleanSym, marketKey, tfKey, nowMs)
        }
        val cachedCandles = cachedEntities.map { it.toCandlePoint() }
        val lastCloseTs = candleDao.getLastClosedTime(cleanSym, marketKey, tfKey)
            ?: cachedCandles.maxOfOrNull { it.effectiveCloseTimestamp(effectiveStepMs(timeframe)) }

        val effStepMs = effectiveStepMs(timeframe)
        val ageBeforeFetch = if (lastCloseTs != null) (nowMs - lastCloseTs).coerceAtLeast(0L) else Long.MAX_VALUE
        val needsIncremental = forceRefresh ||
            cachedCandles.size < minOf(100, minBars) ||
            lastCloseTs == null ||
            ageBeforeFetch >= effStepMs

        var requestedStart: Long? = null
        var newBarsCount = 0

        if (needsIncremental) {
            requestedStart = if (lastCloseTs != null && cachedCandles.size >= 100 && !forceRefresh) {
                lastCloseTs + 1L
            } else {
                null
            }
            val limit = minBars.coerceIn(100, 1000)
            val fetchedRaw = if (remoteFetcher != null) {
                remoteFetcher(cleanSym, timeframe, requestedStart, isFutures, limit)
            } else if (assetType == AssetType.CRYPTO) {
                var cryptoBars = apiService.fetchBinanceKlinesIncremental(
                    symbol = cleanSym,
                    timeframe = timeframe,
                    startTimeMs = requestedStart,
                    isFutures = isFutures,
                    limit = limit
                )
                // Aggregate 4h from 1h or 1W (FIVE_DAYS) from 1D when remote returns sparse history
                if (cryptoBars.size < 100 && timeframe == Timeframe.FOUR_HOURS && requestedStart == null) {
                    val h1 = apiService.fetchBinanceKlinesIncremental(cleanSym, Timeframe.ONE_HOUR, null, isFutures, 1000)
                    if (h1.isNotEmpty()) cryptoBars = apiService.aggregateCandles(h1, Timeframe.FOUR_HOURS.stepMillis)
                } else if (cryptoBars.size < 60 && timeframe == Timeframe.FIVE_DAYS && requestedStart == null) {
                    val d1 = apiService.fetchBinanceKlinesIncremental(cleanSym, Timeframe.ONE_DAY, null, isFutures, 1000)
                    if (d1.isNotEmpty()) cryptoBars = apiService.aggregateCandles(d1, SEVEN_DAYS_MS)
                }
                cryptoBars
            } else {
                val stockRes = apiService.fetchStockQuote(cleanSym, timeframe, cacheBust = forceRefresh)
                stockRes.getOrNull()?.candles.orEmpty().filter { it.isClosed }
            }

            val strictlyClosedNew = fetchedRaw.filter { bar ->
                val closeT = bar.effectiveCloseTimestamp(effStepMs)
                bar.isClosed && closeT <= nowMs && bar.open > 0.0 && bar.close > 0.0 && bar.high >= bar.low &&
                    (requestedStart == null || bar.timestamp >= requestedStart || closeT > (lastCloseTs ?: 0L))
            }

            if (strictlyClosedNew.isNotEmpty()) {
                val entities = strictlyClosedNew.map { bar ->
                    CachedCandleEntity(
                        symbol = cleanSym,
                        marketType = marketKey,
                        timeframe = tfKey,
                        openTime = bar.timestamp,
                        open = bar.open,
                        high = bar.high,
                        low = bar.low,
                        close = bar.close,
                        volume = bar.volume,
                        closeTime = bar.effectiveCloseTimestamp(effStepMs),
                        isClosed = true,
                        fetchedAt = nowMs,
                        lastAccessedAt = nowMs
                    )
                }
                candleDao.insertCandles(entities)
                newBarsCount = entities.size

                val allAfter = candleDao.getClosedCandles(cleanSym, marketKey, tfKey)
                if (allAfter.size > MAX_BARS_PER_BUCKET) {
                    val cutoff = allAfter[allAfter.size - MAX_BARS_PER_BUCKET].openTime
                    candleDao.pruneOlderThan(cleanSym, marketKey, tfKey, cutoff)
                }
                enforceDbSizeCap()
            }
        }

        val finalCandles = if (newBarsCount > 0) {
            candleDao.getClosedCandles(cleanSym, marketKey, tfKey).map { it.toCandlePoint() }
        } else {
            cachedCandles
        }
        val latestClose = finalCandles.maxOfOrNull { it.effectiveCloseTimestamp(effStepMs) } ?: 0L
        val finalAge = if (latestClose > 0L) (nowMs - latestClose).coerceAtLeast(0L) else Long.MAX_VALUE
        val staleLimit = (effStepMs * 2L) + 15_000L
        val isStale = finalCandles.isEmpty() || (effStepMs > 0L && finalAge > staleLimit)

        return CachedCandleBatch(
            candles = finalCandles,
            dataAgeMs = finalAge,
            isStale = isStale,
            fetchedNewBarsCount = newBarsCount,
            requestedStartTimeMs = requestedStart
        )
    }

    suspend fun appendClosedWebSocketCandle(
        symbol: String,
        marketType: TradingMarketType,
        timeframe: Timeframe,
        candle: CandlePoint,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (!candle.isClosed || candle.close <= 0.0 || candle.high < candle.low) return
        val effStep = effectiveStepMs(timeframe)
        candleDao.insertCandles(
            listOf(
                CachedCandleEntity(
                    symbol = symbol.trim().uppercase(Locale.US),
                    marketType = marketType.name,
                    timeframe = timeframe.name,
                    openTime = candle.timestamp,
                    open = candle.open,
                    high = candle.high,
                    low = candle.low,
                    close = candle.close,
                    volume = candle.volume,
                    closeTime = candle.effectiveCloseTimestamp(effStep),
                    isClosed = true,
                    fetchedAt = nowMs,
                    lastAccessedAt = nowMs
                )
            )
        )
        enforceDbSizeCap()
    }

    suspend fun enforceDbSizeCap(maxRows: Int = APPROX_MAX_CANDLE_ROWS) {
        val currentBytes = dbFile?.takeIf { it.exists() }?.length() ?: 0L
        val totalRows = candleDao.getTotalCandleCount()
        if (currentBytes > DB_SIZE_CAP_BYTES || totalRows > maxRows) {
            val excess = (totalRows - (maxRows * 8 / 10)).coerceAtLeast(2_000)
            candleDao.deleteOldestByLru(excess)
        }
    }

    fun getCachedReplay(planHash: String, seed: Long, nowMs: Long = System.currentTimeMillis()): ReplayResult? {
        val key = ReplayCacheKey(planHash, seed)
        val entry = replayCache[key] ?: return null
        return if ((nowMs - entry.first) < replayCacheTtlMs) entry.second else {
            replayCache.remove(key)
            null
        }
    }

    fun putCachedReplay(planHash: String, seed: Long, result: ReplayResult, nowMs: Long = System.currentTimeMillis()) {
        replayCache[ReplayCacheKey(planHash, seed)] = nowMs to result
    }

    suspend fun upsertMasterVerdict(
        verdict: MasterVerdict,
        statusFlag: String = if (verdict.isStale) "STALE" else "FRESH",
        errorSummary: String? = null
    ) {
        val cleanSym = verdict.symbol.trim().uppercase(Locale.US)
        val json = verdictAdapter.toJson(verdict)
        val best = verdict.bestPlan
        verdictDao.upsertVerdict(
            MasterVerdictEntity(
                symbol = cleanSym,
                marketType = verdict.marketType.name,
                verdictJson = json,
                headline = verdict.headline,
                primarySide = verdict.weightedBiasSide?.name ?: "NONE",
                primaryGrade = best?.grade?.name ?: "C_WATCH",
                alignmentPercent = verdict.alignmentPercent,
                statusFlag = statusFlag,
                errorSummary = errorSummary,
                seed = verdict.seed,
                computedAt = verdict.computedAtMs
            )
        )
    }

    suspend fun getCachedMasterVerdict(symbol: String, marketType: TradingMarketType): Pair<MasterVerdictEntity, MasterVerdict?>? {
        val entity = verdictDao.getVerdict(symbol.trim().uppercase(Locale.US), marketType.name) ?: return null
        val decoded = runCatching { verdictAdapter.fromJson(entity.verdictJson) }.getOrNull()
        return entity to decoded
    }

    fun observeAllMasterVerdicts(): Flow<List<MasterVerdictEntity>> = verdictDao.observeAllVerdicts()

    fun observeArmedPlans(): Flow<List<ArmedPlanEntity>> = armedPlanDao.observeArmedPlans()

    suspend fun getArmedPlans(): List<ArmedPlanEntity> = armedPlanDao.getAllArmedPlans()

    suspend fun upsertArmedPlan(plan: ArmedPlanEntity) = armedPlanDao.upsertArmedPlan(plan)

    suspend fun deleteArmedPlan(id: String) = armedPlanDao.deleteArmedPlan(id)

    fun observeOutcomeHistory(): Flow<List<PlanOutcomeHistoryEntity>> = outcomeHistoryDao.observeAllOutcomes()

    suspend fun recordPlanOutcomeAndCalibrate(outcome: PlanOutcomeHistoryEntity): Long {
        val id = outcomeHistoryDao.insertOutcome(outcome)
        val persisted = outcome.copy(id = id)
        SignalCalibrationEngine.syncHistoricalOutcomes(listOf(persisted))
        return id
    }

    suspend fun loadAndSyncCalibrationHistory() {
        val all = outcomeHistoryDao.getAllOutcomes()
        if (all.isNotEmpty()) {
            SignalCalibrationEngine.syncHistoricalOutcomes(all)
        }
    }

    private fun CachedCandleEntity.toCandlePoint(): CandlePoint = CandlePoint(
        timestamp = openTime,
        open = open,
        high = high,
        low = low,
        close = close,
        volume = volume,
        isClosed = isClosed,
        closeTimestamp = closeTime
    )

    private fun effectiveStepMs(tf: Timeframe): Long = when (tf) {
        Timeframe.FIVE_DAYS -> SEVEN_DAYS_MS
        else -> tf.stepMillis
    }
}
