package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.background.AnalysisCoordinator
import com.example.data.background.BackgroundDiagnostics
import com.example.data.background.BackgroundMonitorMode
import com.example.data.background.BackgroundPreferences
import com.example.data.background.BinanceKlineWebSocketClient
import com.example.data.background.LiveMarketMonitorService
import com.example.data.background.MarketMonitorWorker
import com.example.data.background.QuietHoursConfig
import com.example.data.db.ArmedPlanEntity
import com.example.data.db.CryptoPair
import com.example.data.db.MasterVerdictEntity
import com.example.data.db.SavedResearchReport
import com.example.data.db.TradingSignal
import com.example.data.db.WatchlistItem
import com.example.data.notification.PriceAlertNotificationManager
import com.example.data.repository.DiagnosticsSnapshot
import com.example.data.repository.MarketRepository
import com.example.domain.master.HorizonSettings
import com.example.domain.master.MasterVerdict
import com.example.domain.master.TradingHorizon
import com.example.domain.model.AgentRole
import com.example.domain.model.AiModelConfig
import com.example.domain.model.ApiSourceInfo
import com.example.domain.model.CryptoExchange
import com.example.domain.model.CryptoScanItem
import com.example.domain.model.ExchangePriceQuote
import com.example.domain.model.LoadBalancingMode
import com.example.domain.model.MarketQuote
import com.example.domain.model.MultiAgentSynthesis
import com.example.domain.model.NewsItem
import com.example.domain.model.OffHoursMode
import com.example.domain.model.PositionSide
import com.example.domain.model.Timeframe
import com.example.domain.model.TradePlanLifecycleStatus
import com.example.domain.model.TradeSetupPlan
import com.example.domain.model.TradingMarketType
import com.example.domain.model.EntryOrderType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.example.domain.model.AssetType
import com.example.domain.model.BacktestResult
import com.example.domain.model.BacktestStrategyConfig
import com.example.domain.model.NewsScannerResult
import com.example.domain.agents.TradingAgentsEngine
import com.example.data.db.TradeOutcomeEntity
import com.example.domain.daytrader.alert.DayTraderAlertItem
import com.example.domain.daytrader.alert.DayTraderAlertManager
import com.example.domain.daytrader.fundamentals.CompanyFactsEngine
import com.example.domain.daytrader.fundamentals.CryptoFundamentals
import com.example.domain.daytrader.fundamentals.StockFundamentalsResult
import com.example.domain.daytrader.ledger.PaperLedgerEngine
import com.example.domain.daytrader.ledger.PaperLedgerSummary
import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.model.TimeWindow
import com.example.domain.daytrader.news.DayTraderNewsEventEngine
import com.example.domain.daytrader.news.EventRiskAssessment
import com.example.domain.daytrader.news.TaggedHeadlineItem
import com.example.domain.daytrader.plan.DayTraderPlan
import com.example.domain.daytrader.plan.ExitPlanner
import com.example.domain.daytrader.plan.StopRuleType
import com.example.domain.daytrader.plan.TargetRuleType
import com.example.domain.daytrader.replay.ReplayResult
import com.example.domain.daytrader.risk.RiskBudgetEngine
import com.example.domain.daytrader.risk.RiskEvaluationResult
import com.example.domain.daytrader.risk.RiskSettings
import com.example.domain.daytrader.scanner.ScanDiagnosticReport
import com.example.domain.daytrader.scanner.ScanHitItem
import com.example.domain.daytrader.scanner.ScanMarketSummary
import com.example.domain.daytrader.scanner.ScanSetupItem
import com.example.domain.daytrader.timer.EntryTimerState
import com.example.domain.daytrader.timer.EntryTimerStateMachine
import com.example.domain.daytrader.timer.EntryTimerStatus
import com.example.domain.analytics.QuantitativeEngine
import com.example.domain.engine.SignalCalibrationEngine
import com.example.domain.engine.SignalEngineV2
import com.example.domain.engine.SignalVetoDetector
import com.example.domain.model.BestTimeframeSummary
import com.example.domain.model.MarketRegime
import com.example.domain.model.ScenarioPlan
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.VerdictState
import com.example.domain.util.PriceFormatter
import kotlinx.coroutines.Dispatchers
import java.util.Locale
import kotlin.random.Random

enum class AppScreen(val title: String) {
    TERMINAL("OneQuery Hub"),
    BACKTEST("Strategy Backtest"),
    FAVORITES("Favorites"),
    SCANNER("Market Scanner"),
    WAR_ROOM("OneQuery Hub"),
    AI_FLEET("AI Fleet & APIs"),
    SAVED("Saved Reports")
}

data class UiState(
    val currentScreen: AppScreen = AppScreen.TERMINAL,
    val selectedSymbol: String = "NVDA",
    val selectedTimeframe: Timeframe = Timeframe.ONE_MONTH,
    val selectedMarketType: TradingMarketType = TradingMarketType.SPOT,
    val selectedExchange: CryptoExchange = CryptoExchange.BINANCE,
    val exchangeQuotes: List<ExchangePriceQuote> = emptyList(),
    val positionSetupSide: PositionSide = PositionSide.LONG,
    val tradeSetupPlan: TradeSetupPlan? = null,
    val isLiveTickingActive: Boolean = true,
    val liveTickDirection: Int = 0, // +1 up (green), -1 down (red), 0 neutral
    val liveTickCounter: Long = 0,
    val alertsEnabled: Boolean = true,
    val customAlertThreshold: Double? = null,
    val alertNotificationsLog: List<String> = emptyList(),
    val showAdBanner: Boolean = true,
    val currentQuote: MarketQuote? = null,
    val newsItems: List<NewsItem> = emptyList(),
    val agentSynthesis: MultiAgentSynthesis? = null,
    val newsScannerResult: NewsScannerResult? = null,
    val selectedFavoriteSymbol: String? = null,
    val favoriteQuote: MarketQuote? = null,
    val favoriteSynthesis: MultiAgentSynthesis? = null,
    val isLoadingFavorite: Boolean = false,
    val watchlist: List<WatchlistItem> = emptyList(),
    val savedReports: List<SavedResearchReport> = emptyList(),
    val apiSources: List<ApiSourceInfo> = emptyList(),
    val aiModels: List<AiModelConfig> = emptyList(),
    val aiCompanies: List<com.example.domain.model.AiCompanyConfig> = emptyList(),
    val aiEnrichmentEnabled: Boolean = true,
    val isDiscoveringCompany: String? = null,
    val ambiguousPastedKey: String? = null,
    val loadBalancingMode: LoadBalancingMode = LoadBalancingMode.AUTO_CASCADE_FREE_TIER,
    val scanTimeframe: Timeframe = Timeframe.FIVE_MIN,
    val cryptoScanList: List<CryptoScanItem> = emptyList(),
    val cachedCryptoPairs: List<CryptoPair> = emptyList(),
    val cachedTradingSignals: List<TradingSignal> = emptyList(),
    val isScanningCrypto: Boolean = false,
    val selectedLeverage: Int = 10,
    val isLoadingQuote: Boolean = false,
    val isRunningAgents: Boolean = false,
    val agentRunningPhase: String = "",
    val lastAiDispatchTelemetry: List<Pair<AgentRole, String>> = emptyList(),
    val feedbackMessage: String? = null,
    val backtestConfig: BacktestStrategyConfig = BacktestStrategyConfig(),
    val backtestResult: BacktestResult? = null,
    val isRunningBacktest: Boolean = false,
    val isViewingSavedPlan: Boolean = false,
    val viewingSavedReport: SavedResearchReport? = null,
    // Day-Trader Pack v1 State
    val replayResult: ReplayResult? = null,
    val isRunningReplay: Boolean = false,
    val replaySeed: Long = 19L,
    val dayTraderPlan: DayTraderPlan? = null,
    val entryTimerStatus: EntryTimerStatus? = null,
    val riskSettings: RiskSettings = RiskSettings(
        accountBalance = 0.0,
        maxRiskPerTradeDollars = 0.0,
        riskPercent = 1.0,
        isConfigured = false,
        availableCashOrMargin = 0.0
    ),
    val riskEvaluation: RiskEvaluationResult? = null,
    val scanHits: List<ScanHitItem> = emptyList(),
    val scanSetups: List<ScanSetupItem> = emptyList(),
    val scanMarketSummary: ScanMarketSummary? = null,
    val scanDiagnostics: ScanDiagnosticReport? = null,
    val isRunningDayTraderScan: Boolean = false,
    val selectedScannerUniverse: String = "ALL",
    val tradeOutcomes: List<TradeOutcomeEntity> = emptyList(),
    val paperLedgerSummary: PaperLedgerSummary? = null,
    val dayTraderAlerts: List<DayTraderAlertItem> = emptyList(),
    val taggedHeadlines: List<TaggedHeadlineItem> = emptyList(),
    val eventRiskAssessment: EventRiskAssessment? = null,
    val cryptoFundamentals: CryptoFundamentals? = null,
    val verdictState: VerdictState? = null,
    val bestTimeframeSummary: BestTimeframeSummary? = null,
    val hypotheticalScenarioReplay: ReplayResult? = null,
    val hypotheticalScenarioSide: PositionSide? = null,
    val isRunningHypotheticalReplay: Boolean = false,
    // Patch A & B Settings + Fundamentals + Diagnostics
    val offHoursMode: OffHoursMode = OffHoursMode.CAUTION,
    val stockEntryWindows: List<TimeWindow> = listOf(
        TimeWindow(9, 45, 11, 30),
        TimeWindow(14, 0, 15, 30)
    ),
    val secUserAgent: String = "",
    val acceptHigherRiskThisTrade: Boolean = false,
    val stockFundamentals: StockFundamentalsResult? = null,
    val diagnosticsSnapshot: DiagnosticsSnapshot = DiagnosticsSnapshot(),
    val futuresPerpAvailableForSymbol: Boolean = true,
    // Master Verdict v1 (Parts A, B, C)
    val masterVerdict: MasterVerdict? = null,
    val isLoadingMasterVerdict: Boolean = false,
    val selectedHorizon: TradingHorizon = TradingHorizon.INTRADAY,
    val horizonSettings: HorizonSettings = HorizonSettings(),
    val cachedMasterVerdicts: List<MasterVerdictEntity> = emptyList(),
    val armedPlans: List<ArmedPlanEntity> = emptyList(),
    val backgroundMonitorMode: BackgroundMonitorMode = BackgroundMonitorMode.STANDARD,
    val quietHoursConfig: QuietHoursConfig = QuietHoursConfig(),
    val allowBgOnBatterySaver: Boolean = false,
    val backgroundDiagnostics: BackgroundDiagnostics = BackgroundDiagnostics(),
    val isRefreshingWatchlist: Boolean = false
) {
    val connectedCompaniesCount: Int get() = aiCompanies.count { it.isConnected }
    val isEngineOnly: Boolean get() = !aiEnrichmentEnabled || connectedCompaniesCount == 0
    val hasConnectedLlm: Boolean get() = aiEnrichmentEnabled && connectedCompaniesCount > 0
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MarketRepository(application)
    private val prefs = application.getSharedPreferences("trading_agents_prefs", Context.MODE_PRIVATE)
    val notificationManager = PriceAlertNotificationManager(application)
    val alertManager = DayTraderAlertManager(application, notificationManager)
    private val analysisCoordinator = AnalysisCoordinator(application, repository, notificationManager)
    private val klineWsClient = BinanceKlineWebSocketClient(repository.getOkHttpClient(), viewModelScope)
    private var liveTickerJob: Job? = null
    private var replayJob: Job? = null
    private var fundamentalsJob: Job? = null
    private var masterVerdictJob: Job? = null
    private var lastAlertedSymbolStatus: String = ""
    private val _tradePlansCache = mutableMapOf<String, TradeSetupPlan>()
    private fun getPlanCacheKey(symbol: String, marketType: TradingMarketType, timeframe: Timeframe) =
        "${symbol.uppercase()}_${marketType.name}_${timeframe.name}"

    private val _uiState = MutableStateFlow(UiState(apiSources = repository.apiSources))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadPersistedPreferences()

        viewModelScope.launch {
            repository.syncCalibrationHistoryFromRoom()
            repository.seedDefaultWatchlistIfEmpty()
            repository.seedDefaultAiModelsIfEmpty()
            repository.seedDefaultCompaniesIfEmpty()
        }

        viewModelScope.launch {
            repository.getAiCompanies().collectLatest { companies ->
                _uiState.value = _uiState.value.copy(aiCompanies = companies)
            }
        }

        viewModelScope.launch {
            repository.getWatchlist().collectLatest { list ->
                _uiState.value = _uiState.value.copy(watchlist = list)
            }
        }

        viewModelScope.launch {
            repository.getSavedReports().collectLatest { list ->
                _uiState.value = _uiState.value.copy(savedReports = list)
            }
        }

        viewModelScope.launch {
            repository.getAiModels().collectLatest { models ->
                _uiState.value = _uiState.value.copy(aiModels = models)
            }
        }

        viewModelScope.launch {
            repository.getAllCachedCryptoPairs().collectLatest { pairs ->
                _uiState.value = _uiState.value.copy(cachedCryptoPairs = pairs)
            }
        }

        viewModelScope.launch {
            repository.getCachedSignals().collectLatest { signals ->
                _uiState.value = _uiState.value.copy(cachedTradingSignals = signals)
            }
        }

        viewModelScope.launch {
            repository.getAllTradeOutcomes().collectLatest { outcomes ->
                val summary = PaperLedgerEngine.computeLedgerSummary(outcomes)
                PaperLedgerEngine.feedOutcomesToCalibrationEngine(outcomes)
                _uiState.value = _uiState.value.copy(
                    tradeOutcomes = outcomes,
                    paperLedgerSummary = summary
                )
            }
        }

        viewModelScope.launch {
            alertManager.alerts.collectLatest { alerts ->
                val activeSym = _uiState.value.selectedSymbol
                val scopedAlerts = alerts.filter {
                    it.symbol.equals(activeSym, ignoreCase = true) ||
                        it.symbol.equals("MACRO", ignoreCase = true)
                }
                _uiState.value = _uiState.value.copy(dayTraderAlerts = scopedAlerts)
            }
        }

        viewModelScope.launch {
            repository.observeCachedMasterVerdicts().collectLatest { verdicts ->
                _uiState.value = _uiState.value.copy(cachedMasterVerdicts = verdicts)
            }
        }

        viewModelScope.launch {
            repository.observeArmedPlans().collectLatest { armed ->
                _uiState.value = _uiState.value.copy(armedPlans = armed)
            }
        }

        viewModelScope.launch {
            klineWsClient.closedEvents.collect { evt ->
                val state = _uiState.value
                if (evt.symbol.equals(state.selectedSymbol, ignoreCase = true) &&
                    state.currentQuote?.assetType == AssetType.CRYPTO
                ) {
                    repository.cacheRepository.appendClosedWebSocketCandle(
                        symbol = evt.symbol,
                        marketType = evt.marketType,
                        timeframe = evt.timeframe,
                        candle = evt.candle
                    )
                    val q = state.currentQuote ?: return@collect
                    refreshMasterVerdict(
                        quote = q,
                        marketType = evt.marketType,
                        closedTimeframe = evt.timeframe,
                        forceRefresh = false
                    )
                }
            }
        }

        applyBackgroundMonitorSchedule(BackgroundPreferences.getMode(getApplication()))

        // Initial load
        executeUnifiedQuery("NVDA")
        scanCrypto(TradingMarketType.FUTURES)
        runDayTraderScan("ALL")
        startLiveTicker()
    }

    private fun loadPersistedPreferences() {
        val modeName = prefs.getString("off_hours_mode", OffHoursMode.CAUTION.name) ?: OffHoursMode.CAUTION.name
        val offHours = runCatching { OffHoursMode.valueOf(modeName) }.getOrDefault(OffHoursMode.CAUTION)
        val secUa = prefs.getString("sec_user_agent", "") ?: ""
        val windowsRaw = prefs.getString("stock_entry_windows", "09:45-11:30, 14:00-15:30") ?: "09:45-11:30, 14:00-15:30"
        val parsedWindows = parseEntryWindowsString(windowsRaw)

        val isRiskConfigured = prefs.getBoolean("risk_configured", false)
        val accBal = prefs.getFloat("risk_account_balance", 0.0f).toDouble()
        val riskPct = prefs.getFloat("risk_percent", 1.0f).toDouble().takeIf { it > 0.0 } ?: 1.0
        val loadedRisk = if (isRiskConfigured && accBal > 0.0) {
            RiskSettings(
                accountBalance = accBal,
                riskPercent = riskPct,
                maxRiskPerTradeDollars = accBal * (riskPct / 100.0),
                availableCashOrMargin = accBal,
                isConfigured = true
            )
        } else {
            RiskSettings(
                accountBalance = 0.0,
                maxRiskPerTradeDollars = 0.0,
                riskPercent = 1.0,
                isConfigured = false,
                availableCashOrMargin = 0.0
            )
        }

        val appCtx = getApplication<Application>()
        val bgMode = BackgroundPreferences.getMode(appCtx)
        val quietCfg = BackgroundPreferences.getQuietHours(appCtx)
        val allowBatt = BackgroundPreferences.getAllowOnBatterySaver(appCtx)
        val hSettings = BackgroundPreferences.getHorizonSettings(appCtx)
        val bgDiag = BackgroundPreferences.getDiagnostics(appCtx)

        _uiState.value = _uiState.value.copy(
            offHoursMode = offHours,
            secUserAgent = secUa,
            stockEntryWindows = parsedWindows,
            riskSettings = loadedRisk,
            backgroundMonitorMode = bgMode,
            quietHoursConfig = quietCfg,
            allowBgOnBatterySaver = allowBatt,
            horizonSettings = hSettings,
            backgroundDiagnostics = bgDiag
        )
    }

    private fun parseEntryWindowsString(raw: String): List<TimeWindow> {
        val parsed = raw.split(",", ";").mapNotNull { segment ->
            val cleanSeg = segment.trim().replace("–", "-")
            val parts = cleanSeg.split("-")
            if (parts.size != 2) return@mapNotNull null
            val s = parts[0].trim().split(":")
            val e = parts[1].trim().split(":")
            if (s.size != 2 || e.size != 2) return@mapNotNull null
            val sh = s[0].toIntOrNull()?.coerceIn(0, 23) ?: return@mapNotNull null
            val sm = s[1].toIntOrNull()?.coerceIn(0, 59) ?: return@mapNotNull null
            val eh = e[0].toIntOrNull()?.coerceIn(0, 23) ?: return@mapNotNull null
            val em = e[1].toIntOrNull()?.coerceIn(0, 59) ?: return@mapNotNull null
            if (eh * 60 + em <= sh * 60 + sm) return@mapNotNull null
            TimeWindow(sh, sm, eh, em)
        }
        return parsed.ifEmpty {
            listOf(TimeWindow(9, 45, 11, 30), TimeWindow(14, 0, 15, 30))
        }
    }

    fun setOffHoursMode(mode: OffHoursMode) {
        prefs.edit().putString("off_hours_mode", mode.name).apply()
        _uiState.value = _uiState.value.copy(
            offHoursMode = mode,
            feedbackMessage = "Session off-hours mode set to ${mode.label}"
        )
        recalculateCurrentSetup()
    }

    fun setStockEntryWindows(windowsStr: String) {
        val parsed = parseEntryWindowsString(windowsStr)
        val serialized = parsed.joinToString(", ") {
            String.format(Locale.US, "%02d:%02d-%02d:%02d", it.startHour, it.startMinute, it.endHour, it.endMinute)
        }
        prefs.edit().putString("stock_entry_windows", serialized).apply()
        _uiState.value = _uiState.value.copy(
            stockEntryWindows = parsed,
            feedbackMessage = "Stock entry windows updated: $serialized ET"
        )
        recalculateCurrentSetup()
    }

    fun setSecUserAgent(userAgent: String) {
        val clean = userAgent.trim()
        prefs.edit().putString("sec_user_agent", clean).apply()
        _uiState.value = _uiState.value.copy(
            secUserAgent = clean,
            feedbackMessage = if (clean.contains("@")) "SEC User-Agent saved" else "Include name & email for SEC EDGAR access"
        )
        val quote = _uiState.value.currentQuote ?: return
        fetchStockFundamentalsIfApplicable(quote)
    }

    fun acceptHigherRiskForCurrentTrade() {
        _uiState.value = _uiState.value.copy(
            acceptHigherRiskThisTrade = true,
            feedbackMessage = "Override accepted for this trade (min notional risk override active)."
        )
        val quote = _uiState.value.currentQuote ?: return
        val dtPlan = _uiState.value.dayTraderPlan ?: return
        val profile = getMarketProfile(quote.assetType, _uiState.value.selectedMarketType)
        val updatedRisk = evaluateRiskForPlan(dtPlan, profile, quote.assetType)
        _uiState.value = _uiState.value.copy(riskEvaluation = updatedRisk)
    }

    fun refreshDiagnostics() {
        _uiState.value = _uiState.value.copy(diagnosticsSnapshot = repository.getDiagnosticsSnapshot())
    }

    private fun recalculateCurrentSetup() {
        val quote = _uiState.value.currentQuote ?: return
        val currentTf = _uiState.value.selectedTimeframe
        val synth = _uiState.value.agentSynthesis
        val setup = repository.calculateTradeSetupPlan(
            symbol = quote.symbol,
            currentPrice = quote.currentPrice,
            side = _uiState.value.positionSetupSide,
            leverage = _uiState.value.selectedLeverage,
            candles = quote.candles,
            dayHigh = quote.dayHigh,
            dayLow = quote.dayLow,
            timeframe = currentTf,
            assetType = quote.assetType,
            marketType = _uiState.value.selectedMarketType,
            isSynthetic = quote.isSynthetic,
            source = quote.primarySource,
            agentReports = synth?.agentReports ?: emptyList(),
            researcherDossier = synth?.researcherDossier,
            newsScannerResult = _uiState.value.newsScannerResult,
            manipulationAnalysis = synth?.manipulationAnalysis,
            tradingViewSummary = synth?.tradingViewSummary,
            futuresData = quote.futuresData,
            managerDecision = synth?.managerDecision,
            riskTeamEvaluation = synth?.riskTeamEvaluation,
            offHoursMode = _uiState.value.offHoursMode,
            futuresPerpAvailable = _uiState.value.futuresPerpAvailableForSymbol,
            customStockEntryWindows = _uiState.value.stockEntryWindows,
            aiShiftPoints = synth?.aiShiftPoints ?: 0,
            aiShiftModelName = synth?.aiShiftModelName
        )
        runDayTraderPipeline(quote, setup, _uiState.value.newsItems, synth)
    }

    private fun fetchStockFundamentalsIfApplicable(quote: MarketQuote) {
        fundamentalsJob?.cancel()
        val normalized = CompanyFactsEngine.normalizeEquityTickerForSec(quote.symbol)
        val isEquityOrEqPerp = quote.assetType == AssetType.STOCK || normalized != quote.symbol.trim().uppercase()
        if (!isEquityOrEqPerp) {
            _uiState.value = _uiState.value.copy(stockFundamentals = null)
            return
        }
        fundamentalsJob = viewModelScope.launch(Dispatchers.IO) {
            val res = repository.fetchSecFundamentals(
                symbol = quote.symbol,
                assetType = quote.assetType,
                secUserAgent = _uiState.value.secUserAgent
            )
            if (_uiState.value.selectedSymbol.equals(quote.symbol, ignoreCase = true)) {
                _uiState.value = _uiState.value.copy(stockFundamentals = res)
            }
        }
    }

    private fun startLiveTicker() {
        liveTickerJob?.cancel()
        liveTickerJob = viewModelScope.launch {
            var prevPrice = _uiState.value.currentQuote?.currentPrice ?: 0.0
            while (isActive) {
                delay(2500)
                if (!_uiState.value.isLiveTickingActive) continue
                val current = _uiState.value.currentQuote ?: continue
                if (current.currentPrice <= 0.0) continue

                // Fetch real live price from Binance for Crypto or Yahoo for Stock
                val livePrice = if (current.assetType == AssetType.CRYPTO) {
                    val isFutures = _uiState.value.selectedMarketType == TradingMarketType.FUTURES
                    repository.fetchLiveCryptoPrice(current.symbol, isFutures)
                } else null

                val newPrice = livePrice?.takeIf { it > 0.0 } ?: continue
                if (newPrice == prevPrice) continue

                // Anomaly check: ensure price doesn't jump wildly (e.g. 1000x multiplier or network glitch)
                if (current.currentPrice > 0.0) {
                    val ratio = newPrice / current.currentPrice
                    if (ratio > 1.8 || ratio < 0.55) {
                        continue
                    }
                }

                // Update last candle close & high/low with real Binance tape
                val updatedCandles = if (current.candles.isNotEmpty()) {
                    val last = current.candles.last()
                    val updatedLast = last.copy(
                        high = maxOf(last.high, newPrice),
                        low = minOf(last.low, newPrice),
                        close = newPrice
                    )
                    current.candles.dropLast(1) + updatedLast
                } else current.candles

                val refOpen24h = current.ticker24hOpen.takeIf { it > 0.0 }
                    ?: current.open.takeIf { it > 0.0 }
                    ?: newPrice
                val lastBar = updatedCandles.lastOrNull()
                val updatedCandleChangePct = if (lastBar != null && lastBar.open > 0.0) {
                    ((lastBar.close - lastBar.open) / lastBar.open) * 100.0
                } else {
                    current.candleChangePercent
                }

                val newQuote = current.copy(
                    currentPrice = newPrice,
                    change = newPrice - refOpen24h,
                    changePercent = if (refOpen24h > 0.0) ((newPrice - refOpen24h) / refOpen24h) * 100.0 else current.changePercent,
                    candleChangePercent = updatedCandleChangePct,
                    dayHigh = maxOf(current.dayHigh, newPrice),
                    dayLow = minOf(current.dayLow, newPrice),
                    candles = updatedCandles
                )
                prevPrice = newPrice

                val currentTf = _uiState.value.selectedTimeframe
                val currentMarket = _uiState.value.selectedMarketType

                // Update exchange quotes with real-time book ticker spread (never synthetic offsets)
                val updatedExchanges = repository.fetchRealExchangeQuotes(
                    symbol = newQuote.symbol,
                    assetType = newQuote.assetType,
                    marketType = currentMarket,
                    headerPrice = newPrice
                )

                val cacheKey = getPlanCacheKey(newQuote.symbol, currentMarket, currentTf)
                val targetSide = _uiState.value.positionSetupSide

                // Maintain stable trade setup plan levels while updating real-time lifecycle status
                val currentPlan = _uiState.value.tradeSetupPlan
                val updatedSetup = if (_uiState.value.isViewingSavedPlan && currentPlan != null) {
                    val newStatus = currentPlan.evaluateWithCandles(newPrice, updatedCandles)
                    val isNowFilled = currentPlan.entryFilled || newStatus == TradePlanLifecycleStatus.IN_ENTRY_ZONE || newStatus == TradePlanLifecycleStatus.ACTIVE_IN_TRADE
                    currentPlan.copy(currentPrice = newPrice, lifecycleStatus = newStatus, entryFilled = isNowFilled)
                } else if (currentPlan != null && currentPlan.pairSymbol.equals(newQuote.symbol, ignoreCase = true) && currentPlan.timeframe == currentTf && currentPlan.marketType == currentMarket) {
                    val newStatus = currentPlan.evaluateWithCandles(newPrice, updatedCandles)
                    val isNowFilled = currentPlan.entryFilled || newStatus == TradePlanLifecycleStatus.IN_ENTRY_ZONE || newStatus == TradePlanLifecycleStatus.ACTIVE_IN_TRADE
                    val updated = currentPlan.copy(currentPrice = newPrice, lifecycleStatus = newStatus, entryFilled = isNowFilled)
                    _tradePlansCache[cacheKey] = updated
                    updated
                } else {
                    val candidate = _tradePlansCache[cacheKey]
                    if (candidate != null && isCachedPlanValid(candidate, newQuote.symbol, currentTf, currentMarket, targetSide, newPrice)) {
                        val newStatus = candidate.evaluateWithCandles(newPrice, updatedCandles)
                        val isNowFilled = candidate.entryFilled || newStatus == TradePlanLifecycleStatus.IN_ENTRY_ZONE || newStatus == TradePlanLifecycleStatus.ACTIVE_IN_TRADE
                        val updated = candidate.copy(currentPrice = newPrice, lifecycleStatus = newStatus, entryFilled = isNowFilled)
                        _tradePlansCache[cacheKey] = updated
                        updated
                    } else {
                        val synth = _uiState.value.agentSynthesis
                        val fresh = repository.calculateTradeSetupPlan(
                            newQuote.symbol,
                            newPrice,
                            targetSide,
                            _uiState.value.selectedLeverage,
                            newQuote.candles,
                            newQuote.dayHigh,
                            newQuote.dayLow,
                            currentTf,
                            assetType = newQuote.assetType,
                            marketType = currentMarket,
                            isSynthetic = newQuote.isSynthetic,
                            source = newQuote.primarySource,
                            offHoursMode = _uiState.value.offHoursMode,
                            futuresPerpAvailable = _uiState.value.futuresPerpAvailableForSymbol,
                            customStockEntryWindows = _uiState.value.stockEntryWindows,
                            aiShiftPoints = synth?.aiShiftPoints ?: 0,
                            aiShiftModelName = synth?.aiShiftModelName
                        )
                        _tradePlansCache[cacheKey] = fresh
                        fresh
                    }
                }

                // Check TradingSignals for Buy / Sell / Stop triggers
                if (_uiState.value.alertsEnabled && _uiState.value.cachedTradingSignals.isNotEmpty()) {
                    _uiState.value.cachedTradingSignals.forEach { sig ->
                        if (sig.pairSymbol.equals(newQuote.symbol, ignoreCase = true) || sig.pairSymbol.contains(newQuote.symbol, ignoreCase = true)) {
                            val triggered = notificationManager.checkAndTriggerSignalAlert(sig, newPrice, prevPrice)
                            if (triggered) {
                                val logEntry = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] Alert for ${sig.pairSymbol}: $${formatPrice(newPrice)}"
                                _uiState.value = _uiState.value.copy(
                                    alertNotificationsLog = listOf(logEntry) + _uiState.value.alertNotificationsLog.take(9)
                                )
                            }
                        }
                    }
                }

                // Check custom price threshold alert
                val threshold = _uiState.value.customAlertThreshold
                if (_uiState.value.alertsEnabled && threshold != null) {
                    val crossed = (prevPrice < threshold && newPrice >= threshold) || (prevPrice > threshold && newPrice <= threshold)
                    if (crossed) {
                        notificationManager.sendNotification(
                            notificationId = 9999,
                            title = "THRESHOLD HIT: ${newQuote.symbol}",
                            message = "Price crossed your custom alert threshold at $${formatPrice(newPrice)}!",
                            subText = "Custom Price Alert"
                        )
                        val logEntry = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] Custom Threshold Hit for ${newQuote.symbol}: $${formatPrice(newPrice)}"
                        _uiState.value = _uiState.value.copy(
                            customAlertThreshold = null,
                            alertNotificationsLog = listOf(logEntry) + _uiState.value.alertNotificationsLog.take(9)
                        )
                    }
                }

                val tickDir = if (newPrice > prevPrice) 1 else if (newPrice < prevPrice) -1 else 0
                prevPrice = newPrice

                // Evaluate Day-Trader Entry Timer & Lifecycle alerts on live tick without mutating entry triggers on unclosed bar
                val currentDayPlan = _uiState.value.dayTraderPlan
                val updatedTimer = if (currentDayPlan != null && currentDayPlan.symbol.equals(newQuote.symbol, ignoreCase = true)) {
                    val profile = getMarketProfile(newQuote.assetType, currentMarket)
                    val closedBars = newQuote.candles.filter { it.isClosed }
                    val isLastClosed = newQuote.candles.lastOrNull()?.isClosed ?: true
                    EntryTimerStateMachine.evaluate(
                        plan = currentDayPlan,
                        latestCompletedBar = closedBars.lastOrNull(),
                        isCurrentBarClosed = isLastClosed,
                        profile = profile,
                        hasActiveVeto = updatedSetup.verdictState == SignalVerdictState.NO_TRADE || currentDayPlan.validationError != null,
                        vetoMessage = updatedSetup.unavailableReason.takeIf { it.isNotBlank() } ?: currentDayPlan.validationError
                    )
                } else _uiState.value.entryTimerStatus

                val statusKey = "${newQuote.symbol}_${updatedSetup.lifecycleStatus.name}"
                if (statusKey != lastAlertedSymbolStatus) {
                    when (updatedSetup.lifecycleStatus) {
                        TradePlanLifecycleStatus.STOPPED_OUT -> {
                            lastAlertedSymbolStatus = statusKey
                            alertManager.onStopHit(newQuote.symbol, updatedSetup.stopLoss, updatedSetup.side)
                        }
                        TradePlanLifecycleStatus.TP1_REACHED -> {
                            lastAlertedSymbolStatus = statusKey
                            alertManager.onTargetHit(newQuote.symbol, 1, updatedSetup.takeProfit1, 1.5)
                        }
                        else -> Unit
                    }
                }

                val currentVerdict = _uiState.value.verdictState
                val updatedSetupPlan = if (currentVerdict != null && !currentVerdict.hasValidPlan) null else updatedSetup
                val updatedVerdict = currentVerdict?.copy(
                    plan = updatedSetupPlan,
                    livePrice = newPrice
                )

                _uiState.value = _uiState.value.copy(
                    currentQuote = newQuote,
                    exchangeQuotes = updatedExchanges,
                    tradeSetupPlan = updatedSetupPlan,
                    verdictState = updatedVerdict,
                    entryTimerStatus = if (updatedSetupPlan != null) updatedTimer else null,
                    liveTickDirection = tickDir,
                    liveTickCounter = _uiState.value.liveTickCounter + 1
                )
            }
        }
    }

    fun setSelectedExchange(exchange: CryptoExchange) {
        _uiState.value = _uiState.value.copy(
            selectedExchange = exchange,
            feedbackMessage = "Data source switched to ${exchange.displayName} (Real-time feed)"
        )
        val current = _uiState.value.currentQuote
        if (current != null && repository.isCryptoSymbol(current.symbol)) {
            loadQuoteForSymbol(
                current.symbol,
                _uiState.value.selectedTimeframe,
                _uiState.value.selectedMarketType,
                exchange
            )
        }
    }

    fun setPositionSide(side: PositionSide) {
        _uiState.value = _uiState.value.copy(
            positionSetupSide = side,
            acceptHigherRiskThisTrade = false
        )
        val quote = _uiState.value.currentQuote ?: return
        val currentTf = _uiState.value.selectedTimeframe
        val currentMarket = _uiState.value.selectedMarketType

        viewModelScope.launch {
            val perpAvail = repository.hasFuturesPerpMarket(quote.symbol, quote.assetType)
            _uiState.value = _uiState.value.copy(futuresPerpAvailableForSymbol = perpAvail)
            if (side == PositionSide.SHORT && currentMarket == TradingMarketType.SPOT && perpAvail) {
                _uiState.value = _uiState.value.copy(
                    selectedMarketType = TradingMarketType.FUTURES,
                    feedbackMessage = "SHORT routed to Futures (perp) for ${quote.symbol}"
                )
                loadQuoteForSymbol(
                    symbol = quote.symbol,
                    timeframe = currentTf,
                    marketType = TradingMarketType.FUTURES,
                    exchange = _uiState.value.selectedExchange,
                    explicitAssetType = quote.assetType
                )
                return@launch
            }

            val synth = _uiState.value.agentSynthesis
            val setup = repository.calculateTradeSetupPlan(
                symbol = quote.symbol,
                currentPrice = quote.currentPrice,
                side = side,
                leverage = _uiState.value.selectedLeverage,
                candles = quote.candles,
                dayHigh = quote.dayHigh,
                dayLow = quote.dayLow,
                timeframe = currentTf,
                assetType = quote.assetType,
                marketType = _uiState.value.selectedMarketType,
                isSynthetic = quote.isSynthetic,
                source = quote.primarySource,
                agentReports = synth?.agentReports ?: emptyList(),
                researcherDossier = synth?.researcherDossier,
                newsScannerResult = _uiState.value.newsScannerResult,
                manipulationAnalysis = synth?.manipulationAnalysis,
                tradingViewSummary = synth?.tradingViewSummary,
                futuresData = quote.futuresData,
                managerDecision = synth?.managerDecision,
                riskTeamEvaluation = synth?.riskTeamEvaluation,
                offHoursMode = _uiState.value.offHoursMode,
                futuresPerpAvailable = perpAvail,
                customStockEntryWindows = _uiState.value.stockEntryWindows,
                aiShiftPoints = synth?.aiShiftPoints ?: 0,
                aiShiftModelName = synth?.aiShiftModelName
            )
            val hasValid = setup.verdictState != SignalVerdictState.NO_TRADE && setup.isAvailable && setup.recommendedEntry > 0.0 && setup.stopLoss > 0.0
            if (hasValid) {
                _tradePlansCache[getPlanCacheKey(quote.symbol, _uiState.value.selectedMarketType, currentTf)] = setup
            }
            runDayTraderPipeline(quote, setup, _uiState.value.newsItems, synth)
        }
    }

    fun recalibrateEntryToCurrentPrice() {
        val quote = _uiState.value.currentQuote ?: return
        val currentTf = _uiState.value.selectedTimeframe
        val synth = _uiState.value.agentSynthesis
        val cacheKey = getPlanCacheKey(quote.symbol, _uiState.value.selectedMarketType, currentTf)
        _tradePlansCache.remove(cacheKey)

        val rawSetup = repository.calculateTradeSetupPlan(
            symbol = quote.symbol,
            currentPrice = quote.currentPrice,
            side = _uiState.value.positionSetupSide,
            leverage = _uiState.value.selectedLeverage,
            candles = quote.candles,
            dayHigh = quote.dayHigh,
            dayLow = quote.dayLow,
            timeframe = currentTf,
            assetType = quote.assetType,
            marketType = _uiState.value.selectedMarketType,
            isSynthetic = quote.isSynthetic,
            source = quote.primarySource,
            agentReports = synth?.agentReports ?: emptyList(),
            researcherDossier = synth?.researcherDossier,
            newsScannerResult = _uiState.value.newsScannerResult,
            manipulationAnalysis = synth?.manipulationAnalysis,
            tradingViewSummary = synth?.tradingViewSummary,
            futuresData = quote.futuresData,
            managerDecision = synth?.managerDecision,
            riskTeamEvaluation = synth?.riskTeamEvaluation,
            offHoursMode = _uiState.value.offHoursMode,
            futuresPerpAvailable = _uiState.value.futuresPerpAvailableForSymbol,
            customStockEntryWindows = _uiState.value.stockEntryWindows,
            aiShiftPoints = synth?.aiShiftPoints ?: 0,
            aiShiftModelName = synth?.aiShiftModelName
        )

        val hasValid = rawSetup.verdictState != SignalVerdictState.NO_TRADE && rawSetup.isAvailable && rawSetup.recommendedEntry > 0.0 && rawSetup.stopLoss > 0.0
        val setup = if (hasValid) {
            val isInZone = quote.currentPrice in minOf(rawSetup.entryZoneStart, rawSetup.entryZoneEnd)..maxOf(rawSetup.entryZoneStart, rawSetup.entryZoneEnd)
            val s = rawSetup.copy(
                createdAt = System.currentTimeMillis(),
                entryFilled = isInZone && rawSetup.verdictState == SignalVerdictState.TRADE,
                lifecycleStatus = if (isInZone && rawSetup.verdictState == SignalVerdictState.TRADE) TradePlanLifecycleStatus.IN_ENTRY_ZONE else TradePlanLifecycleStatus.PENDING_ENTRY
            )
            _tradePlansCache[cacheKey] = s
            s
        } else {
            rawSetup
        }
        _uiState.value = _uiState.value.copy(
            feedbackMessage = if (hasValid) "Fresh forward ${currentTf.label} setup for ${quote.symbol} calculated @ ${PriceFormatter.formatCurrency(setup.recommendedEntry)}" else "No valid setup for ${quote.symbol}: ${setup.unavailableReason.ifBlank { "No-trade condition" }}"
        )
        runDayTraderPipeline(quote, setup, _uiState.value.newsItems, synth)
    }

    fun toggleLiveTicking() {
        val newStatus = !_uiState.value.isLiveTickingActive
        _uiState.value = _uiState.value.copy(
            isLiveTickingActive = newStatus,
            feedbackMessage = if (newStatus) "Live price ticking resumed (1.5s real-time)" else "Live price ticking paused"
        )
    }

    fun toggleAlertsEnabled() {
        val newAlerts = !_uiState.value.alertsEnabled
        _uiState.value = _uiState.value.copy(
            alertsEnabled = newAlerts,
            feedbackMessage = if (newAlerts) "Trading signal price alerts activated" else "Trading signal alerts muted"
        )
    }

    fun setCustomPriceAlert(price: Double) {
        _uiState.value = _uiState.value.copy(
            customAlertThreshold = price,
            feedbackMessage = "Price alert armed for $${formatPrice(price)}"
        )
    }

    fun sendTestSignalAlert() {
        val symbol = _uiState.value.selectedSymbol
        val price = _uiState.value.currentQuote?.currentPrice ?: 67450.0
        notificationManager.sendNotification(
            notificationId = 7777,
            title = "BUY / LONG ALERT: $symbol Hit Entry",
            message = "Price reached algorithmic buy zone $${formatPrice(price * 0.998)}! Confidence: 92%. Invalidation stop at $${formatPrice(price * 0.96)}.",
            subText = "TradingSignal Trigger"
        )
        val logEntry = "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}] Manual Test Alert for $symbol: $${formatPrice(price)}"
        _uiState.value = _uiState.value.copy(
            alertNotificationsLog = listOf(logEntry) + _uiState.value.alertNotificationsLog.take(9),
            feedbackMessage = "Test push notification sent! Check your notification shade."
        )
    }

    fun toggleAdBanner() {
        val newAd = !_uiState.value.showAdBanner
        _uiState.value = _uiState.value.copy(
            showAdBanner = newAd,
            feedbackMessage = if (newAd) "Ad banner slot enabled (Google AdMob test container)" else "Ad slot dismissed (Pro Terminal Mode)"
        )
    }

    private fun formatPrice(price: Double): String {
        return PriceFormatter.formatPrice(price)
    }

    fun setScreen(screen: AppScreen) {
        _uiState.value = _uiState.value.copy(currentScreen = screen)
    }

    fun setMarketType(marketType: TradingMarketType) {
        _uiState.value = _uiState.value.copy(
            selectedMarketType = marketType,
            acceptHigherRiskThisTrade = false,
            tradeSetupPlan = null,
            verdictState = null,
            bestTimeframeSummary = null,
            agentSynthesis = null,
            replayResult = null,
            hypotheticalScenarioReplay = null,
            hypotheticalScenarioSide = null,
            dayTraderPlan = null,
            entryTimerStatus = null,
            riskEvaluation = null,
            feedbackMessage = "Switched to ${if (marketType == TradingMarketType.FUTURES) "Perpetual Futures" else "Spot Trading"} mode"
        )
        executeUnifiedQuery(_uiState.value.selectedSymbol, explicitMarketType = marketType)
    }

    private fun isCachedPlanValid(
        plan: TradeSetupPlan?,
        symbol: String,
        timeframe: Timeframe,
        marketType: TradingMarketType,
        side: PositionSide,
        livePrice: Double
    ): Boolean {
        if (plan == null) return false
        if (!plan.isAvailable) return false
        if (!plan.pairSymbol.equals(symbol, ignoreCase = true)) return false
        if (plan.timeframe != timeframe) return false
        if (plan.marketType != marketType) return false
        if (plan.side != side) return false
        if (livePrice <= 0.0) return false

        // Reject any level further than 5 x ATR(setup TF) or 25% from live price
        val atr = if (plan.atrUsed > 0.0) plan.atrUsed else (livePrice * 0.015)
        val maxDist = minOf(atr * 5.0, livePrice * 0.25)
        if (kotlin.math.abs(plan.recommendedEntry - livePrice) > maxDist) return false
        if (kotlin.math.abs(plan.stopLoss - livePrice) > maxDist * 1.5) return false
        if (kotlin.math.abs(plan.takeProfit1 - livePrice) > maxDist * 2.0) return false

        // Check if price moved past TP1 or beyond SL
        val status = plan.evaluateStatus(livePrice)
        if (status == TradePlanLifecycleStatus.TP1_REACHED ||
            status == TradePlanLifecycleStatus.TP2_REACHED ||
            status == TradePlanLifecycleStatus.TP3_REACHED ||
            status == TradePlanLifecycleStatus.STOPPED_OUT
        ) {
            return false
        }

        // Check if price is more than 3x ATR from entry zone
        val entry = plan.recommendedEntry
        if (kotlin.math.abs(livePrice - entry) > 3.0 * atr) {
            return false
        }

        // Check if plan has expired
        if (plan.expiryTimestamp > 0L && System.currentTimeMillis() > plan.expiryTimestamp) {
            return false
        }
        val maxAgeMillis = 6L * timeframe.stepMillis
        val age = System.currentTimeMillis() - plan.createdAt
        if (age > maxAgeMillis) {
            return false
        }

        // Check if invalidation price was breached
        if (plan.invalidationPrice > 0.0) {
            if (plan.side == PositionSide.LONG && livePrice <= plan.invalidationPrice) return false
            if (plan.side == PositionSide.SHORT && livePrice >= plan.invalidationPrice) return false
        }

        return true
    }

    fun setTimeframe(timeframe: Timeframe) {
        _uiState.value = _uiState.value.copy(
            selectedTimeframe = timeframe,
            acceptHigherRiskThisTrade = false,
            isLoadingQuote = true,
            tradeSetupPlan = null,
            verdictState = null,
            bestTimeframeSummary = null,
            agentSynthesis = null,
            replayResult = null,
            hypotheticalScenarioReplay = null,
            hypotheticalScenarioSide = null,
            dayTraderPlan = null,
            entryTimerStatus = null,
            riskEvaluation = null
        )
        loadQuoteForSymbol(
            symbol = _uiState.value.selectedSymbol,
            timeframe = timeframe,
            marketType = _uiState.value.selectedMarketType,
            exchange = _uiState.value.selectedExchange
        )
    }

    fun setLeverage(leverage: Int) {
        _uiState.value = _uiState.value.copy(selectedLeverage = leverage)
        val quote = _uiState.value.currentQuote ?: return
        val currentPrice = quote.currentPrice
        val liqLong = currentPrice * (1.0 - (1.0 / leverage) + 0.005)
        val liqShort = currentPrice * (1.0 + (1.0 / leverage) - 0.005)

        val updatedFutures = quote.futuresData?.copy(
            defaultLeverage = leverage,
            liquidationLong = liqLong,
            liquidationShort = liqShort
        )

        val existingPlan = _uiState.value.tradeSetupPlan
        val updatedPlan = if (existingPlan != null) {
            val recEntry = existingPlan.recommendedEntry
            val lev = leverage.coerceIn(1, 100)
            val maintenanceMargin = 0.005
            val liq = if (existingPlan.side == PositionSide.LONG) {
                (recEntry * (1.0 - (1.0 / lev) + maintenanceMargin)).coerceAtLeast(0.0)
            } else {
                recEntry * (1.0 + (1.0 / lev) - maintenanceMargin)
            }
            val maxLoss = (Math.abs(recEntry - existingPlan.stopLoss) / recEntry) * 100.0 * lev
            val expectedGain = (Math.abs(existingPlan.takeProfit1 - recEntry) / recEntry) * 100.0 * lev
            existingPlan.copy(
                recommendedLeverage = lev,
                estimatedLiquidationPrice = liq,
                maxLossPercent = maxLoss,
                expectedGainPercent = expectedGain
            )
        } else null

        val finalPlan = updatedPlan ?: _uiState.value.tradeSetupPlan
        _uiState.value = _uiState.value.copy(
            currentQuote = quote.copy(futuresData = updatedFutures),
            tradeSetupPlan = finalPlan
        )
        _uiState.value.dayTraderPlan?.let { dtPlan ->
            val profile = getMarketProfile(quote.assetType, _uiState.value.selectedMarketType)
            val reEvaluatedRisk = evaluateRiskForPlan(dtPlan, profile, quote.assetType)
            _uiState.value = _uiState.value.copy(riskEvaluation = reEvaluatedRisk)
        }
    }

    fun setLoadBalancingMode(mode: LoadBalancingMode) {
        _uiState.value = _uiState.value.copy(
            loadBalancingMode = mode,
            feedbackMessage = "Load balancing set to: ${mode.label}"
        )
    }

    fun openSavedReport(report: SavedResearchReport) {
        val isLong = !report.action.contains("SELL")
        val spread = (report.price * 0.0035).coerceAtLeast(0.01)
        val zStart = if (isLong) report.price - spread else report.price
        val zEnd = if (isLong) report.price else report.price + spread

        val savedPlan = TradeSetupPlan(
            side = if (isLong) PositionSide.LONG else PositionSide.SHORT,
            pairSymbol = report.symbol,
            currentPrice = report.price,
            entryZoneStart = zStart,
            entryZoneEnd = zEnd,
            recommendedEntry = report.price,
            recommendedLeverage = 10,
            takeProfit1 = report.targetPrice,
            takeProfit2 = if (isLong) report.targetPrice + (report.targetPrice - report.price) * 0.75 else report.targetPrice - (report.price - report.targetPrice) * 0.75,
            takeProfit3 = if (isLong) report.targetPrice + (report.targetPrice - report.price) * 1.5 else report.targetPrice - (report.price - report.targetPrice) * 1.5,
            stopLoss = report.stopLossPrice,
            estimatedLiquidationPrice = if (isLong) report.price * 0.90 else report.price * 1.10,
            riskRewardRatio = String.format(Locale.US, "1 : %.1f", kotlin.math.abs(report.targetPrice - report.price) / kotlin.math.abs(report.price - report.stopLossPrice).coerceAtLeast(0.0001)),
            maxLossPercent = kotlin.math.abs(report.price - report.stopLossPrice) / report.price * 100.0,
            expectedGainPercent = kotlin.math.abs(report.targetPrice - report.price) / report.price * 100.0,
            reasoning = "Saved Thesis (${report.companyName}): ${report.executiveSummary}",
            invalidationPrice = report.stopLossPrice,
            lifecycleStatus = if (isLong && (_uiState.value.currentQuote?.currentPrice ?: report.price) >= report.targetPrice) TradePlanLifecycleStatus.TP1_REACHED else TradePlanLifecycleStatus.PENDING_ENTRY,
            isLocked = true,
            timeframe = _uiState.value.selectedTimeframe,
            isAvailable = true,
            unavailableReason = "",
            confluenceScore = report.confidenceScore,
            contributingNodes = listOf("Saved Room Database Thesis", report.companyName, "Saved Conf: ${report.confidenceScore}%"),
            whyThisEntry = "Loaded from Saved Thesis. Saved Entry: $${formatPrice(report.price)}, Saved Target: $${formatPrice(report.targetPrice)}, Saved Stop: $${formatPrice(report.stopLossPrice)}.",
            entryType = EntryOrderType.LIMIT
        )

        _uiState.value = _uiState.value.copy(
            selectedSymbol = report.symbol,
            isViewingSavedPlan = true,
            viewingSavedReport = report,
            tradeSetupPlan = savedPlan,
            positionSetupSide = if (isLong) PositionSide.LONG else PositionSide.SHORT,
            feedbackMessage = "Loaded saved entry for ${report.symbol} (Target: $${formatPrice(report.targetPrice)})"
        )

        loadQuoteForSymbol(
            symbol = report.symbol,
            timeframe = _uiState.value.selectedTimeframe,
            marketType = _uiState.value.selectedMarketType,
            exchange = _uiState.value.selectedExchange
        )
    }

    fun switchToLiveSetup() {
        val sym = _uiState.value.selectedSymbol
        _uiState.value = _uiState.value.copy(
            isViewingSavedPlan = false,
            viewingSavedReport = null,
            isLoadingQuote = true,
            tradeSetupPlan = null,
            feedbackMessage = "Recalculating fresh live setup for $sym..."
        )
        executeUnifiedQuery(sym)
    }

    fun selectSymbol(symbol: String, explicitAssetType: AssetType? = null) {
        val clean = symbol.trim().uppercase()
        if (clean.isBlank()) return
        val isCrypto = explicitAssetType?.let { it == AssetType.CRYPTO } ?: repository.isCryptoSymbol(clean)
        val targetMarketType = if (isCrypto) {
            _uiState.value.selectedMarketType
        } else {
            TradingMarketType.SPOT
        }
        val scopedAlerts = alertManager.alerts.value.filter {
            it.symbol.equals(clean, ignoreCase = true) || it.symbol.equals("MACRO", ignoreCase = true)
        }
        _uiState.value = _uiState.value.copy(
            selectedSymbol = clean,
            selectedMarketType = targetMarketType,
            acceptHigherRiskThisTrade = false,
            stockFundamentals = null,
            isLoadingQuote = true,
            isViewingSavedPlan = false,
            viewingSavedReport = null,
            tradeSetupPlan = null,
            verdictState = null,
            bestTimeframeSummary = null,
            agentSynthesis = null,
            newsScannerResult = null,
            newsItems = emptyList(),
            exchangeQuotes = emptyList(),
            replayResult = null,
            hypotheticalScenarioReplay = null,
            hypotheticalScenarioSide = null,
            dayTraderPlan = null,
            entryTimerStatus = null,
            riskEvaluation = null,
            dayTraderAlerts = scopedAlerts,
            taggedHeadlines = emptyList(),
            eventRiskAssessment = null,
            cryptoFundamentals = null
        )
        executeUnifiedQuery(clean, explicitMarketType = targetMarketType, explicitAssetType = explicitAssetType)
    }

    private fun loadQuoteForSymbol(
        symbol: String,
        timeframe: Timeframe,
        marketType: TradingMarketType,
        exchange: CryptoExchange = _uiState.value.selectedExchange,
        explicitAssetType: AssetType? = null
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoadingQuote = true
            )

            val quoteResult = repository.getQuote(symbol, timeframe, marketType, exchange, explicitAssetType)
            val initialQuote = quoteResult.getOrNull()

            // Guard against out-of-order async responses
            if (symbol != _uiState.value.selectedSymbol ||
                timeframe != _uiState.value.selectedTimeframe ||
                marketType != _uiState.value.selectedMarketType ||
                exchange != _uiState.value.selectedExchange
            ) {
                return@launch
            }

            if (initialQuote != null) {
                val perpAvailable = repository.hasFuturesPerpMarket(initialQuote.symbol, initialQuote.assetType)
                val resolvedAssetTypeForNews = initialQuote.assetType
                val news = repository.getNews(symbol, resolvedAssetTypeForNews)
                val scannerResult = repository.scanNewsWithGemini(symbol, initialQuote.assetType, broadcastAllModels = false)
                val synthesis = repository.runAgentAnalysis(
                    quote = initialQuote,
                    news = news,
                    scannerResult = scannerResult,
                    aiEnrichmentEnabled = _uiState.value.aiEnrichmentEnabled,
                    balancingMode = _uiState.value.loadBalancingMode
                )

                val targetSide = when {
                    synthesis.action == com.example.domain.model.TradingAction.SELL || synthesis.action == com.example.domain.model.TradingAction.STRONG_SELL -> PositionSide.SHORT
                    synthesis.action == com.example.domain.model.TradingAction.BUY || synthesis.action == com.example.domain.model.TradingAction.STRONG_BUY -> PositionSide.LONG
                    else -> _uiState.value.positionSetupSide
                }

                // Patch A #1: Spot + SHORT routes to Futures perp feed cleanly
                val routedMarket = if (targetSide == PositionSide.SHORT && marketType == TradingMarketType.SPOT && perpAvailable) {
                    TradingMarketType.FUTURES
                } else {
                    marketType
                }
                val quote = if (routedMarket != marketType && initialQuote.assetType == AssetType.CRYPTO) {
                    repository.getQuote(symbol, timeframe, routedMarket, exchange, initialQuote.assetType).getOrNull() ?: initialQuote
                } else {
                    initialQuote
                }
                val exchanges = repository.fetchRealExchangeQuotes(symbol, quote.assetType, routedMarket, quote.currentPrice)

                val currentTf = timeframe
                val cacheKey = getPlanCacheKey(symbol, routedMarket, currentTf)
                val cachedPlan = _tradePlansCache[cacheKey]

                val setup = if (_uiState.value.isViewingSavedPlan && _uiState.value.tradeSetupPlan != null) {
                    val existing = _uiState.value.tradeSetupPlan!!
                    val newStatus = existing.evaluateWithCandles(quote.currentPrice, quote.candles)
                    existing.copy(currentPrice = quote.currentPrice, lifecycleStatus = newStatus)
                } else if (isCachedPlanValid(cachedPlan, symbol, currentTf, routedMarket, targetSide, quote.currentPrice)) {
                    val updatedStatus = cachedPlan!!.evaluateWithCandles(quote.currentPrice, quote.candles)
                    val updated = cachedPlan.copy(
                        currentPrice = quote.currentPrice,
                        lifecycleStatus = updatedStatus
                    )
                    _tradePlansCache[cacheKey] = updated
                    updated
                } else {
                    val fresh = repository.calculateTradeSetupPlan(
                        symbol = symbol,
                        currentPrice = quote.currentPrice,
                        side = targetSide,
                        leverage = _uiState.value.selectedLeverage,
                        candles = quote.candles,
                        dayHigh = quote.dayHigh,
                        dayLow = quote.dayLow,
                        timeframe = currentTf,
                        assetType = quote.assetType,
                        marketType = marketType,
                        isSynthetic = quote.isSynthetic,
                        source = quote.primarySource,
                        agentReports = synthesis.agentReports,
                        researcherDossier = synthesis.researcherDossier,
                        newsScannerResult = scannerResult,
                        manipulationAnalysis = synthesis.manipulationAnalysis,
                        tradingViewSummary = synthesis.tradingViewSummary,
                        futuresData = quote.futuresData,
                        managerDecision = synthesis.managerDecision,
                        riskTeamEvaluation = synthesis.riskTeamEvaluation,
                        offHoursMode = _uiState.value.offHoursMode,
                        futuresPerpAvailable = perpAvailable,
                        customStockEntryWindows = _uiState.value.stockEntryWindows,
                        aiShiftPoints = synthesis.aiShiftPoints,
                        aiShiftModelName = synthesis.aiShiftModelName
                    )
                    if (fresh.verdictState != SignalVerdictState.NO_TRADE && fresh.isAvailable && fresh.recommendedEntry > 0.0) {
                        _tradePlansCache[cacheKey] = fresh
                    }
                    fresh
                }

                _uiState.value = _uiState.value.copy(
                    selectedMarketType = routedMarket,
                    futuresPerpAvailableForSymbol = perpAvailable,
                    positionSetupSide = targetSide,
                    currentQuote = quote,
                    newsItems = news,
                    newsScannerResult = scannerResult,
                    agentSynthesis = synthesis,
                    exchangeQuotes = exchanges,
                    isLoadingQuote = false
                )
                if (quote.assetType == AssetType.CRYPTO) {
                    klineWsClient.subscribe(quote.symbol, routedMarket)
                } else {
                    klineWsClient.disconnect()
                }
                runDayTraderPipeline(quote, setup, news, synthesis)
                refreshMasterVerdict(quote = quote, marketType = routedMarket)

                // Async best-timeframe triplet evaluation across 5m / 15m / 1h / 4h / 1D
                viewModelScope.launch(Dispatchers.Default) {
                    try {
                        val tfQuotes = repository.getQuotesForTriplet(
                            symbol = quote.symbol,
                            marketType = routedMarket,
                            exchange = exchange,
                            explicitAssetType = quote.assetType
                        )
                        val quotesMap = tfQuotes.toMutableMap().apply {
                            putIfAbsent(timeframe, quote)
                        }
                        val summary = SignalEngineV2.evaluateTimeframeTriplets(
                            symbol = quote.symbol,
                            chartTimeframe = timeframe,
                            quotesByTf = quotesMap,
                            marketType = routedMarket,
                            assetType = quote.assetType,
                            leverage = _uiState.value.verdictState?.plan?.recommendedLeverage ?: 10,
                            offHoursMode = _uiState.value.offHoursMode,
                            futuresPerpAvailable = perpAvailable,
                            customStockEntryWindows = _uiState.value.stockEntryWindows
                        )
                        if (_uiState.value.selectedSymbol.equals(quote.symbol, ignoreCase = true) &&
                            _uiState.value.selectedTimeframe == timeframe
                        ) {
                            val updatedVs = _uiState.value.verdictState?.copy(bestTimeframeSummary = summary)
                            _uiState.value = _uiState.value.copy(
                                bestTimeframeSummary = summary,
                                verdictState = updatedVs
                            )
                        }
                    } catch (_: Exception) {
                    }
                }
            } else {
                refreshDiagnostics()
                _uiState.value = _uiState.value.copy(
                    isLoadingQuote = false,
                    feedbackMessage = "Failed to load data for $symbol"
                )
            }
        }
    }

    /**
     * Executes the Tauric multi-agent research analysis by distributing the reasoning
     * load across multiple AI models & API keys according to the Load Balancing Mode.
     * All agents & nodes scan the query simultaneously to produce a single unified consensus result.
     */
    fun runDeepAgentAnalysis() {
        val quote = _uiState.value.currentQuote ?: return
        val models = _uiState.value.aiModels
        val mode = _uiState.value.loadBalancingMode

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRunningAgents = true,
                tradeSetupPlan = _uiState.value.tradeSetupPlan?.copy(
                    lifecycleStatus = TradePlanLifecycleStatus.ANALYZING,
                    isAvailable = false,
                    unavailableReason = "Analyzing agent pipeline across all nodes..."
                ) ?: TradeSetupPlan.createAnalyzing(
                    symbol = quote.symbol,
                    timeframe = _uiState.value.selectedTimeframe,
                    side = _uiState.value.positionSetupSide,
                    currentPrice = quote.currentPrice
                )
            )
            val telemetry = mutableListOf<Pair<AgentRole, String>>()

            // 0. NewsScannerAgent: Scrapes real financial headlines and passes them into Gemini / all models simultaneously
            _uiState.value = _uiState.value.copy(
                agentRunningPhase = "NewsScannerAgent: Scraping financial headlines & querying Gemini model context..."
            )
            val scannerResult = repository.newsScannerAgent.scanHeadlinesAndAnalyze(
                query = quote.symbol,
                assetType = quote.assetType,
                models = models,
                dispatcher = repository.aiDispatcher,
                broadcastAllModels = true
            )
            val scannedModelsStr = scannerResult.modelsScanned.joinToString(", ")
            telemetry.add(AgentRole.NEWS_SCANNER to "Scanned ${scannerResult.headlines.size} headlines via $scannedModelsStr (${if (scannerResult.priceImpactPercent >= 0) "+" else ""}${String.format(Locale.US, "%.1f", scannerResult.priceImpactPercent)}% impulse)")
            delay(200)

            // 1. Fundamental Analyst
            val fModel = getTargetModelForRole(AgentRole.FUNDAMENTAL, models, mode)
            _uiState.value = _uiState.value.copy(
                agentRunningPhase = "Agent (Fundamental): Dispatched to ${fModel.provider.displayName} [${fModel.modelName}]..."
            )
            val fRes = repository.aiDispatcher.dispatchPrompt(AgentRole.FUNDAMENTAL, "Audit valuation for ${quote.symbol}", models, mode)
            telemetry.add(AgentRole.FUNDAMENTAL to "${fRes.providerName} (${fRes.modelName}) - ${fRes.latencyMs}ms")
            delay(200)

            // 2. Technical Analyst
            val tModel = getTargetModelForRole(AgentRole.TECHNICAL, models, mode)
            _uiState.value = _uiState.value.copy(
                agentRunningPhase = "Agent (Technical): Dispatched to ${tModel.provider.displayName} [${tModel.modelName}]..."
            )
            val tRes = repository.aiDispatcher.dispatchPrompt(AgentRole.TECHNICAL, "Calculate RSI & Moving Averages for ${quote.symbol}", models, mode)
            telemetry.add(AgentRole.TECHNICAL to "${tRes.providerName} (${tRes.modelName}) - ${tRes.latencyMs}ms")
            delay(200)

            // 3. Sentiment Analyst
            val sModel = getTargetModelForRole(AgentRole.SENTIMENT, models, mode)
            _uiState.value = _uiState.value.copy(
                agentRunningPhase = "Agent (Sentiment): Merging NewsScanner headlines & Bloomberg Tape for ${quote.symbol}..."
            )
            val sRes = repository.aiDispatcher.dispatchPrompt(AgentRole.SENTIMENT, "Scan news sentiment and calibrate catalyst impact for ${quote.symbol}", models, mode)
            telemetry.add(AgentRole.SENTIMENT to "${sRes.providerName} (${sRes.modelName}) - ${sRes.latencyMs}ms")
            delay(200)

            // 4. Risk Manager
            val rModel = getTargetModelForRole(AgentRole.RISK_MANAGEMENT, models, mode)
            _uiState.value = _uiState.value.copy(
                agentRunningPhase = "Agent (Risk): Calculating stop-loss & position size limits for ${quote.symbol}..."
            )
            val rRes = repository.aiDispatcher.dispatchPrompt(AgentRole.RISK_MANAGEMENT, "Size stop-loss and liquidation limits for ${quote.symbol}", models, mode)
            telemetry.add(AgentRole.RISK_MANAGEMENT to "${rRes.providerName} (${rRes.modelName}) - ${rRes.latencyMs}ms")
            delay(200)

            // 5. Chief Strategist & Price Change Synthesis
            val cModel = getTargetModelForRole(AgentRole.CHIEF_STRATEGIST, models, mode)
            _uiState.value = _uiState.value.copy(
                agentRunningPhase = "Chief Strategist: Synthesizing Unified Consensus across all nodes..."
            )
            val cRes = repository.aiDispatcher.dispatchPrompt(AgentRole.CHIEF_STRATEGIST, "Synthesize consensus price change decision combining technicals and NewsScanner catalysts for ${quote.symbol}", models, mode)
            telemetry.add(AgentRole.CHIEF_STRATEGIST to "${cRes.providerName} (${cRes.modelName}) - ${cRes.latencyMs}ms")
            delay(200)

            val extraNodes = telemetry.map { "${it.first.title}: ${it.second}" }
            val news = if (_uiState.value.newsItems.isNotEmpty()) _uiState.value.newsItems else repository.getNews(quote.symbol, quote.assetType)
            val synthesis = repository.runAgentAnalysis(quote, news, scannerResult, extraNodes)
            val pctStr = "${if (synthesis.predictedPriceChangePercent >= 0) "+" else ""}${String.format(Locale.US, "%.1f", synthesis.predictedPriceChangePercent)}%"

            val currentTf = _uiState.value.selectedTimeframe
            val targetSide = when {
                synthesis.action == com.example.domain.model.TradingAction.SELL || synthesis.action == com.example.domain.model.TradingAction.STRONG_SELL -> PositionSide.SHORT
                synthesis.action == com.example.domain.model.TradingAction.BUY || synthesis.action == com.example.domain.model.TradingAction.STRONG_BUY -> PositionSide.LONG
                else -> _uiState.value.positionSetupSide
            }
            val freshPlan = repository.calculateTradeSetupPlan(
                symbol = quote.symbol,
                currentPrice = quote.currentPrice,
                side = targetSide,
                leverage = _uiState.value.selectedLeverage,
                candles = quote.candles,
                dayHigh = quote.dayHigh,
                dayLow = quote.dayLow,
                timeframe = currentTf,
                assetType = quote.assetType,
                marketType = _uiState.value.selectedMarketType,
                isSynthetic = quote.isSynthetic,
                source = quote.primarySource,
                agentReports = synthesis.agentReports,
                researcherDossier = synthesis.researcherDossier,
                newsScannerResult = scannerResult,
                manipulationAnalysis = synthesis.manipulationAnalysis,
                tradingViewSummary = synthesis.tradingViewSummary,
                futuresData = quote.futuresData,
                managerDecision = synthesis.managerDecision,
                riskTeamEvaluation = synthesis.riskTeamEvaluation
            )
            val hasValid = freshPlan.verdictState != SignalVerdictState.NO_TRADE && freshPlan.isAvailable && freshPlan.recommendedEntry > 0.0 && freshPlan.stopLoss > 0.0
            if (hasValid) {
                _tradePlansCache[getPlanCacheKey(quote.symbol, _uiState.value.selectedMarketType, currentTf)] = freshPlan
            }

            _uiState.value = _uiState.value.copy(
                positionSetupSide = targetSide,
                agentSynthesis = synthesis,
                newsScannerResult = scannerResult,
                isRunningAgents = false,
                agentRunningPhase = "",
                lastAiDispatchTelemetry = telemetry,
                feedbackMessage = "Unified consensus locked across all agents & nodes! ${synthesis.action.label} ($pctStr)."
            )
            runDayTraderPipeline(quote, freshPlan, news, synthesis)
        }
    }

    private fun getTargetModelForRole(role: AgentRole, models: List<AiModelConfig>, mode: LoadBalancingMode): AiModelConfig {
        val enabled = models.filter { it.isEnabled }
        if (enabled.isEmpty()) return AiModelConfig("sim", com.example.domain.model.AiProvider.SIMULATION, "tauric-quant-local", "", null)
        return when (mode) {
            LoadBalancingMode.TOKEN_AVAILABILITY_BALANCED -> {
                com.example.domain.ai.MultiAiDispatcher.rankModelsByTokenAndAvailability(enabled, 150L).firstOrNull() ?: enabled.first()
            }
            LoadBalancingMode.AUTO_CASCADE_FREE_TIER -> {
                com.example.domain.ai.MultiAiDispatcher.rankModelsForFreeTierCascade(enabled).firstOrNull() ?: enabled.first()
            }
            LoadBalancingMode.SPECIALIZED_ROLE -> enabled.find { it.assignedRole == role } ?: enabled.first()
            LoadBalancingMode.ROUND_ROBIN_PARALLEL -> enabled.random()
        }
    }

    fun setScanTimeframe(timeframe: Timeframe) {
        _uiState.value = _uiState.value.copy(scanTimeframe = timeframe)
        scanCrypto(_uiState.value.selectedMarketType, timeframe)
    }

    fun scanCrypto(
        marketType: TradingMarketType = _uiState.value.selectedMarketType,
        timeframe: Timeframe = _uiState.value.scanTimeframe
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanningCrypto = true, scanTimeframe = timeframe)
            val list = repository.scanCryptoMarkets(marketType, timeframe)
            _uiState.value = _uiState.value.copy(
                cryptoScanList = list,
                isScanningCrypto = false,
                feedbackMessage = "Scanned ${list.size} ${marketType.label} pairs on ${timeframe.label} timeframe with buy/sell price targets"
            )
        }
    }

    fun fetchBloombergFeed(query: String = "markets") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(feedbackMessage = "Fetching Bloomberg Intelligence and Markets Tape...")
            val bNews = repository.getBloombergNews(query)
            _uiState.value = _uiState.value.copy(
                newsItems = bNews,
                feedbackMessage = "Collected ${bNews.size} Bloomberg Intelligence & Wire articles"
            )
        }
    }

    fun saveCurrentReport() {
        val quote = _uiState.value.currentQuote ?: return
        val synthesis = _uiState.value.agentSynthesis ?: return

        viewModelScope.launch {
            repository.saveReport(quote, synthesis)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Report for ${quote.symbol} saved to local database!"
            )
        }
    }

    fun deleteSavedReport(id: Long) {
        viewModelScope.launch {
            repository.deleteReport(id)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Research report deleted"
            )
        }
    }

    fun toggleWatchlist(symbol: String) {
        val clean = symbol.trim().uppercase()
        val quote = _uiState.value.currentQuote
        val inWatchlist = _uiState.value.watchlist.any { it.symbol == clean }

        viewModelScope.launch {
            if (inWatchlist) {
                repository.removeFromWatchlist(clean)
                _uiState.value = _uiState.value.copy(
                    feedbackMessage = "Removed $clean from Favorites",
                    selectedFavoriteSymbol = if (_uiState.value.selectedFavoriteSymbol == clean) null else _uiState.value.selectedFavoriteSymbol
                )
            } else {
                val name = quote?.name ?: clean
                val type = quote?.assetType ?: if (repository.isCryptoSymbol(clean)) com.example.domain.model.AssetType.CRYPTO else com.example.domain.model.AssetType.STOCK
                repository.addToWatchlist(clean, name, type)
                _uiState.value = _uiState.value.copy(
                    feedbackMessage = "Added $clean to Favorites"
                )
            }
        }
    }

    /**
     * Selects a stock or crypto from the Favorites list, loads its live quote,
     * scans news headlines via NewsScannerAgent and Gemini, and generates the unified final result
     * so the user can immediately view data right on the screen.
     */
    fun selectFavorite(symbol: String) {
        val clean = symbol.trim().uppercase(Locale.US)
        _uiState.value = _uiState.value.copy(
            selectedFavoriteSymbol = clean,
            isLoadingFavorite = true,
            selectedSymbol = clean
        )

        viewModelScope.launch {
            val isCrypto = repository.isCryptoSymbol(clean)
            val marketType = if (isCrypto) TradingMarketType.FUTURES else TradingMarketType.SPOT
            val quoteRes = repository.getQuote(clean, _uiState.value.selectedTimeframe, marketType)
            val quote = quoteRes.getOrNull()

            if (quote != null) {
                val news = repository.getNews(clean, quote.assetType)
                val scannerResult = repository.newsScannerAgent.scanHeadlinesAndAnalyze(
                    query = clean,
                    assetType = quote.assetType,
                    models = _uiState.value.aiModels,
                    dispatcher = repository.aiDispatcher,
                    broadcastAllModels = false
                )
                val synthesis = repository.runAgentAnalysis(quote, news, scannerResult)
                val exchanges = repository.fetchRealExchangeQuotes(clean, quote.assetType, marketType, quote.currentPrice)
                val currentTf = _uiState.value.selectedTimeframe
                val cacheKey = getPlanCacheKey(clean, _uiState.value.selectedMarketType, currentTf)
                val existingPlan = if (_uiState.value.tradeSetupPlan?.pairSymbol.equals(clean, ignoreCase = true) &&
                    _uiState.value.tradeSetupPlan?.timeframe == currentTf
                ) {
                    _uiState.value.tradeSetupPlan
                } else {
                    _tradePlansCache[cacheKey]
                }
                val hasExistingPlan = existingPlan != null && isCachedPlanValid(
                    existingPlan,
                    clean,
                    currentTf,
                    _uiState.value.selectedMarketType,
                    existingPlan.side,
                    quote.currentPrice
                )

                val targetSide = if (hasExistingPlan) {
                    existingPlan.side
                } else when {
                    synthesis.action == com.example.domain.model.TradingAction.SELL || synthesis.action == com.example.domain.model.TradingAction.STRONG_SELL -> PositionSide.SHORT
                    synthesis.action == com.example.domain.model.TradingAction.BUY || synthesis.action == com.example.domain.model.TradingAction.STRONG_BUY -> PositionSide.LONG
                    else -> _uiState.value.positionSetupSide
                }

                val setup = if (hasExistingPlan) {
                    val updatedStatus = existingPlan.evaluateWithCandles(quote.currentPrice, quote.candles)
                    val updated = existingPlan.copy(
                        currentPrice = quote.currentPrice,
                        lifecycleStatus = updatedStatus
                    )
                    _tradePlansCache[cacheKey] = updated
                    updated
                } else {
                    val fresh = repository.calculateTradeSetupPlan(
                        symbol = clean,
                        currentPrice = quote.currentPrice,
                        side = targetSide,
                        leverage = _uiState.value.selectedLeverage,
                        candles = quote.candles,
                        dayHigh = quote.dayHigh,
                        dayLow = quote.dayLow,
                        timeframe = currentTf,
                        assetType = quote.assetType,
                        marketType = _uiState.value.selectedMarketType,
                        isSynthetic = quote.isSynthetic,
                        source = quote.primarySource,
                        agentReports = synthesis.agentReports,
                        researcherDossier = synthesis.researcherDossier,
                        newsScannerResult = scannerResult,
                        manipulationAnalysis = synthesis.manipulationAnalysis,
                        tradingViewSummary = synthesis.tradingViewSummary,
                        futuresData = quote.futuresData,
                        managerDecision = synthesis.managerDecision,
                        riskTeamEvaluation = synthesis.riskTeamEvaluation
                    )
                    _tradePlansCache[cacheKey] = fresh
                    fresh
                }

                _uiState.value = _uiState.value.copy(
                    positionSetupSide = targetSide,
                    currentQuote = quote,
                    favoriteQuote = quote,
                    favoriteSynthesis = synthesis,
                    agentSynthesis = synthesis,
                    newsScannerResult = scannerResult,
                    newsItems = news,
                    exchangeQuotes = exchanges,
                    tradeSetupPlan = setup,
                    isLoadingFavorite = false,
                    feedbackMessage = "Loaded data & unified consensus for $clean"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoadingFavorite = false,
                    feedbackMessage = "Could not load data for $clean"
                )
            }
        }
    }

    fun addToFavorites(symbol: String, name: String = "", assetType: AssetType = AssetType.STOCK) {
        val clean = symbol.trim().uppercase(Locale.US)
        if (clean.isBlank()) return
        val finalName = if (name.isNotBlank()) name else {
            _uiState.value.currentQuote?.takeIf { it.symbol == clean }?.name ?: clean
        }
        val finalType = if (repository.isCryptoSymbol(clean)) AssetType.CRYPTO else assetType

        viewModelScope.launch {
            repository.addToWatchlist(clean, finalName, finalType)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Added $clean ($finalName) to Favorites"
            )
        }
    }

    fun removeFromFavorites(symbol: String) {
        val clean = symbol.trim().uppercase(Locale.US)
        viewModelScope.launch {
            repository.removeFromWatchlist(clean)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Removed $clean from Favorites",
                selectedFavoriteSymbol = if (_uiState.value.selectedFavoriteSymbol == clean) null else _uiState.value.selectedFavoriteSymbol
            )
        }
    }

    /**
     * Executes EVERYTHING in ONE single unified query:
     * 1. Fetches real-time price quote, exchange feeds, and futures/spot metrics
     * 2. Runs NewsScannerAgent to scrape financial headlines from Bloomberg Wire, Google News RSS, Yahoo Finance, Reuters
     * 3. Passes headlines into Gemini model context & broadcasts to all active fleet models
     * 4. Scans across ALL agent and node disciplines (NewsScanner, Fundamental, Technical, Sentiment, Risk, Chief Strategist, Crypto Scanner)
     * 5. Produces the single unified consensus result and updates all state in one atomic step.
     */
    fun executeUnifiedQuery(
        query: String,
        explicitMarketType: TradingMarketType? = null,
        explicitAssetType: AssetType? = null
    ) {
        val clean = query.trim().uppercase(Locale.US)
        if (clean.isBlank()) return

        val isCrypto = explicitAssetType?.let { it == AssetType.CRYPTO } ?: repository.isCryptoSymbol(clean)
        val targetMarketType = if (isCrypto) {
            explicitMarketType ?: _uiState.value.selectedMarketType
        } else {
            TradingMarketType.SPOT
        }

        val scopedAlerts = alertManager.alerts.value.filter {
            it.symbol.equals(clean, ignoreCase = true) || it.symbol.equals("MACRO", ignoreCase = true)
        }
        _uiState.value = _uiState.value.copy(
            selectedSymbol = clean,
            selectedMarketType = targetMarketType,
            acceptHigherRiskThisTrade = false,
            stockFundamentals = null,
            isLoadingQuote = true,
            isRunningAgents = true,
            tradeSetupPlan = null,
            verdictState = null,
            bestTimeframeSummary = null,
            agentSynthesis = null,
            replayResult = null,
            hypotheticalScenarioReplay = null,
            hypotheticalScenarioSide = null,
            dayTraderPlan = null,
            entryTimerStatus = null,
            riskEvaluation = null,
            dayTraderAlerts = scopedAlerts,
            taggedHeadlines = emptyList(),
            eventRiskAssessment = null,
            cryptoFundamentals = null,
            agentRunningPhase = "Initiating Unified Scan: Scanning $clean (${if (isCrypto) "Crypto" else "Stock"}) through all agents & nodes..."
        )

        viewModelScope.launch {
            // 1. Fetch live quote & exchange data
            _uiState.value = _uiState.value.copy(
                agentRunningPhase = "Connecting to market exchange & order book feeds for $clean..."
            )
            val quoteResult = repository.getQuote(
                clean,
                _uiState.value.selectedTimeframe,
                targetMarketType,
                _uiState.value.selectedExchange,
                explicitAssetType = if (isCrypto) AssetType.CRYPTO else AssetType.STOCK
            )
            val initialQuote = quoteResult.getOrNull()

            if (initialQuote == null) {
                refreshDiagnostics()
                _uiState.value = _uiState.value.copy(
                    isLoadingQuote = false,
                    isRunningAgents = false,
                    agentRunningPhase = "",
                    feedbackMessage = "Could not fetch market data for $clean"
                )
                return@launch
            }

            val perpAvailable = repository.hasFuturesPerpMarket(initialQuote.symbol, initialQuote.assetType)
            val news = repository.getNews(clean)

            // 2. NewsScannerAgent: Scrapes real financial headlines and passes into Gemini context & all models
            _uiState.value = _uiState.value.copy(
                agentRunningPhase = "NewsScannerAgent: Scraping Bloomberg, Reuters & Google News for $clean..."
            )
            val scannerResult = repository.newsScannerAgent.scanHeadlinesAndAnalyze(
                query = clean,
                assetType = initialQuote.assetType,
                models = _uiState.value.aiModels,
                dispatcher = repository.aiDispatcher,
                broadcastAllModels = true
            )

            // 3. Scan through all agent nodes simultaneously
            _uiState.value = _uiState.value.copy(
                agentRunningPhase = "Executing multi-agent consensus, TradingView technical studies & SMC analysis..."
            )
            val synthesis = repository.runAgentAnalysis(
                quote = initialQuote,
                news = news,
                scannerResult = scannerResult,
                aiEnrichmentEnabled = _uiState.value.aiEnrichmentEnabled,
                balancingMode = _uiState.value.loadBalancingMode
            )

            val currentTf = _uiState.value.selectedTimeframe
            val targetSide = when {
                synthesis.action == com.example.domain.model.TradingAction.SELL || synthesis.action == com.example.domain.model.TradingAction.STRONG_SELL -> PositionSide.SHORT
                synthesis.action == com.example.domain.model.TradingAction.BUY || synthesis.action == com.example.domain.model.TradingAction.STRONG_BUY -> PositionSide.LONG
                else -> _uiState.value.positionSetupSide
            }

            // Patch A #1: Spot + SHORT routes to Futures perp feed cleanly
            val routedMarket = if (targetSide == PositionSide.SHORT && targetMarketType == TradingMarketType.SPOT && perpAvailable) {
                TradingMarketType.FUTURES
            } else {
                targetMarketType
            }
            val quote = if (routedMarket != targetMarketType && initialQuote.assetType == AssetType.CRYPTO) {
                repository.getQuote(clean, currentTf, routedMarket, _uiState.value.selectedExchange, initialQuote.assetType).getOrNull() ?: initialQuote
            } else {
                initialQuote
            }
            val exchanges = repository.fetchRealExchangeQuotes(clean, quote.assetType, routedMarket, quote.currentPrice)

            val cacheKey = getPlanCacheKey(clean, routedMarket, currentTf)
            val cachedPlan = _tradePlansCache[cacheKey]

            val setup = if (_uiState.value.isViewingSavedPlan && _uiState.value.tradeSetupPlan != null) {
                val existing = _uiState.value.tradeSetupPlan!!
                val newStatus = existing.evaluateWithCandles(quote.currentPrice, quote.candles)
                existing.copy(currentPrice = quote.currentPrice, lifecycleStatus = newStatus)
            } else if (isCachedPlanValid(cachedPlan, clean, currentTf, routedMarket, targetSide, quote.currentPrice)) {
                val updatedStatus = cachedPlan!!.evaluateWithCandles(quote.currentPrice, quote.candles)
                val updated = cachedPlan.copy(
                    currentPrice = quote.currentPrice,
                    lifecycleStatus = updatedStatus
                )
                _tradePlansCache[cacheKey] = updated
                updated
            } else {
                val fresh = repository.calculateTradeSetupPlan(
                    symbol = clean,
                    currentPrice = quote.currentPrice,
                    side = targetSide,
                    leverage = _uiState.value.selectedLeverage,
                    candles = quote.candles,
                    dayHigh = quote.dayHigh,
                    dayLow = quote.dayLow,
                    timeframe = currentTf,
                    assetType = quote.assetType,
                    marketType = targetMarketType,
                    isSynthetic = quote.isSynthetic,
                    source = quote.primarySource,
                    agentReports = synthesis.agentReports,
                    researcherDossier = synthesis.researcherDossier,
                    newsScannerResult = scannerResult,
                    manipulationAnalysis = synthesis.manipulationAnalysis,
                    tradingViewSummary = synthesis.tradingViewSummary,
                    futuresData = quote.futuresData,
                    managerDecision = synthesis.managerDecision,
                    riskTeamEvaluation = synthesis.riskTeamEvaluation,
                    offHoursMode = _uiState.value.offHoursMode,
                    futuresPerpAvailable = perpAvailable,
                    customStockEntryWindows = _uiState.value.stockEntryWindows,
                    aiShiftPoints = synthesis.aiShiftPoints,
                    aiShiftModelName = synthesis.aiShiftModelName
                )
                _tradePlansCache[cacheKey] = fresh
                fresh
            }

            // 4. Execute strategy backtest simulation node in the background
            val backtest = try {
                TradingAgentsEngine.runHistoricalBacktest(
                    quote = quote,
                    config = _uiState.value.backtestConfig,
                    timeframe = _uiState.value.selectedTimeframe
                )
            } catch (e: Exception) {
                null
            }

            // 5. Update UI state with complete unified results
            _uiState.value = _uiState.value.copy(
                selectedMarketType = routedMarket,
                futuresPerpAvailableForSymbol = perpAvailable,
                positionSetupSide = targetSide,
                currentQuote = quote,
                newsItems = news,
                newsScannerResult = scannerResult,
                agentSynthesis = synthesis,
                exchangeQuotes = exchanges,
                backtestResult = backtest,
                isLoadingQuote = false,
                isRunningAgents = false,
                agentRunningPhase = "",
                feedbackMessage = "Scan Complete: ${synthesis.verdictState.label} on $clean"
            )
            if (quote.assetType == AssetType.CRYPTO) {
                klineWsClient.subscribe(quote.symbol, routedMarket)
            } else {
                klineWsClient.disconnect()
            }
            runDayTraderPipeline(quote, setup, news, synthesis)
            refreshMasterVerdict(quote = quote, marketType = routedMarket, forceRefresh = true)

            // Async best-timeframe triplet evaluation ("Best now" summary for context TFs & WAIT plans)
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    val tfQuotes = repository.getQuotesForTriplet(
                        symbol = quote.symbol,
                        marketType = routedMarket,
                        exchange = _uiState.value.selectedExchange,
                        explicitAssetType = quote.assetType
                    )
                    val quotesMap = tfQuotes.toMutableMap().apply {
                        putIfAbsent(currentTf, quote)
                    }
                    val summary = SignalEngineV2.evaluateTimeframeTriplets(
                        symbol = quote.symbol,
                        chartTimeframe = currentTf,
                        quotesByTf = quotesMap,
                        marketType = routedMarket,
                        assetType = quote.assetType,
                        leverage = _uiState.value.verdictState?.plan?.recommendedLeverage ?: 10,
                        offHoursMode = _uiState.value.offHoursMode,
                        futuresPerpAvailable = perpAvailable,
                        customStockEntryWindows = _uiState.value.stockEntryWindows
                    )
                    if (_uiState.value.selectedSymbol.equals(quote.symbol, ignoreCase = true) &&
                        _uiState.value.selectedTimeframe == currentTf
                    ) {
                        val updatedVs = _uiState.value.verdictState?.copy(bestTimeframeSummary = summary)
                        _uiState.value = _uiState.value.copy(
                            bestTimeframeSummary = summary,
                            verdictState = updatedVs
                        )
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun scanQueryThroughAllAgentsAndNodes(query: String) {
        executeUnifiedQuery(query)
    }

    fun addOrUpdateAiModel(config: AiModelConfig) {
        viewModelScope.launch {
            repository.saveAiModel(config)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Configured AI Model: ${config.provider.displayName} [${config.modelName}]"
            )
        }
    }

    fun deleteAiModel(id: String) {
        viewModelScope.launch {
            repository.deleteAiModel(id)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Removed AI model from fleet"
            )
        }
    }

    fun toggleAiModel(id: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.setAiModelEnabled(id, isEnabled)
        }
    }

    fun saveCompanyApiKey(companyId: String, apiKey: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDiscoveringCompany = companyId,
                feedbackMessage = "Connecting and discovering models for $companyId..."
            )
            val result = repository.saveCompanyApiKey(companyId, apiKey)
            _uiState.value = _uiState.value.copy(
                isDiscoveringCompany = null,
                feedbackMessage = when (result.status) {
                    com.example.domain.model.CompanyStatus.CONNECTED -> "Connected! Discovered ${result.models.count { it.isUsable }} usable models."
                    com.example.domain.model.CompanyStatus.NEEDS_CREDIT -> "Key accepted, but account needs billing credit/balance."
                    com.example.domain.model.CompanyStatus.INVALID_KEY -> "Invalid API key: ${result.statusDetail ?: "Please verify your key"}"
                    com.example.domain.model.CompanyStatus.QUOTA_USED -> "Quota limit reached for today."
                    else -> result.statusDetail ?: "Updated key"
                }
            )
        }
    }

    fun pasteAnyKey(rawKey: String) {
        val clean = rawKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (clean.isBlank()) return

        if (clean.startsWith("sk-") && !clean.startsWith("sk-ant-") && !clean.startsWith("sk-or-")) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isDiscoveringCompany = "detecting",
                    feedbackMessage = "Analyzing sk- key (probing OpenAI and DeepSeek)..."
                )
                val (company, result) = repository.pasteAnyKeyAndConnect(clean)
                _uiState.value = _uiState.value.copy(
                    isDiscoveringCompany = null,
                    feedbackMessage = if (company != null && result.status == com.example.domain.model.CompanyStatus.CONNECTED) {
                        "Auto-detected and connected ${company.displayName}! (${result.models.size} models)"
                    } else if (company != null && result.status == com.example.domain.model.CompanyStatus.NEEDS_CREDIT) {
                        "Auto-detected ${company.displayName}: Account needs billing balance."
                    } else {
                        "Could not verify key on OpenAI or DeepSeek. Select company manually."
                    }
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDiscoveringCompany = "detecting",
                feedbackMessage = "Auto-detecting provider from key prefix..."
            )
            val (company, result) = repository.pasteAnyKeyAndConnect(clean)
            _uiState.value = _uiState.value.copy(
                isDiscoveringCompany = null,
                feedbackMessage = if (company != null) {
                    "Auto-assigned to ${company.displayName}: ${result.status.label}"
                } else {
                    "Unrecognized key prefix. Enter key in the specific company card below."
                }
            )
        }
    }

    fun refreshCompany(companyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDiscoveringCompany = companyId,
                feedbackMessage = "Refreshing models for $companyId..."
            )
            val result = repository.refreshCompanyModels(companyId)
            _uiState.value = _uiState.value.copy(
                isDiscoveringCompany = null,
                feedbackMessage = "Refresh complete: ${result.models.count { it.isUsable }} usable models."
            )
        }
    }

    fun refreshAllCompanies() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(feedbackMessage = "Refreshing all connected companies...")
            repository.refreshAllCompanies()
            _uiState.value = _uiState.value.copy(feedbackMessage = "Model catalogs refreshed across all companies.")
        }
    }

    fun pinModel(companyId: String, modelId: String?) {
        viewModelScope.launch {
            repository.pinModelForCompany(companyId, modelId)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = if (modelId != null) "Pinned model: $modelId" else "Reset to Auto (Router Managed)"
            )
        }
    }

    fun updateCompanyCustomConfig(companyId: String, endpoint: String?, authHeader: String?) {
        viewModelScope.launch {
            repository.updateCompanyCustomConfig(companyId, endpoint, authHeader)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Updated configuration for $companyId"
            )
        }
    }

    fun toggleAiEnrichment(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            aiEnrichmentEnabled = enabled,
            feedbackMessage = if (enabled) "AI Enrichment enabled (Auto)" else "AI Enrichment disabled (Engine-only mode)"
        )
    }

    fun testApiSource(sourceId: String) {
        viewModelScope.launch {
            val updated = repository.testApiConnection(sourceId)
            val updatedList = _uiState.value.apiSources.map {
                if (it.id == sourceId) updated else it
            }
            _uiState.value = _uiState.value.copy(
                apiSources = updatedList,
                feedbackMessage = "Tested ${updated.name}: ${updated.status}"
            )
        }
    }

    fun clearOfflineCache() {
        viewModelScope.launch {
            repository.clearOfflineScanCache()
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Cleared local Room database cache for crypto pairs and signals"
            )
        }
    }

    fun testAiModelPing(
        modelId: String,
        onFinished: (success: Boolean, latencyMs: Long, message: String) -> Unit = { _, _, _ -> }
    ) {
        val model = _uiState.value.aiModels.find { it.id == modelId }
        if (model == null) {
            onFinished(false, 0L, "Model not configured")
            return
        }
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    feedbackMessage = "Testing ${model.provider.displayName} [${model.modelName}]..."
                )
                val startTime = System.currentTimeMillis()
                val res = repository.aiDispatcher.executeModelCall(
                    model = model,
                    prompt = "Ping health check",
                    roleName = model.assignedRole?.title ?: "AI Fleet Node"
                )
                val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(res.latencyMs)
                val statusStr = if (res.success) {
                    "Online"
                } else {
                    val rawErr = res.errorMessage ?: "Check Key/Endpoint"
                    when {
                        rawErr.contains("Invalid API Key", ignoreCase = true) || rawErr.contains("invalid_api_key", ignoreCase = true) -> "Invalid API Key"
                        rawErr.contains("401", ignoreCase = true) -> "401 Unauthorized"
                        rawErr.contains("404", ignoreCase = true) -> "404 Not Found"
                        rawErr.contains("429", ignoreCase = true) -> "429 Rate Limited"
                        rawErr.length > 25 -> rawErr.take(22) + "..."
                        else -> rawErr
                    }
                }
                val updatedModel = model.copy(
                    lastLatencyMs = latency,
                    lastStatus = if (res.success) "Online" else "Error ($statusStr)"
                )
                repository.saveAiModel(updatedModel)
                _uiState.value = _uiState.value.copy(
                    feedbackMessage = "Ping ${model.modelName}: $statusStr (${latency}ms)"
                )
                onFinished(res.success, latency, statusStr)
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Ping failed for $modelId", e)
                onFinished(false, 0L, e.message ?: "Ping failed")
            }
        }
    }

    fun updateBacktestConfig(config: BacktestStrategyConfig) {
        _uiState.value = _uiState.value.copy(backtestConfig = config)
    }

    fun runStrategyBacktest(
        symbol: String? = null,
        timeframe: Timeframe? = null,
        config: BacktestStrategyConfig? = null
    ) {
        val targetSymbol = symbol ?: _uiState.value.selectedSymbol
        val targetTimeframe = timeframe ?: _uiState.value.selectedTimeframe
        val targetConfig = config ?: _uiState.value.backtestConfig

        _uiState.value = _uiState.value.copy(
            isRunningBacktest = true,
            backtestConfig = targetConfig,
            feedbackMessage = "Simulating multi-agent strategy on historical $targetSymbol (${targetTimeframe.label})..."
        )

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val current = _uiState.value.currentQuote
                val quote = if (current != null && current.symbol.equals(targetSymbol, ignoreCase = true) &&
                    current.candles.size >= 15 &&
                    _uiState.value.selectedTimeframe == targetTimeframe) {
                    current
                } else {
                    repository.getQuote(targetSymbol, targetTimeframe).getOrThrow()
                }

                val result = TradingAgentsEngine.runHistoricalBacktest(
                    quote = quote,
                    config = targetConfig,
                    timeframe = targetTimeframe
                )

                _uiState.value = _uiState.value.copy(
                    backtestResult = result,
                    isRunningBacktest = false,
                    feedbackMessage = "Backtest Complete: ${result.totalTrades} trades simulated (Win Rate: ${String.format(Locale.US, "%.1f", result.winRatePercent)}%, Return: ${if (result.totalReturnPercent >= 0) "+" else ""}${String.format(Locale.US, "%.1f", result.totalReturnPercent)}%)"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunningBacktest = false,
                    feedbackMessage = "Backtest simulation failed: ${e.message}"
                )
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
    }

    fun setFeedback(message: String) {
        _uiState.value = _uiState.value.copy(feedbackMessage = message)
    }

    // --- Day-Trader Pack v1 Pipeline & Actions ---
    private fun getMarketProfile(assetType: AssetType, marketType: TradingMarketType): MarketProfile {
        val stockWindows = _uiState.value.stockEntryWindows
        return when {
            assetType == AssetType.STOCK && marketType == TradingMarketType.FUTURES ->
                MarketProfile.defaultEquityPerp().copy(entryWindows = stockWindows)
            assetType == AssetType.STOCK ->
                MarketProfile.defaultUsStock().copy(entryWindows = stockWindows)
            else -> MarketProfile.defaultCrypto()
        }
    }

    private fun buildCanonicalVerdictState(
        quote: MarketQuote,
        setup: TradeSetupPlan?,
        synthesis: MultiAgentSynthesis?,
        eventRisk: EventRiskAssessment?,
        marketType: TradingMarketType,
        timeframe: Timeframe
    ): VerdictState {
        // Prefer setup.signalOutput (which reflects current offHoursMode, perp routing, AI shift, and selected side)
        val candidateV2 = when {
            setup?.signalOutput?.symbol.equals(quote.symbol, ignoreCase = true) -> setup?.signalOutput
            synthesis?.signalEngineOutput?.symbol.equals(quote.symbol, ignoreCase = true) -> synthesis?.signalEngineOutput
            else -> null
        }

        val indicators = if (quote.candles.size >= 15) QuantitativeEngine.computeIndicators(quote.candles) else null
        val regimeMetricsSummary = if (indicators != null) SignalVetoDetector.formatRegimeMetrics(indicators) else ""
        val maxOffset = minOf((indicators?.averageTrueRange?.takeIf { it > 0.0 } ?: quote.currentPrice * 0.03) * 5.0, quote.currentPrice * 0.25)

        val isSaneV2 = candidateV2 != null &&
            kotlin.math.abs(candidateV2.bullScenario.invalidationLevel - quote.currentPrice) <= maxOffset &&
            kotlin.math.abs(candidateV2.bearScenario.invalidationLevel - quote.currentPrice) <= maxOffset
        val v2 = if (isSaneV2) candidateV2 else null

        val isContextOnlyTf = v2?.isContextOnly == true || timeframe in listOf(Timeframe.FIVE_DAYS, Timeframe.ONE_MONTH, Timeframe.THREE_MONTHS, Timeframe.ONE_YEAR)
        val rawVerdict = v2?.verdictState ?: setup?.verdictState ?: SignalVerdictState.NO_TRADE
        val hasValidLevels = setup != null &&
            setup.pairSymbol.equals(quote.symbol, ignoreCase = true) &&
            setup.isAvailable &&
            setup.entryType != com.example.domain.model.EntryOrderType.WAIT_NO_TRADE &&
            setup.recommendedEntry > 0.0 &&
            setup.stopLoss > 0.0 &&
            rawVerdict != SignalVerdictState.NO_TRADE

        val effectiveVerdict = when {
            hasValidLevels -> rawVerdict
            isContextOnlyTf -> SignalVerdictState.WAIT
            else -> SignalVerdictState.NO_TRADE
        }
        val effectivePlan = if (hasValidLevels) setup else null

        val safePrice = quote.currentPrice.coerceAtLeast(0.0)
        val fallbackBull = ScenarioPlan(
            isBull = true,
            isActive = _uiState.value.positionSetupSide == PositionSide.LONG,
            triggerCondition = "Bullish MSS / CHoCH confirmation with displacement above ${PriceFormatter.formatCurrency(safePrice * 1.012)} and RVOL >= 1.0",
            invalidationLevel = safePrice * 0.985,
            targetLevel = safePrice * 1.035,
            narrative = "Reclaim of demand zone with institutional absorption."
        )
        val fallbackBear = ScenarioPlan(
            isBull = false,
            isActive = _uiState.value.positionSetupSide == PositionSide.SHORT,
            triggerCondition = "Bearish MSS / CHoCH confirmation with rejection below ${PriceFormatter.formatCurrency(safePrice * 0.988)} and RVOL >= 1.0",
            invalidationLevel = safePrice * 1.015,
            targetLevel = safePrice * 0.965,
            narrative = "Rejection at overhead supply with liquidity sweep."
        )
        val bullScenario = v2?.bullScenario ?: fallbackBull
        val bearScenario = v2?.bearScenario ?: fallbackBear

        val baseConflicts = (v2?.conflicts ?: emptyList()).toMutableList()
        val smc = indicators?.smc
        val isMixedSmc = smc != null && (
            (smc.smcSwingStructure.contains("Bearish", true) && smc.smcInternalStructure.contains("Bullish", true)) ||
            (smc.smcSwingStructure.contains("Bullish", true) && smc.smcInternalStructure.contains("Bearish", true))
        )
        if (isMixedSmc && baseConflicts.none { it.contains("MIXED", true) || it.contains("swing", true) }) {
            baseConflicts.add("SMC Structure MIXED (${smc?.smcSwingStructure} vs ${smc?.smcInternalStructure})")
        }
        val vetoes = buildList {
            v2?.vetoReasons?.forEach { if (it.isNotBlank()) add(it) }
            if (isEmpty() && effectiveVerdict == SignalVerdictState.NO_TRADE && setup?.unavailableReason?.isNotBlank() == true) {
                add(setup.unavailableReason)
            }
        }

        val reasons = buildList {
            val why = v2?.oneLineWhy ?: setup?.unavailableReason ?: ""
            if (why.isNotBlank()) add(why)
            if (effectiveVerdict == SignalVerdictState.NO_TRADE && regimeMetricsSummary.isNotBlank() && !why.contains("ADX", ignoreCase = true)) {
                add(regimeMetricsSummary)
            }
        }

        val waitingCond = when {
            effectiveVerdict == SignalVerdictState.TRADE -> ""
            isContextOnlyTf -> _uiState.value.bestTimeframeSummary?.headline ?: "Context-only (${timeframe.label}) — switch to 1D / 4h / 1h for entry timing"
            effectivePlan != null -> v2?.primarySetup?.triggerCondition ?: effectivePlan.structureState.ifBlank { "Trigger confirmation in zone" }
            _uiState.value.positionSetupSide == PositionSide.SHORT -> bearScenario.triggerCondition
            else -> bullScenario.triggerCondition
        }
        val distPct = if (effectivePlan != null && safePrice > 0.0 && effectivePlan.recommendedEntry > 0.0) {
            kotlin.math.abs(safePrice - effectivePlan.recommendedEntry) / safePrice * 100.0
        } else null

        val barsLoadedCount = maxOf(
            quote.barsLoadedCount,
            v2?.barsLoaded ?: 0,
            quote.candles.count { it.isClosed }
        )
        val staleWarn = quote.staleReason ?: if (quote.isStale && quote.candleAgeMillis > 0L) {
            "Stale data (${quote.candleAgeMillis / 60_000L}m since last closed bar)"
        } else null

        return VerdictState(
            symbol = quote.symbol,
            marketType = setup?.marketType ?: marketType,
            timeframe = timeframe,
            verdict = effectiveVerdict,
            reasons = reasons,
            vetoes = vetoes,
            conflicts = baseConflicts.distinct(),
            hasStructuralConflicts = isMixedSmc || baseConflicts.isNotEmpty(),
            plan = effectivePlan,
            bullScenario = bullScenario,
            bearScenario = bearScenario,
            waitingForCondition = waitingCond,
            distanceToTriggerPercent = distPct,
            familyVotes = v2?.allFamilyVotes ?: emptyList(),
            familiesAlignedCount = v2?.familiesAlignedCount ?: 0,
            totalFamiliesCount = 7,
            marketRegime = v2?.marketRegime ?: (indicators?.let { SignalVetoDetector.classifyMarketRegime(it) } ?: MarketRegime.RANGE),
            regimeMetricsSummary = regimeMetricsSummary,
            evidence = v2?.evidence,
            eventRisk = eventRisk,
            oneLineWhy = v2?.oneLineWhy ?: reasons.firstOrNull() ?: "${effectiveVerdict.label} on ${quote.symbol}",
            sessionStatus = v2?.sessionStatus ?: SignalVetoDetector.determineSessionStatus(quote.symbol, quote.assetType),
            livePrice = safePrice,
            setupGrade = v2?.setupGrade,
            softFlags = v2?.softFlags ?: emptyList(),
            needsCondition = v2?.needsCondition ?: waitingCond,
            bestTimeframeSummary = _uiState.value.bestTimeframeSummary,
            barsLoaded = barsLoadedCount,
            lastClosedBarTimestamp = quote.lastClosedBarTimestamp,
            candleAgeMillis = quote.candleAgeMillis,
            staleDataWarning = staleWarn,
            offHoursMode = v2?.offHoursMode ?: _uiState.value.offHoursMode,
            routedViaFuturesShort = (setup?.routedViaFuturesShort == true) || (v2?.routedViaFuturesShort == true),
            isContextOnly = isContextOnlyTf,
            aiShiftPoints = v2?.aiShiftPoints ?: (synthesis?.aiShiftPoints ?: 0),
            aiShiftModelName = v2?.aiShiftModelName ?: synthesis?.aiShiftModelName
        )
    }

    private fun runDayTraderPipeline(
        quote: MarketQuote,
        setup: TradeSetupPlan,
        news: List<NewsItem>,
        synthesis: MultiAgentSynthesis? = _uiState.value.agentSynthesis
    ) {
        val marketType = _uiState.value.selectedMarketType
        val timeframe = _uiState.value.selectedTimeframe
        val profile = getMarketProfile(quote.assetType, marketType)
        val closedCandles = quote.candles.filter { it.isClosed }
        val isCurrentBarClosed = quote.candles.lastOrNull()?.isClosed ?: true

        val taggedNews = DayTraderNewsEventEngine.processHeadlines(
            rawNews = news,
            profile = profile,
            targetSymbol = quote.symbol,
            dayChangePercent = quote.changePercent
        )
        val eventRisk = DayTraderNewsEventEngine.assessEventRisk(
            symbol = quote.symbol,
            profile = profile,
            taggedHeadlines = taggedNews
        )
        if (eventRisk.hasNewsShock && eventRisk.symbol.equals(quote.symbol, ignoreCase = true)) {
            val shockHeadline = taggedNews.firstOrNull { it.isShockEvent }?.headline ?: "Breaking shock event"
            alertManager.onNewsShockDetected(quote.symbol, shockHeadline, eventRisk.shockSourceType ?: "Structured Event")
        }

        val cryptoFund = if (quote.assetType == AssetType.CRYPTO) {
            CompanyFactsEngine.buildCryptoFundamentals(
                symbol = quote.symbol,
                marketCap = quote.marketCap.takeIf { it > 0.0 },
                fdv = null,
                circulatingSupply = null,
                totalSupply = null,
                volume24h = quote.volume.takeIf { it > 0.0 }
            )
        } else null

        fetchStockFundamentalsIfApplicable(quote)

        val canonicalVerdict = buildCanonicalVerdictState(
            quote = quote,
            setup = setup,
            synthesis = synthesis,
            eventRisk = eventRisk,
            marketType = marketType,
            timeframe = timeframe
        )

        repository.recordVerdictEvaluation(canonicalVerdict.verdict, canonicalVerdict.vetoes)

        if (!canonicalVerdict.hasValidPlan || canonicalVerdict.plan == null) {
            replayJob?.cancel()
            _uiState.value = _uiState.value.copy(
                tradeSetupPlan = null,
                verdictState = canonicalVerdict,
                dayTraderPlan = null,
                entryTimerStatus = null,
                riskEvaluation = null,
                replayResult = null,
                isRunningReplay = false,
                hypotheticalScenarioReplay = null,
                hypotheticalScenarioSide = null,
                taggedHeadlines = taggedNews,
                eventRiskAssessment = eventRisk,
                cryptoFundamentals = cryptoFund,
                diagnosticsSnapshot = repository.getDiagnosticsSnapshot()
            )
            return
        }

        val validSetup = canonicalVerdict.plan
        val tickSize = PriceFormatter.inferTickSize(quote.currentPrice)

        val targetPrice = validSetup.takeProfit1.takeIf { it > 0.0 }
            ?: if (validSetup.side == PositionSide.LONG) validSetup.recommendedEntry * 1.02 else validSetup.recommendedEntry * 0.98
        val rawStop = validSetup.stopLoss.takeIf { it > 0.0 }
            ?: if (validSetup.side == PositionSide.LONG) validSetup.recommendedEntry * 0.99 else validSetup.recommendedEntry * 1.01
        val triggerText = validSetup.signalOutput?.primarySetup?.triggerCondition
            ?: validSetup.structureState.ifBlank { "Completed setup bar close beyond trigger" }

        val initialPlan = ExitPlanner.createPlan(
            symbol = quote.symbol,
            side = validSetup.side,
            entryPrice = validSetup.recommendedEntry,
            stopRule = StopRuleType.STRUCTURE_SWEEP,
            rawStopPrice = rawStop,
            targetRule = TargetRuleType.STRUCTURE_LEVEL,
            rawTargetPrice = targetPrice,
            tickSize = tickSize,
            profile = profile,
            isTriggerFired = false,
            triggerCondition = triggerText
        )

        val hasVeto = canonicalVerdict.verdict == SignalVerdictState.NO_TRADE || initialPlan.validationError != null
        val vetoReason = canonicalVerdict.vetoes.firstOrNull() ?: initialPlan.validationError

        val timerStatus = EntryTimerStateMachine.evaluate(
            plan = initialPlan,
            latestCompletedBar = closedCandles.lastOrNull(),
            isCurrentBarClosed = isCurrentBarClosed,
            profile = profile,
            hasActiveVeto = hasVeto,
            vetoMessage = vetoReason
        )
        val dtPlan = initialPlan.copy(
            isTriggerFired = timerStatus.state == EntryTimerState.TRIGGERED
        )

        val initialRisk = evaluateRiskForPlan(dtPlan, profile, quote.assetType)
        if (!initialRisk.isAllowed && !initialRisk.isAccountUnconfigured) {
            repository.recordSizerBlock()
        }

        _uiState.value = _uiState.value.copy(
            tradeSetupPlan = validSetup,
            verdictState = canonicalVerdict,
            dayTraderPlan = dtPlan,
            entryTimerStatus = timerStatus,
            riskEvaluation = initialRisk,
            hypotheticalScenarioReplay = null,
            hypotheticalScenarioSide = null,
            taggedHeadlines = taggedNews,
            eventRiskAssessment = eventRisk,
            cryptoFundamentals = cryptoFund,
            diagnosticsSnapshot = repository.getDiagnosticsSnapshot()
        )

        runReplayForPlanAsync(
            quote = quote,
            plan = dtPlan,
            profile = profile,
            isFutures = ((validSetup.marketType == TradingMarketType.FUTURES || marketType == TradingMarketType.FUTURES) && quote.assetType == AssetType.CRYPTO),
            seed = _uiState.value.replaySeed
        )
    }

    fun runHypotheticalScenarioReplay(side: PositionSide) {
        val quote = _uiState.value.currentQuote ?: return
        val vs = _uiState.value.verdictState ?: return
        val scenario = if (side == PositionSide.LONG) vs.bullScenario else vs.bearScenario
        val stopPrice = scenario.invalidationLevel
        val targetPrice = scenario.targetLevel
        // Extract price number from trigger text or derive midpoint inside valid range
        val regex = Regex("""\$\s*([0-9,]+\.?[0-9]*)""")
        val extractedTrigger = regex.find(scenario.triggerCondition)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
        val entryPrice = extractedTrigger?.takeIf { it > 0.0 }
            ?: if (side == PositionSide.LONG) quote.currentPrice * 1.012 else quote.currentPrice * 0.988

        if (entryPrice <= 0.0 || stopPrice <= 0.0 || targetPrice <= 0.0) return

        val isFutures = _uiState.value.selectedMarketType == TradingMarketType.FUTURES && quote.assetType == AssetType.CRYPTO
        val seed = _uiState.value.replaySeed
        _uiState.value = _uiState.value.copy(
            isRunningHypotheticalReplay = true,
            hypotheticalScenarioSide = side
        )
        replayJob?.cancel()
        replayJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val res = repository.runReplayForPlan(
                    symbol = quote.symbol,
                    assetType = quote.assetType,
                    side = side,
                    entryPrice = entryPrice,
                    stopLossPrice = stopPrice,
                    takeProfitPrice = targetPrice,
                    isFutures = isFutures,
                    seed = seed
                )
                if (_uiState.value.selectedSymbol.equals(quote.symbol, ignoreCase = true)) {
                    _uiState.value = _uiState.value.copy(
                        hypotheticalScenarioReplay = res,
                        isRunningHypotheticalReplay = false
                    )
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isRunningHypotheticalReplay = false)
            }
        }
    }

    private fun evaluateRiskForPlan(
        plan: DayTraderPlan,
        profile: MarketProfile,
        assetType: AssetType,
        replayVerdict: com.example.domain.daytrader.replay.ReplayVerdict? = _uiState.value.replayResult?.verdict
    ): RiskEvaluationResult {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance(profile.timeZone).apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val todayOutcomes = _uiState.value.tradeOutcomes.filter { it.createdAt >= todayStart && it.filled }
        val realizedToday = todayOutcomes.sumOf { it.realizedR }
        val lev = if (assetType == AssetType.CRYPTO && _uiState.value.selectedMarketType == TradingMarketType.FUTURES) {
            _uiState.value.selectedLeverage
        } else 1
        val lotStep = if (assetType == AssetType.STOCK) 1.0 else 0.01
        val grade = _uiState.value.verdictState?.setupGrade
        val riskMultiplier = if (grade == SetupGrade.B) 0.5 else 1.0

        return RiskBudgetEngine.evaluateRiskAndSize(
            plan = plan,
            profile = profile,
            settings = _uiState.value.riskSettings,
            tradesTakenToday = todayOutcomes.size,
            realizedRToday = realizedToday,
            replayVerdict = replayVerdict,
            currentTimestamp = now,
            lotStepSize = lotStep,
            selectedLeverage = lev,
            sizeMultiplier = riskMultiplier,
            acceptHigherRisk = _uiState.value.acceptHigherRiskThisTrade
        )
    }

    private fun runReplayForPlanAsync(
        quote: MarketQuote,
        plan: DayTraderPlan,
        profile: MarketProfile,
        isFutures: Boolean,
        seed: Long
    ) {
        replayJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunningReplay = true)
        replayJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = repository.runReplayForPlan(
                    symbol = quote.symbol,
                    assetType = quote.assetType,
                    side = plan.side,
                    entryPrice = plan.entryPrice,
                    stopLossPrice = plan.stopPrice,
                    takeProfitPrice = plan.targetPrice,
                    isFutures = isFutures,
                    seed = seed
                )
                if (!_uiState.value.selectedSymbol.equals(quote.symbol, ignoreCase = true)) {
                    return@launch
                }
                if (result.refusalReason == null) {
                    SignalCalibrationEngine.recordReplayPWin(
                        symbol = quote.symbol,
                        pWin = result.effectivePWin,
                        conservativeMode = result.conservativeModeLabel,
                        asIsPWin = result.asIs?.observedTargetShare ?: result.effectivePWin,
                        driftNeutralPWin = result.driftNeutral?.observedTargetShare ?: result.effectivePWin,
                        oddsDiffPoints = result.oddsDiffPoints
                    )
                }
                val updatedRisk = evaluateRiskForPlan(plan, profile, quote.assetType, result.verdict)
                _uiState.value = _uiState.value.copy(
                    replayResult = result,
                    isRunningReplay = false,
                    riskEvaluation = updatedRisk,
                    diagnosticsSnapshot = repository.getDiagnosticsSnapshot()
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isRunningReplay = false)
            }
        }
    }

    fun runReplayForCurrentPlan(customSeed: Long? = null) {
        val quote = _uiState.value.currentQuote ?: return
        val plan = _uiState.value.dayTraderPlan ?: return
        val seedToUse = customSeed ?: _uiState.value.replaySeed
        _uiState.value = _uiState.value.copy(replaySeed = seedToUse)
        val profile = getMarketProfile(quote.assetType, _uiState.value.selectedMarketType)
        val isFutures = _uiState.value.selectedMarketType == TradingMarketType.FUTURES && quote.assetType == AssetType.CRYPTO
        runReplayForPlanAsync(quote, plan, profile, isFutures, seedToUse)
    }

    fun runDayTraderScan(universeFilter: String = _uiState.value.selectedScannerUniverse) {
        _uiState.value = _uiState.value.copy(
            selectedScannerUniverse = universeFilter,
            isRunningDayTraderScan = true
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val unified = repository.scanDayTraderUnified(
                    universeFilter = universeFilter,
                    marketType = _uiState.value.selectedMarketType,
                    offHoursMode = _uiState.value.offHoursMode,
                    customStockEntryWindows = _uiState.value.stockEntryWindows
                )
                _uiState.value = _uiState.value.copy(
                    scanSetups = unified.setups,
                    scanMarketSummary = unified.marketSummary,
                    scanHits = unified.rawRuleHits,
                    scanDiagnostics = unified.diagnostics,
                    isRunningDayTraderScan = false,
                    diagnosticsSnapshot = repository.getDiagnosticsSnapshot()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRunningDayTraderScan = false,
                    feedbackMessage = "Scanner error: ${e.message}",
                    diagnosticsSnapshot = repository.getDiagnosticsSnapshot()
                )
            }
        }
    }

    fun armConditionalAlert(
        symbol: String,
        triggerPrice: Double,
        side: PositionSide,
        condition: String
    ) {
        val clean = symbol.trim().uppercase(Locale.US)
        if (clean.isBlank() || triggerPrice <= 0.0) return
        val formattedPrice = PriceFormatter.formatCurrency(triggerPrice)
        val ts = java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date())
        val entry = "[$ts] Armed conditional alert on $clean (${side.name}) @ $formattedPrice — $condition"
        notificationManager.sendNotification(
            notificationId = 8100 + (clean.hashCode() and 0x3FF),
            title = "ALERT ARMED: $clean ${side.name}",
            message = "Trigger $formattedPrice — $condition",
            subText = "Conditional Watch Alert"
        )
        val shouldSetThreshold = clean.equals(_uiState.value.selectedSymbol, ignoreCase = true)
        _uiState.value = _uiState.value.copy(
            customAlertThreshold = if (shouldSetThreshold) triggerPrice else _uiState.value.customAlertThreshold,
            alertNotificationsLog = listOf(entry) + _uiState.value.alertNotificationsLog.take(9),
            feedbackMessage = "Conditional alert armed for $clean @ $formattedPrice"
        )
    }

    fun saveSymbolToWatchlist(
        symbol: String,
        name: String = "",
        assetType: AssetType? = null
    ) {
        val clean = symbol.trim().uppercase(Locale.US)
        if (clean.isBlank()) return
        val resolvedType = assetType ?: if (repository.isCryptoSymbol(clean)) AssetType.CRYPTO else AssetType.STOCK
        val resolvedName = name.ifBlank { clean }
        viewModelScope.launch {
            repository.addToWatchlist(clean, resolvedName, resolvedType)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Saved $clean to Watchlist"
            )
        }
    }

    fun configureRiskSettings(accountBalance: Double, riskPercent: Double) {
        if (accountBalance <= 0.0 || riskPercent <= 0.0) {
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Enter positive account size and risk %."
            )
            return
        }
        val maxRiskDollars = accountBalance * (riskPercent / 100.0)
        prefs.edit()
            .putBoolean("risk_configured", true)
            .putFloat("risk_account_balance", accountBalance.toFloat())
            .putFloat("risk_percent", riskPercent.toFloat())
            .apply()
        val updatedSettings = _uiState.value.riskSettings.copy(
            accountBalance = accountBalance,
            riskPercent = riskPercent,
            maxRiskPerTradeDollars = maxRiskDollars,
            availableCashOrMargin = accountBalance,
            isConfigured = true
        )
        updateRiskSettings(updatedSettings)
        _uiState.value = _uiState.value.copy(
            feedbackMessage = "Risk budget configured: ${PriceFormatter.formatCash(accountBalance)} acct • ${PriceFormatter.formatPercent(riskPercent, 1)} (${PriceFormatter.formatCash(maxRiskDollars)}) max risk/trade"
        )
    }

    fun updateRiskSettings(newSettings: RiskSettings) {
        prefs.edit()
            .putBoolean("risk_configured", newSettings.isConfigured)
            .putFloat("risk_account_balance", newSettings.accountBalance.toFloat())
            .putFloat("risk_percent", newSettings.riskPercent.toFloat())
            .apply()
        _uiState.value = _uiState.value.copy(riskSettings = newSettings)
        val quote = _uiState.value.currentQuote ?: return
        val plan = _uiState.value.dayTraderPlan ?: return
        val profile = getMarketProfile(quote.assetType, _uiState.value.selectedMarketType)
        val updated = evaluateRiskForPlan(plan, profile, quote.assetType)
        _uiState.value = _uiState.value.copy(riskEvaluation = updated)
    }

    fun logRealTrade(
        symbol: String,
        side: PositionSide,
        entryPrice: Double,
        stopLoss: Double,
        exitPrice: Double,
        entryModel: String
    ) {
        val clean = symbol.trim().uppercase()
        if (clean.isBlank() || entryPrice <= 0.0) return
        val assetType = if (repository.isCryptoSymbol(clean)) AssetType.CRYPTO else AssetType.STOCK
        val sessionName = if (assetType == AssetType.STOCK) {
            com.example.domain.daytrader.session.MarketCalendar.let {
                val cal = java.util.Calendar.getInstance(it.NY_TZ)
                val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val m = cal.get(java.util.Calendar.MINUTE)
                val mins = h * 60 + m
                when {
                    it.isNyseHoliday(cal) -> "OFF_HOURS"
                    mins in (9 * 60 + 30)..(16 * 60) -> "OPEN"
                    else -> "EXTENDED"
                }
            }
        } else "OPEN"
        val risk = kotlin.math.abs(entryPrice - stopLoss).coerceAtLeast(entryPrice * 0.001)
        val tp1 = if (side == PositionSide.LONG) entryPrice + risk * 2.0 else entryPrice - risk * 2.0

        viewModelScope.launch(Dispatchers.IO) {
            repository.recordClosedTradeOutcome(
                symbol = clean,
                side = side,
                entryPrice = entryPrice,
                stopLoss = stopLoss,
                takeProfit1 = tp1,
                exitPrice = exitPrice,
                isRealTrade = true,
                entryModel = entryModel,
                sessionName = sessionName,
                assetType = assetType,
                exitReason = "User Logged Real Trade"
            )
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Logged real trade on $clean ($entryModel) to Ledger"
            )
        }
    }

    fun logPaperTradeFromPlan(outcomeExitPrice: Double? = null, exitReason: String = "Manual Paper Close") {
        val verdictState = _uiState.value.verdictState
        val riskEval = _uiState.value.riskEvaluation
        val plan = _uiState.value.dayTraderPlan
        if (verdictState == null || verdictState.verdict == SignalVerdictState.NO_TRADE || plan == null || riskEval?.paperFollowAllowed == false) {
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Paper fill disabled: requires non-NO_TRADE plan and active daily/cooldown budget."
            )
            return
        }
        val quote = _uiState.value.currentQuote ?: return
        val exitP = outcomeExitPrice ?: quote.currentPrice
        val entryModel = _uiState.value.tradeSetupPlan?.entryModelName ?: "A_TREND_PULLBACK"
        val sessionName = _uiState.value.tradeSetupPlan?.sessionStatus?.name ?: "OPEN"

        viewModelScope.launch(Dispatchers.IO) {
            repository.recordClosedTradeOutcome(
                symbol = plan.symbol,
                side = plan.side,
                entryPrice = plan.entryPrice,
                stopLoss = plan.stopPrice,
                takeProfit1 = plan.targetPrice,
                exitPrice = exitP,
                isRealTrade = false,
                entryModel = entryModel,
                sessionName = sessionName,
                assetType = quote.assetType,
                exitReason = exitReason
            )
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Paper outcome logged for ${plan.symbol} @ ${PriceFormatter.formatCurrency(exitP)}"
            )
        }
    }

    fun dismissAlertBanner() {
        alertManager.clearAlerts()
    }

    fun clearPaperLedger() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearPaperLedger()
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Paper & real trade ledger cleared"
            )
        }
    }

    // =========================================================================
    // MASTER VERDICT v1 (Parts A, B, C) Actions
    // =========================================================================

    fun refreshMasterVerdict(
        quote: MarketQuote? = _uiState.value.currentQuote,
        marketType: TradingMarketType = _uiState.value.selectedMarketType,
        closedTimeframe: Timeframe? = null,
        forceRefresh: Boolean = false
    ) {
        val activeQuote = quote ?: return
        masterVerdictJob?.cancel()
        masterVerdictJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingMasterVerdict = true)
            val state = _uiState.value
            val synth = state.agentSynthesis
            val evaluated = runCatching {
                analysisCoordinator.evaluateSymbol(
                    symbol = activeQuote.symbol,
                    assetType = activeQuote.assetType,
                    marketType = marketType,
                    livePriceHint = activeQuote.currentPrice,
                    isBackground = false,
                    closedTimeframe = closedTimeframe,
                    forceRefresh = forceRefresh,
                    offHoursMode = state.offHoursMode,
                    riskSettings = state.riskSettings,
                    aiShiftPoints = synth?.aiShiftPoints ?: 0,
                    aiShiftModelName = synth?.aiShiftModelName,
                    aiSummary = synth?.unifiedConsensusSummary?.takeIf { !state.isEngineOnly && it.isNotBlank() }
                )
            }.getOrNull()

            if (_uiState.value.selectedSymbol.equals(activeQuote.symbol, ignoreCase = true)) {
                _uiState.value = _uiState.value.copy(
                    masterVerdict = evaluated ?: _uiState.value.masterVerdict,
                    isLoadingMasterVerdict = false,
                    backgroundDiagnostics = BackgroundPreferences.getDiagnostics(getApplication())
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMasterVerdict = false)
            }
        }
    }

    fun selectHorizon(horizon: TradingHorizon) {
        if (horizon == TradingHorizon.OUTLOOK) {
            _uiState.value = _uiState.value.copy(selectedHorizon = horizon)
            return
        }
        val targetTf = _uiState.value.horizonSettings.tripletFor(horizon).setupTf
        _uiState.value = _uiState.value.copy(selectedHorizon = horizon)
        if (_uiState.value.selectedTimeframe != targetTf) {
            setTimeframe(targetTf)
        }
    }

    fun updateHorizonSettings(settings: HorizonSettings) {
        BackgroundPreferences.setHorizonSettings(getApplication(), settings)
        _uiState.value = _uiState.value.copy(
            horizonSettings = settings,
            feedbackMessage = "Horizon timeframe triplets updated"
        )
        refreshMasterVerdict(forceRefresh = true)
    }

    private fun applyBackgroundMonitorSchedule(mode: BackgroundMonitorMode) {
        val appCtx = getApplication<Application>()
        when (mode) {
            BackgroundMonitorMode.OFF -> {
                MarketMonitorWorker.cancel(appCtx)
                LiveMarketMonitorService.stop(appCtx)
            }
            BackgroundMonitorMode.STANDARD -> {
                LiveMarketMonitorService.stop(appCtx)
                MarketMonitorWorker.scheduleStandard(appCtx)
            }
            BackgroundMonitorMode.LIVE -> {
                MarketMonitorWorker.scheduleStandard(appCtx)
                val started = LiveMarketMonitorService.startSafely(appCtx)
                if (!started) {
                    _uiState.value = _uiState.value.copy(
                        feedbackMessage = "Live monitor requires foreground permission; running Standard 15m Worker fallback."
                    )
                }
            }
        }
    }

    fun setBackgroundMonitorMode(mode: BackgroundMonitorMode) {
        val appCtx = getApplication<Application>()
        BackgroundPreferences.setMode(appCtx, mode)
        _uiState.value = _uiState.value.copy(
            backgroundMonitorMode = mode,
            feedbackMessage = "Background monitor: ${mode.label}"
        )
        applyBackgroundMonitorSchedule(mode)
    }

    fun setQuietHours(config: QuietHoursConfig) {
        BackgroundPreferences.setQuietHours(getApplication(), config)
        _uiState.value = _uiState.value.copy(
            quietHoursConfig = config,
            feedbackMessage = if (config.enabled) "Quiet hours active (${config.startHour}:00–${config.endHour}:00)" else "Quiet hours disabled"
        )
    }

    fun setAllowBgOnBatterySaver(allow: Boolean) {
        BackgroundPreferences.setAllowOnBatterySaver(getApplication(), allow)
        _uiState.value = _uiState.value.copy(
            allowBgOnBatterySaver = allow,
            feedbackMessage = if (allow) "Background updates allowed on Battery Saver" else "Paused automatically when Battery Saver is active"
        )
    }

    fun forceRecomputeWatchlist() {
        if (_uiState.value.isRefreshingWatchlist) return
        _uiState.value = _uiState.value.copy(isRefreshingWatchlist = true)
        viewModelScope.launch(Dispatchers.IO) {
            val summary = runCatching {
                analysisCoordinator.runBackgroundWatchlistCycle(maxSymbolsPerRun = 10)
            }.getOrNull()
            val diag = BackgroundPreferences.getDiagnostics(getApplication())
            _uiState.value = _uiState.value.copy(
                isRefreshingWatchlist = false,
                backgroundDiagnostics = diag,
                feedbackMessage = summary?.let {
                    "Watchlist refreshed: ${it.evaluatedCount} evaluated, ${it.skippedUnchangedCount} unchanged"
                } ?: "Watchlist refresh completed"
            )
        }
    }

    fun armHorizonAlert(horizon: TradingHorizon) {
        val verdict = _uiState.value.masterVerdict ?: return
        val plan = verdict.horizonPlans[horizon] ?: return
        val existingId = "${verdict.symbol}_${verdict.marketType.name}_${horizon.name}"
        val alreadyArmed = _uiState.value.armedPlans.any { it.id == existingId }
        viewModelScope.launch(Dispatchers.IO) {
            if (alreadyArmed) {
                repository.deleteArmedPlan(existingId)
                _uiState.value = _uiState.value.copy(
                    feedbackMessage = "Disarmed ${horizon.label} alert on ${verdict.symbol}"
                )
            } else {
                val entity = ArmedPlanEntity(
                    id = existingId,
                    symbol = verdict.symbol,
                    marketType = verdict.marketType.name,
                    horizon = horizon.name,
                    side = (plan.side ?: PositionSide.LONG).name,
                    mode = "ARMED_ALERT",
                    entryZoneMin = plan.entryZoneMin ?: plan.limitPrice ?: verdict.livePrice,
                    entryZoneMax = plan.entryZoneMax ?: plan.limitPrice ?: verdict.livePrice,
                    triggerPrice = plan.triggerPrice ?: plan.limitPrice ?: verdict.livePrice,
                    triggerCondition = plan.triggerCondition,
                    stopLoss = plan.stopLoss ?: 0.0,
                    tp1 = plan.tp1 ?: 0.0,
                    tp2 = plan.tp2 ?: 0.0,
                    tp3 = plan.tp3 ?: 0.0,
                    entryModel = plan.entryModel,
                    grade = plan.grade.name.take(1),
                    validUntilMs = plan.validUntilMs
                )
                repository.upsertArmedPlan(entity)
                _uiState.value = _uiState.value.copy(
                    feedbackMessage = "Armed ${horizon.label} alert for ${verdict.symbol} (${plan.formatZoneOrDash()})"
                )
            }
        }
    }

    fun paperFollowHorizon(horizon: TradingHorizon) {
        val verdict = _uiState.value.masterVerdict ?: return
        val plan = verdict.horizonPlans[horizon] ?: return
        val entityId = "${verdict.symbol}_${verdict.marketType.name}_${horizon.name}"
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ArmedPlanEntity(
                id = entityId,
                symbol = verdict.symbol,
                marketType = verdict.marketType.name,
                horizon = horizon.name,
                side = (plan.side ?: PositionSide.LONG).name,
                mode = "PAPER_FOLLOW",
                entryZoneMin = plan.entryZoneMin ?: plan.limitPrice ?: verdict.livePrice,
                entryZoneMax = plan.entryZoneMax ?: plan.limitPrice ?: verdict.livePrice,
                triggerPrice = plan.triggerPrice ?: plan.limitPrice ?: verdict.livePrice,
                triggerCondition = plan.triggerCondition,
                stopLoss = plan.stopLoss ?: 0.0,
                tp1 = plan.tp1 ?: 0.0,
                tp2 = plan.tp2 ?: 0.0,
                tp3 = plan.tp3 ?: 0.0,
                entryModel = plan.entryModel,
                grade = plan.grade.name.take(1),
                validUntilMs = plan.validUntilMs
            )
            repository.upsertArmedPlan(entity)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "Paper-following ${verdict.symbol} ${horizon.label} (${plan.entryModel}) — outcomes will update calibration"
            )
        }
    }

    fun toggleFuturesForShort() {
        val quote = _uiState.value.currentQuote ?: return
        _uiState.value = _uiState.value.copy(
            selectedMarketType = TradingMarketType.FUTURES,
            positionSetupSide = PositionSide.SHORT,
            feedbackMessage = "Switched to Perpetual Futures SHORT for ${quote.symbol}"
        )
        executeUnifiedQuery(quote.symbol, explicitMarketType = TradingMarketType.FUTURES, explicitAssetType = quote.assetType)
    }

    fun openFromNotification(symbol: String, horizonName: String?) {
        val clean = symbol.trim().uppercase(Locale.US)
        if (clean.isBlank()) return
        val parsedHorizon = horizonName?.let { h ->
            runCatching { TradingHorizon.valueOf(h) }.getOrNull()
        } ?: _uiState.value.selectedHorizon
        val setupTf = if (parsedHorizon != TradingHorizon.OUTLOOK) {
            _uiState.value.horizonSettings.tripletFor(parsedHorizon).setupTf
        } else _uiState.value.selectedTimeframe
        _uiState.value = _uiState.value.copy(
            currentScreen = AppScreen.TERMINAL,
            selectedHorizon = parsedHorizon,
            selectedTimeframe = setupTf
        )
        executeUnifiedQuery(clean)
    }

    override fun onCleared() {
        super.onCleared()
        klineWsClient.disconnect()
        liveTickerJob?.cancel()
        replayJob?.cancel()
        fundamentalsJob?.cancel()
        masterVerdictJob?.cancel()
    }
}
