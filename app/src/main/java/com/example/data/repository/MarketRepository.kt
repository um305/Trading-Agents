package com.example.data.repository

import android.content.Context
import com.example.data.api.MarketDataService
import com.example.data.db.AiCompanyEntity
import com.example.data.db.DiscoveredModelEntity
import com.example.data.db.AppDatabase
import com.example.data.db.CryptoPair
import com.example.data.db.SavedResearchReport
import com.example.data.db.TradingSignal
import com.example.data.db.WatchlistItem
import com.example.domain.agents.TradingAgentsEngine
import com.example.domain.model.ApiSourceInfo
import com.example.domain.model.AssetType
import com.example.domain.model.CryptoScanItem
import com.example.domain.model.MarketQuote
import com.example.domain.model.MultiAgentSynthesis
import com.example.domain.model.NewsItem
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingAction
import com.example.domain.model.TradingMarketType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import com.example.domain.model.AiCompanyConfig
import com.example.domain.model.CompanyDefinition
import com.example.domain.model.CompanyStatus
import com.example.domain.model.DiscoveredModel
import com.example.domain.model.ModelTier
import com.example.domain.model.SupportedCompanies
import com.example.domain.ai.CompanyDiscoveryEngine
import com.example.domain.ai.AiFleetRouter
import com.example.domain.ai.DiscoveryResult

private data class SetupTuple(val entry: Double, val start: Double, val end: Double, val sl: Double)

data class BarFeedDiagnostic(
    val symbol: String,
    val timeframeLabel: String,
    val barsLoaded: Int,
    val candleAgeMillis: Long,
    val updatedAtMillis: Long
)

data class DiagnosticsSnapshot(
    val noTradeReasonCounts: Map<String, Int> = emptyMap(),
    val totalEvaluatedCount: Int = 0,
    val noTradeCount: Int = 0,
    val replayBlockCount: Int = 0,
    val sizerBlockCount: Int = 0,
    val barFeeds: List<BarFeedDiagnostic> = emptyList(),
    val recentApiErrors: List<String> = emptyList()
)

class MarketRepository(context: Context) {

    private val apiService = MarketDataService()
    private val database = AppDatabase.getDatabase(context)
    private val reportDao = database.researchReportDao()
    private val watchlistDao = database.watchlistDao()
    private val aiModelDao = database.aiModelDao()
    private val aiCompanyDao = database.aiCompanyDao()
    private val cryptoPairDao = database.cryptoPairDao()
    private val tradingSignalDao = database.tradingSignalDao()
    private val tradeOutcomeDao = database.tradeOutcomeDao()
    val cacheRepository = com.example.data.cache.MarketCacheRepository(context, apiService)
    val discoveryEngine = CompanyDiscoveryEngine(aiCompanyDao = aiCompanyDao)
    val fleetRouter = AiFleetRouter(aiCompanyDao = aiCompanyDao)
    val aiDispatcher = com.example.domain.ai.MultiAiDispatcher(aiModelDao)
    val newsScannerAgent = com.example.domain.agents.NewsScannerAgent(apiService)
    fun getOkHttpClient(): okhttp3.OkHttpClient = apiService.getOkHttpClient()

    private val sessionPoolCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, com.example.domain.daytrader.replay.SessionPool>>()
    private val sessionPoolCacheTtlMs = 6L * 3600_000L
    private val quoteCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, MarketQuote>>()
    private val quoteCacheTtlMs = 30_000L
    private val secFundamentalsCache = java.util.concurrent.ConcurrentHashMap<String, com.example.domain.daytrader.fundamentals.StockFundamentalsResult>()
    private val secCacheTtlMs = 24L * 3600_000L

    private val verdictHistoryLock = Any()
    private val verdictHistory = ArrayDeque<Pair<Long, String?>>(200)
    private val replayBlockCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private val sizerBlockCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private val barsDiagnostics = java.util.concurrent.ConcurrentHashMap<String, BarFeedDiagnostic>()

    fun recordVerdictEvaluation(verdict: com.example.domain.model.SignalVerdictState, vetoes: List<String>) {
        val reason = if (verdict == com.example.domain.model.SignalVerdictState.NO_TRADE) {
            vetoes.firstOrNull()?.substringBefore(":")?.trim()?.ifBlank { "Other NO_TRADE" } ?: "Other NO_TRADE"
        } else {
            null
        }
        synchronized(verdictHistoryLock) {
            if (verdictHistory.size >= 200) verdictHistory.removeFirst()
            verdictHistory.addLast(System.currentTimeMillis() to reason)
        }
    }

    fun recordReplayRefusal() {
        replayBlockCounter.incrementAndGet()
    }

    fun recordSizerBlock() {
        sizerBlockCounter.incrementAndGet()
    }

    fun getDiagnosticsSnapshot(): DiagnosticsSnapshot {
        val counts = linkedMapOf<String, Int>()
        var total = 0
        var noTrades = 0
        synchronized(verdictHistoryLock) {
            total = verdictHistory.size
            for ((_, reason) in verdictHistory) {
                if (reason != null) {
                    noTrades++
                    counts[reason] = (counts[reason] ?: 0) + 1
                }
            }
        }
        return DiagnosticsSnapshot(
            noTradeReasonCounts = counts.entries.sortedByDescending { it.value }.associate { it.key to it.value },
            totalEvaluatedCount = total,
            noTradeCount = noTrades,
            replayBlockCount = replayBlockCounter.get(),
            sizerBlockCount = sizerBlockCounter.get(),
            barFeeds = barsDiagnostics.values.sortedByDescending { it.updatedAtMillis }.take(15),
            recentApiErrors = apiService.getRecentApiErrorsRedacted()
        )
    }

    // Configured Free APIs list
    val apiSources = mutableListOf(
        ApiSourceInfo(
            id = "yahoo_finance",
            name = "Yahoo Finance Public API",
            description = "Zero-key free public endpoint for real-time market quotes, OHLCV candles, 52-week ranges & market volume.",
            endpointUrl = "https://query1.finance.yahoo.com/v8/finance/chart/AAPL",
            isFree = true,
            requiresApiKey = false,
            status = "Active",
            dataPointsProvided = "Real-time Quotes, 1D/5D/1M/1Y Candles, Volume"
        ),
        ApiSourceInfo(
            id = "coingecko",
            name = "CoinGecko Free API",
            description = "Zero-key free public crypto market data endpoint for BTC, ETH, SOL and top 100+ digital assets.",
            endpointUrl = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd",
            isFree = true,
            requiresApiKey = false,
            status = "Active",
            dataPointsProvided = "Crypto Prices, 24h Vol, Market Cap, History"
        ),
        ApiSourceInfo(
            id = "google_news_rss",
            name = "Google & Yahoo Finance RSS",
            description = "Free live financial journalism feed returning real-time news headlines, publication dates, and media sources.",
            endpointUrl = "https://news.google.com/rss/search?q=market+stock",
            isFree = true,
            requiresApiKey = false,
            status = "Active",
            dataPointsProvided = "Headlines, Sentiment Polarity, Timestamps"
        ),
        ApiSourceInfo(
            id = "finnhub_free",
            name = "Finnhub Free Tier API",
            description = "Used by Tauric TradingAgents framework for analyst consensus, price targets and SEC filings.",
            endpointUrl = "https://finnhub.io/api/v1/quote?symbol=AAPL&token=sandbox_token",
            isFree = true,
            requiresApiKey = true,
            status = "Configurable",
            dataPointsProvided = "Analyst Recommendations, Real-time Quotes"
        ),
        ApiSourceInfo(
            id = "alphavantage_free",
            name = "Alpha Vantage Free Tier API",
            description = "Free tier API for historical equity metrics, fundamental overviews and technical indicator signals.",
            endpointUrl = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=IBM&apikey=demo",
            isFree = true,
            requiresApiKey = true,
            status = "Configurable",
            dataPointsProvided = "Global Quote, Technical Studies, Earnings"
        ),
        ApiSourceInfo(
            id = "bloomberg_wire",
            name = "Bloomberg Financial Wire & Tape",
            description = "Institutional macro tape, bond yields, earnings intelligence & Bloomberg markets terminal headlines.",
            endpointUrl = "https://news.google.com/rss/search?q=site:bloomberg.com+markets",
            isFree = true,
            requiresApiKey = false,
            status = "Active",
            dataPointsProvided = "Bloomberg Intelligence, Fixed Income, Commodities & Equities Tape"
        ),
        ApiSourceInfo(
            id = "binance_futures",
            name = "Binance Futures & Klines API",
            description = "Zero-key high-frequency market data: 1m, 5m, 15m, 30m, 1h, 4h klines, funding rates and open interest.",
            endpointUrl = "https://fapi.binance.com/fapi/v1/premiumIndex?symbol=BTCUSDT",
            isFree = true,
            requiresApiKey = false,
            status = "Active",
            dataPointsProvided = "1m-4h Candles, Funding Rates, Open Interest, Liquidations"
        ),
        ApiSourceInfo(
            id = "gemini_ai",
            name = "Gemini AI Multi-Agent Core",
            description = "Google DeepMind's LLM engine orchestrating agent reasoning, inter-agent debate and research synthesis.",
            endpointUrl = "https://generativelanguage.googleapis.com",
            isFree = true,
            requiresApiKey = true,
            status = "Active",
            dataPointsProvided = "Agent Reasoning, Multi-Agent Debate, Synthesis"
        )
    )

    suspend fun getQuote(
        symbol: String,
        timeframe: Timeframe = Timeframe.ONE_MONTH,
        marketType: com.example.domain.model.TradingMarketType = com.example.domain.model.TradingMarketType.SPOT,
        exchange: com.example.domain.model.CryptoExchange = com.example.domain.model.CryptoExchange.BINANCE,
        explicitAssetType: AssetType? = null
    ): Result<MarketQuote> {
        val clean = symbol.trim().uppercase()
        val cacheKey = "${clean}_${marketType.name}_${timeframe.name}_${explicitAssetType?.name ?: "AUTO"}_${exchange.name}"
        val now = System.currentTimeMillis()
        val isSetupTf = timeframe !in listOf(
            Timeframe.FIVE_DAYS,
            Timeframe.ONE_MONTH,
            Timeframe.THREE_MONTHS,
            Timeframe.ONE_YEAR
        )

        quoteCache[cacheKey]?.let { (cachedAt, cachedQuote) ->
            val fresh = (now - cachedAt) < quoteCacheTtlMs
            val enoughBars = !isSetupTf || cachedQuote.barsLoadedCount >= 100
            if (fresh && enoughBars && !cachedQuote.isStale) {
                return Result.success(cachedQuote)
            }
        }

        val isMegaCapStock = clean in listOf("AAPL", "TSLA", "NVDA", "MSFT", "AMZN", "GOOGL", "META", "AMD", "NFLX", "SPY", "QQQ", "PLTR", "INTC", "DIS", "BA", "COIN", "SNDK")
        val isCrypto = explicitAssetType?.let { it == AssetType.CRYPTO } ?: (
            marketType == com.example.domain.model.TradingMarketType.FUTURES ||
            isCryptoSymbol(clean) ||
            (!isMegaCapStock && apiService.probeBinanceCrypto(clean))
        )

        suspend fun fetchQuoteOnce(): Result<MarketQuote> {
            return if (isCrypto) {
                val cryptoResult = apiService.fetchCryptoQuote(clean, timeframe, exchange, marketType)
                if (cryptoResult.isSuccess && cryptoResult.getOrNull()?.primarySource?.contains("Generator") != true) {
                    cryptoResult
                } else if (explicitAssetType == null && !clean.endsWith("USDT") && !clean.endsWith("-PERP")) {
                    val stockResult = apiService.fetchStockQuote(clean, timeframe)
                    if (stockResult.isSuccess && stockResult.getOrNull()?.primarySource?.contains("Generator") != true) {
                        stockResult
                    } else cryptoResult
                } else {
                    cryptoResult
                }
            } else {
                val stockResult = apiService.fetchStockQuote(clean, timeframe)
                if (stockResult.isFailure || stockResult.getOrNull()?.primarySource?.contains("Generator") == true) {
                    val cryptoProbe = apiService.fetchCryptoQuote(clean, timeframe, exchange, marketType)
                    if (cryptoProbe.isSuccess && cryptoProbe.getOrNull()?.primarySource?.contains("Generator") != true) {
                        cryptoProbe
                    } else {
                        stockResult
                    }
                } else {
                    stockResult
                }
            }
        }

        var rawQuoteResult = fetchQuoteOnce()
        var rawQuote = rawQuoteResult.getOrNull()

        // Extended-history retry: if bars < 100 or stale on setup timeframe, retry once before veto
        if (rawQuote != null && isSetupTf && (rawQuote.barsLoadedCount < 100 || rawQuote.isStale)) {
            val retryResult = fetchQuoteOnce()
            val retryQuote = retryResult.getOrNull()
            if (retryQuote != null && retryQuote.barsLoadedCount >= rawQuote.barsLoadedCount) {
                rawQuoteResult = retryResult
                rawQuote = retryQuote
            }
        }

        val resolvedQuote = rawQuote ?: run {
            apiService.recordApiError("Quote", "Failed to load $clean (${timeframe.label}): ${rawQuoteResult.exceptionOrNull()?.message ?: "unavailable"}")
            return rawQuoteResult
        }
        val resolvedAssetType = if (isCrypto) AssetType.CRYPTO else AssetType.STOCK

        val finalQuote = if (isCrypto || marketType == com.example.domain.model.TradingMarketType.FUTURES) {
            val futuresData = apiService.fetchCryptoFuturesData(clean, resolvedQuote.currentPrice)
            resolvedQuote.copy(
                assetType = resolvedAssetType,
                marketType = marketType,
                timeframe = timeframe,
                futuresData = futuresData
            )
        } else {
            resolvedQuote.copy(
                assetType = resolvedAssetType,
                timeframe = timeframe,
                marketType = marketType
            )
        }

        barsDiagnostics["${finalQuote.symbol.uppercase()}_${timeframe.label}_${marketType.name}"] = BarFeedDiagnostic(
            symbol = finalQuote.symbol.uppercase(),
            timeframeLabel = "${timeframe.label} (${marketType.name})",
            barsLoaded = maxOf(finalQuote.barsLoadedCount, finalQuote.candles.count { it.isClosed }),
            candleAgeMillis = finalQuote.candleAgeMillis,
            updatedAtMillis = System.currentTimeMillis()
        )

        if (!finalQuote.isStale && (!isSetupTf || finalQuote.barsLoadedCount >= 100)) {
            quoteCache[cacheKey] = now to finalQuote
        }

        return Result.success(finalQuote)
    }

    suspend fun hasFuturesPerpMarket(symbol: String, assetType: AssetType): Boolean {
        return apiService.hasBinanceFuturesPerp(symbol, assetType)
    }

    suspend fun fetchSecFundamentals(
        symbol: String,
        assetType: AssetType,
        secUserAgent: String
    ): com.example.domain.daytrader.fundamentals.StockFundamentalsResult {
        val cleanTicker = com.example.domain.daytrader.fundamentals.CompanyFactsEngine.normalizeEquityTickerForSec(symbol)
        if (assetType == AssetType.CRYPTO && cleanTicker == symbol.trim().uppercase()) {
            return com.example.domain.daytrader.fundamentals.StockFundamentalsResult(
                facts = null,
                unavailableReason = null
            )
        }
        val now = System.currentTimeMillis()
        secFundamentalsCache[cleanTicker]?.let { cached ->
            if (cached.facts != null && (now - cached.lastUpdatedMs) < secCacheTtlMs) {
                return cached
            }
        }
        val fetched = apiService.fetchSecCompanyFacts(symbol, secUserAgent)
        if (fetched.facts != null && fetched.facts.quarters.isNotEmpty()) {
            secFundamentalsCache[cleanTicker] = fetched
        }
        return fetched
    }

    suspend fun getQuotesForTriplet(
        symbol: String,
        marketType: TradingMarketType,
        exchange: com.example.domain.model.CryptoExchange,
        explicitAssetType: AssetType?
    ): Map<Timeframe, MarketQuote> {
        val candidateTfs = listOf(
            Timeframe.FIVE_MIN,
            Timeframe.FIFTEEN_MIN,
            Timeframe.ONE_HOUR,
            Timeframe.FOUR_HOURS,
            Timeframe.ONE_DAY
        )
        val out = linkedMapOf<Timeframe, MarketQuote>()
        for (tf in candidateTfs) {
            try {
                getQuote(symbol, tf, marketType, exchange, explicitAssetType).getOrNull()?.let {
                    if (it.candles.isNotEmpty() && !it.isStale) {
                        out[tf] = it
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    suspend fun scanCryptoMarkets(
        marketType: TradingMarketType,
        timeframe: Timeframe = Timeframe.FIVE_MIN
    ): List<CryptoScanItem> {
        return try {
            val liveItems = apiService.scanCryptoMarkets(marketType, timeframe)

            // Cache pairs to Room database
            val pairEntities = liveItems.map { item ->
                val base = item.symbol.replace("USDT", "").replace("-PERP", "")
                CryptoPair(
                    symbol = item.symbol,
                    name = item.name,
                    baseAsset = base,
                    quoteAsset = "USDT",
                    marketType = marketType.name,
                    price = item.price,
                    change24hPercent = item.change24h,
                    high24h = item.price * 1.05,
                    low24h = item.price * 0.95,
                    volume24hUsd = item.volume24hUsd,
                    rsi14 = item.rsi,
                    macdSignal = if (item.change24h > 0) 1.25 else -0.85,
                    fundingRatePercent = item.fundingRate ?: 0.0,
                    openInterestUsd = item.openInterestUsd ?: 0.0,
                    longRatioPercent = 52.0 + (item.change24h.coerceIn(-10.0, 10.0)),
                    shortRatioPercent = 100.0 - (52.0 + (item.change24h.coerceIn(-10.0, 10.0))),
                    primaryVerdict = item.agentVerdict.label,
                    isBullish = item.agentVerdict.isPositive,
                    lastScannedAt = System.currentTimeMillis()
                )
            }
            cryptoPairDao.insertPairs(pairEntities)

            // Extract & cache real-time algorithmic trading signals with explicit buy prices for offline review
            val signals = liveItems.mapNotNull { item ->
                val buyPrice = if (item.recommendedBuyPrice > 0.0) item.recommendedBuyPrice else item.price
                val targetPrice = if (item.targetSellPrice > 0.0) item.targetSellPrice else (item.price * 1.08)
                val stopPrice = if (item.stopLossPrice > 0.0) item.stopLossPrice else (item.price * 0.96)

                when {
                    item.rsi < 35.0 -> {
                        TradingSignal(
                            pairSymbol = item.symbol,
                            marketType = marketType.name,
                            signalType = "OVERSOLD_REVERSAL",
                            verdict = "BUY",
                            confidenceScore = 86,
                            entryPrice = buyPrice,
                            targetPrice = targetPrice,
                            stopLossPrice = stopPrice,
                            riskRewardRatio = "2.0:1",
                            timeframe = timeframe.label,
                            reasoning = "[${timeframe.label}] Buy at $${String.format(java.util.Locale.US, "%.2f", buyPrice)} (RSI: ${String.format(java.util.Locale.US, "%.1f", item.rsi)}) for mean-reversion bounce toward $${String.format(java.util.Locale.US, "%.2f", targetPrice)}.",
                            generatedBy = "Technical Analyst Agent"
                        )
                    }
                    (item.fundingRate ?: 0.0) > 0.015 -> {
                        TradingSignal(
                            pairSymbol = item.symbol,
                            marketType = marketType.name,
                            signalType = "PERP_FUNDING_SQUEEZE",
                            verdict = "HOLD",
                            confidenceScore = 78,
                            entryPrice = buyPrice,
                            targetPrice = targetPrice,
                            stopLossPrice = stopPrice,
                            riskRewardRatio = "1.8:1",
                            timeframe = timeframe.label,
                            reasoning = "[${timeframe.label}] Elevated perp funding rate (+${String.format(java.util.Locale.US, "%.4f", item.fundingRate)}%). Wait for dip near $${String.format(java.util.Locale.US, "%.2f", buyPrice)} before entering.",
                            generatedBy = "Risk Management Agent"
                        )
                    }
                    item.change24h > 3.0 -> {
                        TradingSignal(
                            pairSymbol = item.symbol,
                            marketType = marketType.name,
                            signalType = "BULLISH_BREAKOUT",
                            verdict = "STRONG BUY",
                            confidenceScore = 91,
                            entryPrice = buyPrice,
                            targetPrice = targetPrice,
                            stopLossPrice = stopPrice,
                            riskRewardRatio = "2.8:1",
                            timeframe = timeframe.label,
                            reasoning = "[${timeframe.label}] Strong momentum surge (+${String.format(java.util.Locale.US, "%.1f", item.change24h)}%). Recommended Buy Limit at $${String.format(java.util.Locale.US, "%.2f", buyPrice)} with target $${String.format(java.util.Locale.US, "%.2f", targetPrice)}.",
                            generatedBy = "Chief Strategist"
                        )
                    }
                    else -> null
                }
            }
            if (signals.isNotEmpty()) {
                tradingSignalDao.insertSignals(signals)
            }

            liveItems
        } catch (e: Exception) {
            // Fallback to locally cached Room database for offline analysis
            val cachedPairs = cryptoPairDao.getPairsByMarketType(marketType.name).first()
            if (cachedPairs.isNotEmpty()) {
                cachedPairs.map { pair ->
                    val buyPrice = if (pair.isBullish) pair.price * 0.995 else pair.price * 0.96
                    CryptoScanItem(
                        symbol = pair.symbol,
                        name = pair.name,
                        marketType = marketType,
                        price = pair.price,
                        change24h = pair.change24hPercent,
                        volume24hUsd = pair.volume24hUsd,
                        fundingRate = if (marketType == TradingMarketType.FUTURES) pair.fundingRatePercent else null,
                        openInterestUsd = if (marketType == TradingMarketType.FUTURES) pair.openInterestUsd else null,
                        rsi = pair.rsi14,
                        trendSignal = if (pair.isBullish) "[${timeframe.label}] Cached: Bullish" else "[${timeframe.label}] Cached: Neutral",
                        agentVerdict = if (pair.isBullish) TradingAction.BUY else TradingAction.HOLD,
                        recommendedBuyPrice = buyPrice,
                        targetSellPrice = pair.price * 1.08,
                        stopLossPrice = pair.price * 0.96
                    )
                }
            } else {
                emptyList()
            }
        }
    }

    suspend fun getBloombergNews(query: String = "markets"): List<NewsItem> {
        return apiService.fetchBloombergNews(query)
    }

    suspend fun fetchRealExchangeQuotes(
        symbol: String,
        assetType: AssetType,
        marketType: TradingMarketType,
        headerPrice: Double
    ): List<com.example.domain.model.ExchangePriceQuote> {
        return apiService.fetchExchangeBookTickers(symbol, assetType, marketType, headerPrice)
    }

    fun getExchangeQuotes(symbol: String, basePrice: Double, change24h: Double, baseVolume: Double): List<com.example.domain.model.ExchangePriceQuote> {
        // Legacy synchronous callers must never fabricate synthetic venue quotes
        return emptyList()
    }

    suspend fun fetchLiveCryptoPrice(symbol: String, isFutures: Boolean): Double? {
        return apiService.fetchBinanceLivePrice(symbol, isFutures)
    }

    fun calculateTradeSetupPlan(
        symbol: String,
        currentPrice: Double,
        side: com.example.domain.model.PositionSide,
        leverage: Int = 10,
        candles: List<com.example.domain.model.CandlePoint> = emptyList(),
        dayHigh: Double = 0.0,
        dayLow: Double = 0.0,
        timeframe: com.example.domain.model.Timeframe = com.example.domain.model.Timeframe.FIFTEEN_MIN,
        assetType: com.example.domain.model.AssetType = com.example.domain.model.AssetType.CRYPTO,
        marketType: com.example.domain.model.TradingMarketType = com.example.domain.model.TradingMarketType.SPOT,
        isSynthetic: Boolean = false,
        source: String = "Exchange API",
        agentReports: List<com.example.domain.model.AgentReport> = emptyList(),
        researcherDossier: com.example.domain.model.ResearcherDossier? = null,
        newsScannerResult: com.example.domain.model.NewsScannerResult? = null,
        manipulationAnalysis: com.example.domain.model.MarketManipulationAnalysis? = null,
        tradingViewSummary: com.example.domain.model.TradingViewTechnicalSummary? = null,
        futuresData: com.example.domain.model.CryptoFuturesData? = null,
        managerDecision: com.example.domain.model.ManagerDecision? = null,
        riskTeamEvaluation: com.example.domain.model.RiskTeamEvaluation? = null,
        offHoursMode: com.example.domain.model.OffHoursMode = com.example.domain.model.OffHoursMode.CAUTION,
        futuresPerpAvailable: Boolean = (assetType == AssetType.CRYPTO),
        customStockEntryWindows: List<com.example.domain.daytrader.model.TimeWindow>? = null,
        aiShiftPoints: Int = 0,
        aiShiftModelName: String? = null,
        dataMixedFromSpot: Boolean = false
    ): com.example.domain.model.TradeSetupPlan = Companion.calculateTradeSetupPlan(
        symbol, currentPrice, side, leverage, candles, dayHigh, dayLow, timeframe, assetType, marketType, isSynthetic, source,
        agentReports, researcherDossier, newsScannerResult, manipulationAnalysis, tradingViewSummary, futuresData,
        managerDecision, riskTeamEvaluation, offHoursMode, futuresPerpAvailable, customStockEntryWindows,
        aiShiftPoints, aiShiftModelName, dataMixedFromSpot
    )

    companion object {
        fun formatPrice(price: Double): String {
            val absVal = kotlin.math.abs(price)
            return when {
                absVal >= 1000.0 -> String.format(java.util.Locale.US, "%,.2f", price)
                absVal >= 1.0 -> String.format(java.util.Locale.US, "%.2f", price)
                absVal >= 0.01 -> String.format(java.util.Locale.US, "%.4f", price)
                absVal >= 0.0001 -> String.format(java.util.Locale.US, "%.6f", price)
                absVal > 0.0 -> String.format(java.util.Locale.US, "%.8f", price)
                else -> "0.00"
            }
        }

        fun calculateTradeSetupPlan(
            symbol: String,
            currentPrice: Double,
            side: com.example.domain.model.PositionSide,
            leverage: Int = 10,
            candles: List<com.example.domain.model.CandlePoint> = emptyList(),
            dayHigh: Double = 0.0,
            dayLow: Double = 0.0,
            timeframe: com.example.domain.model.Timeframe = com.example.domain.model.Timeframe.FIFTEEN_MIN,
            assetType: com.example.domain.model.AssetType = com.example.domain.model.AssetType.CRYPTO,
            marketType: com.example.domain.model.TradingMarketType = if (leverage > 1 || side == com.example.domain.model.PositionSide.SHORT) com.example.domain.model.TradingMarketType.FUTURES else com.example.domain.model.TradingMarketType.SPOT,
            isSynthetic: Boolean = false,
            source: String = "Exchange API",
            agentReports: List<com.example.domain.model.AgentReport> = emptyList(),
            researcherDossier: com.example.domain.model.ResearcherDossier? = null,
            newsScannerResult: com.example.domain.model.NewsScannerResult? = null,
            manipulationAnalysis: com.example.domain.model.MarketManipulationAnalysis? = null,
            tradingViewSummary: com.example.domain.model.TradingViewTechnicalSummary? = null,
            futuresData: com.example.domain.model.CryptoFuturesData? = null,
            managerDecision: com.example.domain.model.ManagerDecision? = null,
            riskTeamEvaluation: com.example.domain.model.RiskTeamEvaluation? = null,
            offHoursMode: com.example.domain.model.OffHoursMode = com.example.domain.model.OffHoursMode.CAUTION,
            futuresPerpAvailable: Boolean = (assetType == AssetType.CRYPTO),
            customStockEntryWindows: List<com.example.domain.daytrader.model.TimeWindow>? = null,
            aiShiftPoints: Int = 0,
            aiShiftModelName: String? = null,
            dataMixedFromSpot: Boolean = false
        ): com.example.domain.model.TradeSetupPlan {
            val inputs = com.example.domain.engine.UnifiedEntryEngine.EntryEngineInputs(
                symbol = symbol,
                currentPrice = currentPrice,
                side = side,
                timeframe = timeframe,
                marketType = marketType,
                assetType = assetType,
                leverage = leverage,
                candles = candles,
                dayHigh = dayHigh,
                dayLow = dayLow,
                isSynthetic = isSynthetic,
                source = source,
                agentReports = agentReports,
                researcherDossier = researcherDossier,
                newsScannerResult = newsScannerResult,
                manipulationAnalysis = manipulationAnalysis,
                tradingViewSummary = tradingViewSummary,
                futuresData = futuresData,
                managerDecision = managerDecision,
                riskTeamEvaluation = riskTeamEvaluation,
                offHoursMode = offHoursMode,
                futuresPerpAvailable = futuresPerpAvailable,
                customStockEntryWindows = customStockEntryWindows,
                aiShiftPoints = aiShiftPoints,
                aiShiftModelName = aiShiftModelName,
                dataMixedFromSpot = dataMixedFromSpot
            )
            return com.example.domain.engine.UnifiedEntryEngine.buildTradeSetupPlan(inputs)
        }
    }


    // Room Database access for offline analysis
    fun getCachedCryptoPairs(marketType: String): Flow<List<CryptoPair>> {
        return cryptoPairDao.getPairsByMarketType(marketType)
    }

    fun getAllCachedCryptoPairs(): Flow<List<CryptoPair>> {
        return cryptoPairDao.getAllPairs()
    }

    fun getCachedSignals(): Flow<List<TradingSignal>> {
        return tradingSignalDao.getAllSignals()
    }

    fun getCachedSignalsForPair(symbol: String): Flow<List<TradingSignal>> {
        return tradingSignalDao.getSignalsForPair(symbol)
    }

    suspend fun clearOfflineScanCache() {
        cryptoPairDao.clearAllPairs()
        tradingSignalDao.clearAllSignals()
    }

    // AI Models & Fleet Operations
    fun getAiModels(): Flow<List<com.example.domain.model.AiModelConfig>> {
        return aiModelDao.getAllModels().map { entityList ->
            entityList.map { entity ->
                val provider = try {
                    com.example.domain.model.AiProvider.valueOf(entity.provider)
                } catch (e: Exception) {
                    com.example.domain.model.AiProvider.GEMINI
                }
                val role = entity.assignedRole?.let { roleStr ->
                    try {
                        com.example.domain.model.AgentRole.valueOf(roleStr)
                    } catch (e: Exception) {
                        null
                    }
                }
                com.example.domain.model.AiModelConfig(
                    id = entity.id,
                    provider = provider,
                    modelName = entity.modelName,
                    apiKey = com.example.data.security.KeyEncryptionManager.decrypt(entity.apiKey),
                    assignedRole = role,
                    isEnabled = entity.isEnabled,
                    requestsRouted = entity.requestsRouted,
                    lastLatencyMs = entity.lastLatencyMs,
                    lastStatus = entity.lastStatus,
                    totalTokensUsed = entity.tokensUsed,
                    customEndpoint = entity.customEndpoint,
                    customDisplayName = entity.customDisplayName,
                    customAuthHeader = entity.customAuthHeader
                )
            }
        }
    }

    suspend fun saveAiModel(config: com.example.domain.model.AiModelConfig) {
        val sanitizedKey = config.apiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        val encryptedKey = com.example.data.security.KeyEncryptionManager.encrypt(sanitizedKey)
        val entity = com.example.data.db.AiModelEntity(
            id = config.id,
            provider = config.provider.name,
            modelName = config.modelName,
            apiKey = encryptedKey,
            assignedRole = config.assignedRole?.name,
            isEnabled = config.isEnabled,
            requestsRouted = config.requestsRouted,
            lastLatencyMs = config.lastLatencyMs,
            lastStatus = if (sanitizedKey.isBlank()) "Offline (Unconfigured)" else config.lastStatus,
            customEndpoint = config.customEndpoint,
            customDisplayName = config.customDisplayName,
            customAuthHeader = config.customAuthHeader,
            tokensUsed = config.totalTokensUsed
        )
        aiModelDao.insertModel(entity)
    }

    suspend fun deleteAiModel(id: String) {
        aiModelDao.deleteModel(id)
    }

    suspend fun setAiModelEnabled(id: String, isEnabled: Boolean) {
        aiModelDao.setEnabled(id, isEnabled)
    }

    suspend fun seedDefaultAiModelsIfEmpty() {
        seedDefaultCompaniesIfEmpty()
    }

    fun getAiCompanies(): Flow<List<AiCompanyConfig>> = combine(
        aiCompanyDao.getAllCompanies(),
        aiCompanyDao.getAllDiscoveredModels()
    ) { entities, models ->
        val modelsByCompany = models.groupBy { it.companyId }
        SupportedCompanies.ALL.map { def ->
            val entity = entities.firstOrNull { it.companyId.equals(def.id, ignoreCase = true) }
            val rawKey = entity?.apiKey?.let {
                try { com.example.data.security.KeyEncryptionManager.decrypt(it) } catch (_: Exception) { "" }
            } ?: ""
            val detected = (modelsByCompany[def.id] ?: emptyList()).map { m ->
                val vState = when {
                    m.isUsable && m.latencyMs > 0 -> com.example.domain.model.ModelVerificationState.VERIFIED
                    m.errorReason?.contains("credit", ignoreCase = true) == true || m.errorReason?.contains("balance", ignoreCase = true) == true || m.errorReason?.contains("limit: 0", ignoreCase = true) == true -> com.example.domain.model.ModelVerificationState.NEEDS_BILLING
                    !m.isUsable -> com.example.domain.model.ModelVerificationState.FAILED
                    else -> com.example.domain.model.ModelVerificationState.UNVERIFIED
                }
                DiscoveredModel(
                    id = m.id,
                    companyId = m.companyId,
                    modelName = m.modelName,
                    tier = try { ModelTier.valueOf(m.tier) } catch (_: Exception) { ModelTier.STANDARD },
                    isUsable = m.isUsable,
                    verificationState = vState,
                    isFree = m.isFree,
                    latencyMs = m.latencyMs,
                    contextWindow = m.contextWindow,
                    callsToday = m.callsToday,
                    lastTestedMs = m.lastTestedMs,
                    errorReason = m.errorReason
                )
            }
            val status = try {
                CompanyStatus.valueOf(entity?.status ?: CompanyStatus.NO_KEY.name)
            } catch (_: Exception) {
                if (rawKey.isNotBlank()) CompanyStatus.CONNECTED else CompanyStatus.NO_KEY
            }
            AiCompanyConfig(
                definition = def,
                apiKey = rawKey,
                status = status,
                statusDetail = entity?.statusDetail,
                quotaResetTimeMs = entity?.quotaResetTimeMs ?: 0L,
                pinnedModelId = entity?.pinnedModelId,
                customEndpoint = entity?.customEndpoint,
                customAuthHeader = entity?.customAuthHeader,
                callsToday = entity?.callsToday ?: 0,
                detectedModels = detected
            )
        }
    }

    suspend fun seedDefaultCompaniesIfEmpty() {
        val existing = aiCompanyDao.getAllCompanies().first()
        if (existing.isEmpty()) {
            val entities = SupportedCompanies.ALL.map { def: CompanyDefinition ->
                AiCompanyEntity(
                    companyId = def.id,
                    displayName = def.displayName,
                    apiKey = "",
                    status = CompanyStatus.NO_KEY.name,
                    statusDetail = "No key entered"
                )
            }
            aiCompanyDao.insertCompanies(entities)
        }
    }

    suspend fun saveCompanyApiKey(companyId: String, rawKey: String): DiscoveryResult {
        val cleanKey = rawKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        val def = SupportedCompanies.findById(companyId) ?: return DiscoveryResult(CompanyStatus.INVALID_KEY, "Unknown company", emptyList())

        val encryptedKey = if (cleanKey.isNotBlank()) com.example.data.security.KeyEncryptionManager.encrypt(cleanKey) else ""
        val existing = aiCompanyDao.getCompany(companyId)
        val entity = AiCompanyEntity(
            companyId = def.id,
            displayName = def.displayName,
            apiKey = encryptedKey,
            status = if (cleanKey.isBlank()) CompanyStatus.NO_KEY.name else CompanyStatus.TESTING.name,
            customEndpoint = existing?.customEndpoint,
            customAuthHeader = existing?.customAuthHeader
        )
        aiCompanyDao.insertCompany(entity)

        if (cleanKey.isBlank()) {
            aiCompanyDao.deleteDiscoveredModelsForCompany(def.id)
            return DiscoveryResult(CompanyStatus.NO_KEY, "No key", emptyList())
        }

        // Run automatic discovery and probing
        val discovery = discoveryEngine.discoverAndProbeCompany(
            company = def,
            rawApiKey = cleanKey,
            customEndpoint = existing?.customEndpoint,
            customAuthHeader = existing?.customAuthHeader
        )

        aiCompanyDao.updateApiKey(
            companyId = def.id,
            apiKey = encryptedKey,
            status = discovery.status.name,
            detail = discovery.statusDetail,
            errorMsg = if (discovery.status == CompanyStatus.INVALID_KEY) discovery.statusDetail else null
        )

        return discovery
    }

    suspend fun pasteAnyKeyAndConnect(rawKey: String): Pair<CompanyDefinition?, DiscoveryResult> {
        val cleanKey = rawKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        val detected = SupportedCompanies.detectCompanyFromKey(cleanKey)

        if (detected != null) {
            val result = saveCompanyApiKey(detected.id, cleanKey)
            return Pair(detected, result)
        }

        if (cleanKey.startsWith("sk-")) {
            // Try OpenAI first
            val openAiDef = SupportedCompanies.OPENAI
            val openAiResult = saveCompanyApiKey(openAiDef.id, cleanKey)
            if (openAiResult.status == CompanyStatus.CONNECTED) {
                return Pair(openAiDef, openAiResult)
            }
            // Try DeepSeek next
            val deepSeekDef = SupportedCompanies.DEEPSEEK
            val deepSeekResult = saveCompanyApiKey(deepSeekDef.id, cleanKey)
            if (deepSeekResult.status == CompanyStatus.CONNECTED || deepSeekResult.status == CompanyStatus.NEEDS_CREDIT) {
                aiCompanyDao.updateApiKey(openAiDef.id, "", CompanyStatus.NO_KEY.name, null, null)
                return Pair(deepSeekDef, deepSeekResult)
            }
            return Pair(openAiDef, openAiResult)
        }

        return Pair(null, DiscoveryResult(CompanyStatus.INVALID_KEY, "Unrecognized key format", emptyList()))
    }

    suspend fun refreshCompanyModels(companyId: String): DiscoveryResult {
        val def = SupportedCompanies.findById(companyId) ?: return DiscoveryResult(CompanyStatus.INVALID_KEY, "Unknown company", emptyList())
        val entity = aiCompanyDao.getCompany(companyId)
        val rawKey = entity?.apiKey?.let {
            try { com.example.data.security.KeyEncryptionManager.decrypt(it) } catch (_: Exception) { "" }
        } ?: ""

        val discovery = discoveryEngine.discoverAndProbeCompany(
            company = def,
            rawApiKey = rawKey,
            customEndpoint = entity?.customEndpoint,
            customAuthHeader = entity?.customAuthHeader
        )

        aiCompanyDao.updateStatus(def.id, discovery.status.name, discovery.statusDetail)
        return discovery
    }

    suspend fun refreshAllCompanies() {
        val companies = getAiCompanies().first()
        for (comp in companies) {
            if (comp.apiKey.isNotBlank()) {
                refreshCompanyModels(comp.definition.id)
            }
        }
    }

    suspend fun pinModelForCompany(companyId: String, modelId: String?) {
        aiCompanyDao.updatePinnedModel(companyId, modelId)
    }

    suspend fun updateCompanyCustomConfig(companyId: String, endpoint: String?, authHeader: String?) {
        aiCompanyDao.updateCustomConfig(companyId, endpoint, authHeader)
    }

    suspend fun getNews(symbol: String, assetType: AssetType? = null): List<NewsItem> {
        val resolvedType = assetType ?: if (isCryptoSymbol(symbol.trim().uppercase())) AssetType.CRYPTO else AssetType.STOCK
        val generalNews = apiService.fetchFinancialNews(symbol, resolvedType)
        val bNews = apiService.fetchBloombergNews(symbol, resolvedType)
        val combined = mutableListOf<NewsItem>()
        combined.addAll(bNews)
        combined.addAll(generalNews)
        return combined
            .map { com.example.domain.daytrader.news.SymbolNewsMatcher.tagNewsItem(it, symbol, resolvedType) }
            .filter { com.example.domain.daytrader.news.SymbolNewsMatcher.matchesSymbol(it, symbol, resolvedType) }
            .distinctBy { it.title }
    }

    suspend fun scanNewsWithGemini(
        symbol: String,
        assetType: AssetType,
        broadcastAllModels: Boolean = true
    ): com.example.domain.model.NewsScannerResult {
        val models = getAiModels().first()
        return newsScannerAgent.scanHeadlinesAndAnalyze(
            query = symbol,
            assetType = assetType,
            models = models,
            dispatcher = aiDispatcher,
            broadcastAllModels = broadcastAllModels
        )
    }

    suspend fun runAgentAnalysis(
        quote: MarketQuote,
        news: List<NewsItem>,
        scannerResult: com.example.domain.model.NewsScannerResult? = null,
        extraNodes: List<String> = emptyList(),
        aiEnrichmentEnabled: Boolean = true,
        balancingMode: com.example.domain.model.LoadBalancingMode = com.example.domain.model.LoadBalancingMode.AUTO_CASCADE_FREE_TIER
    ): MultiAgentSynthesis {
        val scopedNews = news
            .map { com.example.domain.daytrader.news.SymbolNewsMatcher.tagNewsItem(it, quote.symbol, quote.assetType) }
            .filter { com.example.domain.daytrader.news.SymbolNewsMatcher.matchesSymbol(it, quote.symbol, quote.assetType) }

        // 1. Always compute 100% deterministic, mathematically sound quantitative analysis first (< 2s)
        val deterministicSynthesis = TradingAgentsEngine.runMultiAgentAnalysis(
            quote = quote,
            news = scopedNews,
            scannerResult = scannerResult,
            extraNodesScanned = extraNodes
        )

        if (!aiEnrichmentEnabled) {
            return deterministicSynthesis
        }

        // 2. Check for connected AI companies
        val companies = getAiCompanies().first()
        val connected = companies.filter { it.isConnected }
        if (connected.isEmpty()) {
            return deterministicSynthesis
        }

        // 3. Asynchronously enrich committee reports with real AI calls (12s per call, 45s max total)
        // Levels (Entry, StopLoss, TakeProfits) remain 100% engine-locked per quantitative rules!
        return try {
            withTimeoutOrNull(45_000L) {
                enrichReportsWithAi(deterministicSynthesis, quote, connected, balancingMode)
            } ?: deterministicSynthesis
        } catch (e: Exception) {
            android.util.Log.w("MarketRepository", "AI enrichment fallback to deterministic engine: ${e.message}")
            deterministicSynthesis
        }
    }

    private suspend fun enrichReportsWithAi(
        base: MultiAgentSynthesis,
        quote: MarketQuote,
        connected: List<AiCompanyConfig>,
        mode: com.example.domain.model.LoadBalancingMode
    ): MultiAgentSynthesis {
        var totalShiftDelta = 0
        var primaryModelUsed: String? = null
        val updatedReports = base.agentReports.map { report ->
            val prompt = "Asset: ${quote.symbol} (${quote.assetType.name}), Price: $${quote.currentPrice}, 24h: ${quote.changePercent}%\n" +
                    "Your Role: ${report.role.title}\n" +
                    "Quantitative Indicators:\n${report.quantitativeMetrics.entries.joinToString("\n") { "${it.key}: ${it.value}" }}\n" +
                    "Deterministic Stance: ${report.stance.name}, Score: ${report.score}/10\n" +
                    "Provide 2-3 concise institutional observations for this setup as JSON: {\"thesis\": \"...\", \"shiftScore\": 0, \"keyPoints\": [\"...\"]}"

            val res = fleetRouter.executeWithFailover(
                prompt = prompt,
                role = report.role,
                companies = connected,
                mode = mode,
                requireJson = true
            )

            if (res.success && res.content.isNotBlank()) {
                val parsed = parseAiReportEnrichment(res.content)
                totalShiftDelta += parsed.scoreDelta
                if (primaryModelUsed == null) {
                    primaryModelUsed = res.modelUsed ?: res.companyUsed
                }
                val newScore = (report.score + parsed.scoreDelta).coerceIn(1, 10)
                report.copy(
                    score = newScore,
                    headlineThesis = parsed.thesis.ifBlank { report.headlineThesis },
                    keyPoints = if (parsed.keyPoints.isNotEmpty()) parsed.keyPoints else report.keyPoints,
                    sourceBadge = "AI (${res.modelUsed ?: res.companyUsed ?: "Fleet"})",
                    modelName = res.modelUsed
                )
            } else {
                res.errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                    apiService.recordApiError("AI_Fleet", "${report.role.title}: $err")
                }
                report.copy(sourceBadge = "Rule-based")
            }
        }

        val shiftPoints = (totalShiftDelta * 3).coerceIn(-15, 15)
        return base.copy(
            agentReports = updatedReports,
            nodesScanned = connected.map { it.definition.displayName },
            aiShiftPoints = shiftPoints,
            aiShiftModelName = primaryModelUsed
        )
    }

    private fun parseAiReportEnrichment(rawJson: String): AiParsedEnrichment {
        return try {
            val root = org.json.JSONObject(rawJson.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
            val thesis = root.optString("thesis")
            val delta = root.optInt("shiftScore", 0).coerceIn(-1, 1)
            val points = mutableListOf<String>()
            val arr = root.optJSONArray("keyPoints")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val p = arr.optString(i)
                    if (p.isNotBlank()) points.add(p)
                }
            }
            AiParsedEnrichment(thesis, delta, points)
        } catch (_: Exception) {
            AiParsedEnrichment("", 0, emptyList())
        }
    }

    private data class AiParsedEnrichment(val thesis: String, val scoreDelta: Int, val keyPoints: List<String>)

    suspend fun testApiConnection(sourceId: String): ApiSourceInfo {
        val source = apiSources.find { it.id == sourceId } ?: return apiSources.first()
        val (success, latency) = apiService.testApiConnection(source.endpointUrl)
        source.status = if (success) "Active (${latency}ms)" else "Degraded (${latency}ms)"
        source.latencyMs = latency
        return source
    }

    // Room Database Operations
    fun getSavedReports(): Flow<List<SavedResearchReport>> = reportDao.getAllReports()

    suspend fun saveReport(
        quote: MarketQuote,
        synthesis: MultiAgentSynthesis
    ): Long {
        val report = SavedResearchReport(
            symbol = quote.symbol,
            companyName = quote.name,
            assetType = quote.assetType.name,
            price = quote.currentPrice,
            changePercent = quote.changePercent,
            action = synthesis.action.label,
            confidenceScore = synthesis.confidenceScore,
            targetPrice = synthesis.targetPrice,
            stopLossPrice = synthesis.stopLossPrice,
            executiveSummary = synthesis.executiveSummary,
            riskLevel = synthesis.riskLevel,
            timestamp = System.currentTimeMillis()
        )
        return reportDao.insertReport(report)
    }

    suspend fun deleteReport(id: Long) = reportDao.deleteReportById(id)

    fun getWatchlist(): Flow<List<WatchlistItem>> = watchlistDao.getAllWatchlist()

    suspend fun addToWatchlist(symbol: String, name: String, assetType: AssetType) {
        watchlistDao.insertItem(
            WatchlistItem(
                symbol = symbol.uppercase(),
                name = name,
                assetType = assetType.name
            )
        )
    }

    suspend fun removeFromWatchlist(symbol: String) {
        watchlistDao.deleteItem(symbol.uppercase())
    }

    suspend fun seedDefaultWatchlistIfEmpty() {
        val current = watchlistDao.getAllWatchlist().first()
        if (current.isEmpty()) {
            val defaults = listOf(
                WatchlistItem("AAPL", "Apple Inc.", AssetType.STOCK.name),
                WatchlistItem("NVDA", "NVIDIA Corp.", AssetType.STOCK.name),
                WatchlistItem("TSLA", "Tesla Inc.", AssetType.STOCK.name),
                WatchlistItem("BTC", "Bitcoin", AssetType.CRYPTO.name),
                WatchlistItem("ETH", "Ethereum", AssetType.CRYPTO.name),
                WatchlistItem("MSFT", "Microsoft Corp.", AssetType.STOCK.name),
                WatchlistItem("SPY", "S&P 500 ETF", AssetType.STOCK.name),
                WatchlistItem("SOL", "Solana", AssetType.CRYPTO.name)
            )
            defaults.forEach { watchlistDao.insertItem(it) }
        }
    }

    fun isCryptoSymbol(symbol: String): Boolean {
        val upper = symbol.uppercase()
        if (upper.endsWith("USDT") || upper.endsWith("USDC") || upper.endsWith("BUSDT") ||
            upper.endsWith("BUSD") || upper.endsWith("-PERP") || upper.endsWith("PERP") ||
            upper.startsWith("1000") || upper.startsWith("10000")
        ) {
            return true
        }
        return upper in listOf(
            "BTC", "ETH", "SOL", "XRP", "DOGE", "ADA", "BNB", "AVAX", "DOT", "MATIC", "POL",
            "SUI", "NEAR", "LINK", "PEPE", "SHIB", "HYPE", "SEI", "UNI", "TRX", "XLM",
            "RENDER", "TAO", "INJ", "FET", "ENA", "WIF", "BONK", "FLOKI", "APT", "ARB", "OP",
            "LTC", "BCH", "TIA", "KAS", "STX", "AAVE", "MKR", "FTM", "S", "SNDK", "SNDKB",
            "SATS", "1000SATS", "PENDLE", "JUP", "PYTH", "ORDI", "WLD", "ARKM", "DYDX", "CRV",
            "ONDO", "W", "NOT", "TON", "STRK", "IO", "ZRO", "KCS", "GALA", "BLUR", "ICP", "ETC",
            "ALGO", "VET", "FIL", "MANA", "SAND", "AXS", "MEME", "BOME", "MEW", "POPCAT", "NEIRO",
            "TURBO", "CHZ", "APE", "LDO", "QNT", "SNX", "COMP", "GRT", "RUNE", "EGLD", "THETA",
            "FLOW", "KAVA", "ROSE", "GMX", "MINA", "BEAM", "RON", "OSMO", "SUPER", "PRIME", "PENGU",
            "TRUMP", "MELANIA", "SPX", "FARTCOIN", "VIRTUAL", "AI16Z", "DRIFT", "GOAT", "ACT", "PNUT",
            "COW", "CETUS", "SWELL", "DEGEN", "TROY", "RAY", "BANANA", "BRETT", "MORPHO", "THE",
            "MOVE", "ME", "BIO", "KAIA", "HYPER", "SWARMS", "AIXBT", "GRIFFAIN", "TOSHI", "MOODENG",
            "CHILLGUY", "DOGS", "HMSTR", "CATI", "SCR", "EIGEN", "GRASS"
        )
    }

    // --- Day-Trader Pack v1: Paper Ledger Operations ---
    fun getAllTradeOutcomes(): Flow<List<com.example.data.db.TradeOutcomeEntity>> = tradeOutcomeDao.getAllOutcomes()

    suspend fun insertTradeOutcome(outcome: com.example.data.db.TradeOutcomeEntity): Long {
        return tradeOutcomeDao.insertOutcome(outcome)
    }

    suspend fun recordClosedTradeOutcome(
        symbol: String,
        side: com.example.domain.model.PositionSide,
        entryPrice: Double,
        stopLoss: Double,
        takeProfit1: Double,
        exitPrice: Double,
        isRealTrade: Boolean,
        entryModel: String,
        sessionName: String,
        assetType: AssetType,
        exitReason: String
    ): Long {
        val risk = kotlin.math.abs(entryPrice - stopLoss).coerceAtLeast(entryPrice * 0.0005)
        val rawGain = if (side == com.example.domain.model.PositionSide.LONG) {
            exitPrice - entryPrice
        } else {
            entryPrice - exitPrice
        }
        val realizedR = rawGain / risk
        val status = if (realizedR > 0.0) "TP1_HIT" else "STOPPED_OUT"

        val entity = com.example.data.db.TradeOutcomeEntity(
            symbol = symbol.uppercase(),
            side = side.name,
            setupType = entryModel,
            timeframe = "5m",
            entryPrice = entryPrice,
            stopLoss = stopLoss,
            takeProfit1 = takeProfit1,
            createdAt = System.currentTimeMillis(),
            status = status,
            filled = true,
            fillPrice = entryPrice,
            exitPrice = exitPrice,
            realizedR = realizedR,
            closedAt = System.currentTimeMillis(),
            isRealTrade = isRealTrade,
            sessionName = sessionName,
            assetType = assetType.name,
            entryModel = entryModel,
            exitReason = exitReason
        )
        return tradeOutcomeDao.insertOutcome(entity)
    }

    suspend fun clearPaperLedger() = tradeOutcomeDao.clearAll()

    // --- Day-Trader Pack v1: Replay Engine ("Time Machine") ---
    suspend fun getOrBuildSessionPool(
        symbol: String,
        assetType: AssetType,
        isFutures: Boolean = false
    ): com.example.domain.daytrader.replay.SessionPool {
        val profileType = when {
            assetType == AssetType.STOCK -> com.example.domain.daytrader.model.MarketProfileType.US_EQUITY
            else -> com.example.domain.daytrader.model.MarketProfileType.CRYPTO
        }
        val cacheKey = "${symbol.uppercase()}_${profileType.name}_$isFutures"
        val now = System.currentTimeMillis()
        sessionPoolCache[cacheKey]?.let { (cachedAt, cachedPool) ->
            if ((now - cachedAt) < sessionPoolCacheTtlMs && cachedPool.totalSessions > 0) {
                return cachedPool
            }
        }

        val candles5m = apiService.fetch60Day5mCandles(symbol, assetType, isFutures)
        val pool = com.example.domain.daytrader.replay.SessionPool.buildFromCandlesWithWeekendFallback(
            symbol = symbol.uppercase(),
            profileType = profileType,
            candles = candles5m,
            todayTimestamp = now
        )
        if (pool.totalSessions > 0) {
            sessionPoolCache[cacheKey] = now to pool
        }
        return pool
    }

    suspend fun runReplayForPlan(
        symbol: String,
        assetType: AssetType,
        side: com.example.domain.model.PositionSide,
        entryPrice: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double,
        isFutures: Boolean = false,
        seed: Long = com.example.domain.daytrader.replay.ReplayEngine.DEFAULT_SEED,
        numReplays: Int = com.example.domain.daytrader.replay.ReplayEngine.DEFAULT_NUM_REPLAYS
    ): com.example.domain.daytrader.replay.ReplayResult {
        val pool = getOrBuildSessionPool(symbol, assetType, isFutures)
        val profileType = if (assetType == AssetType.STOCK) com.example.domain.daytrader.model.MarketProfileType.US_EQUITY
        else com.example.domain.daytrader.model.MarketProfileType.CRYPTO

        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance(
            if (profileType == com.example.domain.daytrader.model.MarketProfileType.CRYPTO) com.example.domain.daytrader.session.MarketCalendar.UTC_TZ
            else com.example.domain.daytrader.session.MarketCalendar.NY_TZ
        ).apply { timeInMillis = now }

        val timeIdx = if (profileType == com.example.domain.daytrader.model.MarketProfileType.CRYPTO) {
            ((cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)) / 5).coerceIn(0, 287)
        } else {
            val minsSinceOpen = (cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)) - (9 * 60 + 30)
            (minsSinceOpen / 5).coerceIn(0, 77)
        }

        val horizonBars = if (profileType == com.example.domain.daytrader.model.MarketProfileType.US_EQUITY) {
            (77 - timeIdx).coerceIn(1, 77)
        } else {
            36 // 3h (or 12 x 15m setup bars = 36 x 5m bars)
        }

        val replayPlan = com.example.domain.daytrader.replay.ReplayPlan(
            symbol = symbol.uppercase(),
            side = side,
            entryPrice = entryPrice,
            stopLossPrice = stopLossPrice,
            takeProfitPrice = takeProfitPrice,
            entryBarTimestamp = now - 300_000L,
            timeOfDayIndex = timeIdx,
            horizonBars = horizonBars,
            profileType = profileType,
            fundingBpsPer8h = if (isFutures) 1.0 else 0.0,
            isEntryBarClosed = true
        )

        val result = com.example.domain.daytrader.replay.ReplayEngine.runReplay(
            plan = replayPlan,
            pool = pool,
            seed = seed,
            numReplays = numReplays,
            useBlockBootstrap = true,
            useVolatilityScaling = true
        )
        if (result.refusalReason != null) {
            recordReplayRefusal()
        }
        return result
    }

    // --- Day-Trader Pack v1: Universal Scanner (Stocks + Crypto + Equity Perps) ---
    suspend fun scanDayTraderUnified(
        universeFilter: String = "ALL", // ALL, STOCKS, CRYPTO, PERPS
        marketType: TradingMarketType = TradingMarketType.SPOT,
        offHoursMode: com.example.domain.model.OffHoursMode = com.example.domain.model.OffHoursMode.CAUTION,
        customStockEntryWindows: List<com.example.domain.daytrader.model.TimeWindow>? = null
    ): com.example.domain.daytrader.scanner.DayTraderScannerEngine.UnifiedScanResult {
        val watchlist = watchlistDao.getAllWatchlist().first()

        val defaultStocks = listOf("SPY", "QQQ", "AAPL", "NVDA", "TSLA", "MSFT", "AMD", "META", "AMZN", "GOOGL", "PLTR", "COIN")
        val defaultCrypto = listOf("BTC", "ETH", "SOL", "XRP", "BNB", "DOGE", "AVAX", "SUI", "LINK", "NEAR", "ADA", "DOT")

        val symbolsToScan = mutableListOf<Pair<String, AssetType>>()
        if (universeFilter == "ALL" || universeFilter == "STOCKS") {
            val wStocks = watchlist.filter { it.assetType == AssetType.STOCK.name }.map { it.symbol }
            (wStocks + defaultStocks).distinct().take(12).forEach { symbolsToScan.add(it to AssetType.STOCK) }
        }
        if (universeFilter == "ALL" || universeFilter == "CRYPTO" || universeFilter == "PERPS") {
            val wCrypto = watchlist.filter { it.assetType == AssetType.CRYPTO.name }.map { it.symbol }
            (wCrypto + defaultCrypto).distinct().take(12).forEach { symbolsToScan.add(it to AssetType.CRYPTO) }
        }

        val distinctSymbols = symbolsToScan.distinctBy { "${it.first.uppercase()}_${it.second.name}" }
        val perpAvailability = mutableMapOf<String, Boolean>()
        for ((sym, aType) in distinctSymbols) {
            perpAvailability["${sym.uppercase()}_${aType.name}"] = apiService.hasBinanceFuturesPerp(sym, aType)
        }

        val isFuturesMode = marketType == TradingMarketType.FUTURES || universeFilter == "PERPS"
        val bundles = distinctSymbols.mapNotNull { (sym, aType) ->
            try {
                val daily = apiService.fetchDailyCandlesForScan(sym, aType, isFutures = isFuturesMode)
                val quote15m = getQuote(
                    symbol = sym,
                    timeframe = Timeframe.FIFTEEN_MIN,
                    marketType = marketType,
                    explicitAssetType = aType
                ).getOrNull()
                val quote1h = getQuote(
                    symbol = sym,
                    timeframe = Timeframe.ONE_HOUR,
                    marketType = marketType,
                    explicitAssetType = aType
                ).getOrNull()
                val bars5m = apiService.fetchQuoteForScan5m(sym, aType, isFutures = isFuturesMode)
                val latestPrice = quote15m?.currentPrice?.takeIf { it > 0.0 }
                    ?: daily.lastOrNull()?.close
                    ?: bars5m.lastOrNull()?.close
                    ?: return@mapNotNull null
                val latestVol = quote15m?.volume?.takeIf { it > 0.0 }?.let { it * latestPrice }
                    ?: ((daily.lastOrNull()?.volume ?: 100_000.0) * latestPrice)
                val sessionBadge = if (aType == AssetType.STOCK) {
                    val cal = java.util.Calendar.getInstance(com.example.domain.daytrader.session.MarketCalendar.NY_TZ)
                    val mins = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
                    when {
                        com.example.domain.daytrader.session.MarketCalendar.isNyseHoliday(cal) -> "OFF_HOURS"
                        mins in (9 * 60 + 30)..(16 * 60) -> "OPEN"
                        else -> "EXTENDED"
                    }
                } else {
                    "OPEN (24/7)"
                }
                val change24h = quote15m?.changePercent ?: if (daily.size >= 2 && daily[daily.size - 2].close > 0) {
                    ((daily.last().close - daily[daily.size - 2].close) / daily[daily.size - 2].close) * 100.0
                } else 0.0
                com.example.domain.daytrader.scanner.DayTraderScannerEngine.SymbolChartBundle(
                    symbol = sym,
                    name = quote15m?.name ?: sym,
                    assetType = aType,
                    dailyCandles = daily,
                    fiveMinuteCandles = bars5m,
                    setupCandles15m = quote15m?.candles?.filter { it.isClosed } ?: emptyList(),
                    htfCandles1h = quote1h?.candles?.filter { it.isClosed } ?: emptyList(),
                    volume24hUsd = latestVol,
                    currentPrice = latestPrice,
                    change24hPercent = change24h,
                    sessionBadge = sessionBadge
                )
            } catch (_: Exception) {
                null
            }
        }

        return com.example.domain.daytrader.scanner.DayTraderScannerEngine.scanSetupsAndMarketSummary(
            bundles = bundles,
            marketType = marketType,
            universeLabel = when (universeFilter) {
                "STOCKS" -> "US Equities"
                "CRYPTO" -> "Crypto"
                "PERPS" -> "Perpetuals"
                else -> "Stocks + Crypto"
            },
            sessionPoolProvider = { sym, pType ->
                sessionPoolCache["${sym.uppercase()}_${pType.name}_$isFuturesMode"]?.second
            },
            topReplayCount = 10,
            offHoursMode = offHoursMode,
            futuresPerpChecker = { s, a -> perpAvailability["${s.uppercase()}_${a.name}"] ?: (a == AssetType.CRYPTO) },
            customStockEntryWindows = customStockEntryWindows
        )
    }

    suspend fun scanDayTraderUniverse(
        universeFilter: String = "ALL", // ALL, STOCKS, CRYPTO, PERPS
        marketType: TradingMarketType = TradingMarketType.SPOT
    ): Pair<List<com.example.domain.daytrader.scanner.ScanHitItem>, com.example.domain.daytrader.scanner.ScanDiagnosticReport> {
        val unified = scanDayTraderUnified(universeFilter, marketType)
        return Pair(unified.rawRuleHits, unified.diagnostics)
    }

    // --- Master Verdict v1 Helpers ---
    suspend fun getClosedCandlesIncremental(
        symbol: String,
        assetType: AssetType,
        marketType: TradingMarketType,
        timeframe: Timeframe,
        minBars: Int = 1000,
        forceRefresh: Boolean = false
    ) = cacheRepository.getClosedCandlesIncremental(
        symbol = symbol,
        assetType = assetType,
        marketType = marketType,
        timeframe = timeframe,
        minBars = minBars,
        forceRefresh = forceRefresh
    )

    suspend fun fetchRealOrderBookSnapshot(
        symbol: String,
        assetType: AssetType,
        marketType: TradingMarketType
    ): com.example.domain.model.OrderBookSnapshot? {
        if (assetType != AssetType.CRYPTO) return null
        return apiService.fetchBinanceOrderBookSnapshot(
            symbol = symbol,
            isFutures = marketType == TradingMarketType.FUTURES
        )
    }

    suspend fun fetchFuturesDataSafe(symbol: String, referencePrice: Double): com.example.domain.model.CryptoFuturesData =
        apiService.fetchCryptoFuturesData(symbol, referencePrice)

    suspend fun fetchNewsSafe(symbol: String, assetType: AssetType): List<NewsItem> =
        apiService.fetchFinancialNews(symbol, assetType)

    suspend fun fetchBinance24hTickersBatch(symbols: List<String>, isFutures: Boolean) =
        apiService.fetchBinance24hTickersBatch(symbols, isFutures)

    fun observeCachedMasterVerdicts() = cacheRepository.observeAllMasterVerdicts()

    suspend fun saveMasterVerdict(verdict: com.example.domain.master.MasterVerdict, statusFlag: String = "FRESH", error: String? = null) =
        cacheRepository.upsertMasterVerdict(verdict, statusFlag, error)

    suspend fun getCachedMasterVerdict(symbol: String, marketType: TradingMarketType) =
        cacheRepository.getCachedMasterVerdict(symbol, marketType)

    fun observeArmedPlans() = cacheRepository.observeArmedPlans()

    suspend fun getArmedPlans() = cacheRepository.getArmedPlans()

    suspend fun upsertArmedPlan(plan: com.example.data.db.ArmedPlanEntity) =
        cacheRepository.upsertArmedPlan(plan)

    suspend fun deleteArmedPlan(id: String) = cacheRepository.deleteArmedPlan(id)

    suspend fun syncCalibrationHistoryFromRoom() = cacheRepository.loadAndSyncCalibrationHistory()

    suspend fun recordPlanOutcomeAndCalibrate(outcome: com.example.data.db.PlanOutcomeHistoryEntity): Long =
        cacheRepository.recordPlanOutcomeAndCalibrate(outcome)
}
