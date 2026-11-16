package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.domain.daytrader.fundamentals.CompanyFactsXbrl
import com.example.domain.daytrader.fundamentals.QuarterlyFinancialFact
import com.example.domain.daytrader.fundamentals.StockFundamentalsResult
import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.plan.ExitPlanner
import com.example.domain.daytrader.plan.StopRuleType
import com.example.domain.daytrader.replay.ReplayDistributionPoint
import com.example.domain.daytrader.replay.ReplayLean
import com.example.domain.daytrader.replay.ReplayPassMetrics
import com.example.domain.daytrader.replay.ReplayResult
import com.example.domain.daytrader.replay.ReplayVerdict
import com.example.domain.daytrader.risk.RiskBudgetEngine
import com.example.domain.daytrader.risk.RiskSettings
import com.example.domain.model.AssetType
import com.example.domain.model.BestTimeframeSummary
import com.example.domain.model.CandlePoint
import com.example.domain.model.EntryModel
import com.example.domain.model.EntryOrderType
import com.example.domain.model.MarketQuote
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSessionStatus
import com.example.domain.model.OffHoursMode
import com.example.domain.model.PositionSide
import com.example.domain.model.ScenarioPlan
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalEvidence
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.SoftFlag
import com.example.domain.model.SoftFlagType
import com.example.domain.model.Timeframe
import com.example.domain.model.TradeSetupPlan
import com.example.domain.model.TradingMarketType
import com.example.domain.model.VerdictState
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.DayTraderRiskSizerAndActionsCard
import com.example.ui.components.ReplayAgentCard
import com.example.ui.components.StockFundamentalsCard
import com.example.ui.components.TradeExecutionSetupCard
import com.example.ui.screens.ApiManagerScreen
import com.example.ui.screens.CryptoScannerScreen
import com.example.ui.screens.ExecutiveDecisionResultCard
import com.example.ui.screens.NoPlanCard
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiStringLintRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val forbiddenPatterns = listOf(
        "dollar zero (\$0.00)" to Regex("""\$0\.00"""),
        "NaN literal" to Regex("""\bNaN\b"""),
        "Infinity literal" to Regex("""Infinity"""),
        "null literal" to Regex("""\bnull\b"""),
        "duplicate 'Matched a rule:' prefix" to Regex("""Matched a rule:\s*Matched a rule""", RegexOption.IGNORE_CASE)
    )

    private fun collectAllStrings(node: SemanticsNode, out: MutableList<String>) {
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { out.add(it.text) }
        node.config.getOrNull(SemanticsProperties.EditableText)?.let { out.add(it.text) }
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { out.add(it) }
        node.children.forEach { collectAllStrings(it, out) }
    }

    private fun assertNoForbiddenUiStrings(screenLabel: String) {
        composeTestRule.waitForIdle()
        val roots = composeTestRule.onAllNodes(isRoot()).fetchSemanticsNodes()
        val texts = mutableListOf<String>()
        roots.forEach { collectAllStrings(it, texts) }

        val violations = mutableListOf<String>()
        for (txt in texts) {
            for ((name, regex) in forbiddenPatterns) {
                if (regex.containsMatchIn(txt)) {
                    violations.add("$screenLabel -> matched $name in: \"$txt\"")
                }
            }
        }
        assertTrue(
            "UI string lint violations found:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    private fun sampleQuote(symbol: String = "SOL", assetType: AssetType = AssetType.CRYPTO): MarketQuote {
        val now = System.currentTimeMillis()
        val step = Timeframe.ONE_HOUR.stepMillis
        val candles = (0 until 120).map { i ->
            val close = 100.0 + (i * 0.1)
            CandlePoint(now - (120 - i) * step, close - 0.2, close + 0.5, close - 0.5, close, 25_000.0, true, now - (119 - i) * step)
        }
        return MarketQuote(
            symbol = symbol,
            name = symbol,
            assetType = assetType,
            marketType = TradingMarketType.FUTURES,
            currentPrice = 112.0,
            change = 2.4,
            changePercent = 2.18,
            open = 109.6,
            dayHigh = 113.5,
            dayLow = 108.4,
            volume = 2_400_000.0,
            candles = candles,
            barsLoadedCount = candles.size
        )
    }

    private fun sampleTradePlan(verdict: SignalVerdictState): TradeSetupPlan = TradeSetupPlan(
        side = PositionSide.LONG,
        pairSymbol = "SOL",
        currentPrice = 112.0,
        entryZoneStart = 110.5,
        entryZoneEnd = 111.5,
        recommendedEntry = 111.0,
        recommendedLeverage = 5,
        takeProfit1 = 116.0,
        takeProfit2 = 120.0,
        takeProfit3 = 125.0,
        stopLoss = 108.0,
        estimatedLiquidationPrice = 92.0,
        riskRewardRatio = "1 : 1.7",
        maxLossPercent = 13.5,
        expectedGainPercent = 22.5,
        timeframe = Timeframe.ONE_HOUR,
        isAvailable = true,
        entryType = EntryOrderType.LIMIT,
        marketType = TradingMarketType.FUTURES,
        verdictState = verdict,
        routedViaFuturesShort = false
    )

    private fun sampleReplay(): ReplayResult = ReplayResult(
        symbol = "SOL",
        seed = 19L,
        numReplays = 500,
        targetFirstPct = 46.0,
        stopFirstPct = 34.0,
        neitherPct = 20.0,
        greenPct = 55.0,
        medianPrice = 113.4,
        p5Price = 107.2,
        p95Price = 119.8,
        averageR = 0.24,
        netAverageR = 0.21,
        fanChartBands = listOf(
            ReplayDistributionPoint(0, 111.0, 111.0, 111.0, 111.0, 111.0),
            ReplayDistributionPoint(12, 107.2, 110.1, 113.4, 116.2, 119.8)
        ),
        histogramBins = emptyList(),
        verdict = ReplayVerdict(
            lean = ReplayLean.LEANS_TARGET,
            headline = "Leans toward the target (46.0% TP vs 34.0% SL)",
            details = listOf("Both as-is and drift-neutral lean positive."),
            breakEvenTargetShare = 0.37,
            observedTargetShare = 0.575,
            seed = 19L
        ),
        history = null,
        asIs = ReplayPassMetrics("As-Is", 49.0, 31.0, 20.0, 58.0, 114.0, 107.5, 120.2, 0.28, 0.25, 0.61, emptyList(), emptyList(), null, 400, 42),
        driftNeutral = ReplayPassMetrics("Drift-Neutral", 46.0, 34.0, 20.0, 55.0, 113.4, 107.2, 119.8, 0.24, 0.21, 0.575, emptyList(), emptyList(), null, 400, 42),
        effectivePWin = 0.46,
        conservativeModeLabel = "Drift-Neutral",
        oddsDiffPoints = 3.0,
        resolvedCount = 400,
        sessionsCount = 42,
        isWeekendFallbackCombined = true,
        poolNotice = "Weekend pool had 12/20 full sessions — combined with weekday pool (42 sessions)."
    )

    @Test
    fun testLint_TerminalCards_TradeWaitNoTradeAndEmptyStates() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MainViewModel(app)
        val quote = sampleQuote("NVDA", AssetType.STOCK)
        val profile = MarketProfile.defaultUsStock()
        val dtPlan = ExitPlanner.createPlan("NVDA", PositionSide.LONG, 111.0, StopRuleType.MANUAL_PRICE, 108.0, profile = profile)
        val unconfigRisk = RiskBudgetEngine.evaluateRiskAndSize(dtPlan, profile, RiskSettings())
        val smallRisk = RiskBudgetEngine.evaluateRiskAndSize(
            dtPlan,
            profile,
            RiskSettings(accountBalance = 200.0, maxRiskPerTradeDollars = 2.0, isConfigured = true, availableCashOrMargin = 200.0),
            lotStepSize = 1.0
        )

        val bull = ScenarioPlan(true, true, "Break above \$111.50 with RVOL >= 1.2", 108.0, 116.0, "Bullish expansion")
        val bear = ScenarioPlan(false, false, "Lose \$108.00 support with distribution", 112.0, 103.0, "Bearish breakdown")

        val tradeVerdict = VerdictState(
            symbol = "NVDA",
            marketType = TradingMarketType.FUTURES,
            timeframe = Timeframe.ONE_HOUR,
            verdict = SignalVerdictState.TRADE,
            plan = sampleTradePlan(SignalVerdictState.TRADE),
            bullScenario = bull,
            bearScenario = bear,
            evidence = SignalEvidence("A Trend Pullback", "Trending", MarketSessionStatus.OPEN, 42, 26, 61.9, 37.0, 24.9, Pair(46.8, 75.0), 0.42, 4.0, false),
            oneLineWhy = "TRADE [Grade A]: Aligned trend continuation.",
            sessionStatus = MarketSessionStatus.OPEN,
            livePrice = 112.0,
            setupGrade = SetupGrade.A,
            offHoursMode = OffHoursMode.CAUTION,
            aiShiftPoints = 6,
            aiShiftModelName = "gemini-2.5-flash"
        )
        val waitVerdict = tradeVerdict.copy(
            verdict = SignalVerdictState.WAIT,
            setupGrade = SetupGrade.C_WATCH,
            softFlags = listOf(SoftFlag(SoftFlagType.OUTSIDE_ENTRY_WINDOW, "Outside entry window (opens at 14:00)", "Timing hold", "Alert ready")),
            needsCondition = "needs: RVOL >= 1.2 or entry window open",
            routedViaFuturesShort = true,
            evidence = SignalEvidence("D Range Fade", "Range", MarketSessionStatus.OPEN, 14, 5, 37.0, 37.0, 0.0, Pair(17.0, 62.0), -0.05, 4.0, true),
            aiShiftPoints = 0,
            aiShiftModelName = null,
            bestTimeframeSummary = BestTimeframeSummary(Timeframe.FOUR_HOURS, PositionSide.LONG, "A Trend Pullback", SetupGrade.B, "Best now: 4h LONG A Trend Pullback, Grade B", "1h waiting for entry window")
        )
        val noTradeVerdict = VerdictState(
            symbol = "NVDA",
            marketType = TradingMarketType.SPOT,
            timeframe = Timeframe.ONE_HOUR,
            verdict = SignalVerdictState.NO_TRADE,
            vetoes = listOf("History Guard Veto: Insufficient history (80/100 bars)"),
            plan = null,
            bullScenario = bull,
            bearScenario = bear,
            waitingForCondition = "100+ closed bars loaded",
            oneLineWhy = "NO TRADE: Insufficient bar history",
            livePrice = 112.0,
            barsLoaded = 80
        )

        val xbrlResult = StockFundamentalsResult(
            facts = CompanyFactsXbrl(
                symbol = "NVDA",
                cik = "0001045810",
                quarters = listOf(QuarterlyFinancialFact("CY2026Q2", 35_000_000_000.0, 18_000_000_000.0, 0.85, 75.0, 40.0)),
                whatChangedNumbersOnly = listOf("Revenue: \$35.00B vs \$30.00B (+16.7% QoQ)"),
                latestGrossMarginPercent = 75.0,
                latestEpsUsd = 0.85,
                latestYoYGrowthPercent = 40.0
            )
        )
        val unavailableFund = StockFundamentalsResult(unavailableReason = "N/A (SEC User-Agent not configured)")

        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    // TRADE state
                    ExecutiveDecisionResultCard(
                        quote = quote,
                        synthesis = null,
                        uiState = UiState(verdictState = tradeVerdict, tradeSetupPlan = tradeVerdict.plan),
                        viewModel = vm
                    )
                    TradeExecutionSetupCard(
                        setupPlan = tradeVerdict.plan,
                        currentPrice = 112.0,
                        selectedSide = PositionSide.LONG,
                        selectedLeverage = 5,
                        onSelectSide = {},
                        onSelectLeverage = {},
                        onArmPriceAlert = {}
                    )
                    DayTraderRiskSizerAndActionsCard(
                        verdict = SignalVerdictState.TRADE,
                        plan = dtPlan,
                        risk = unconfigRisk,
                        riskSettings = RiskSettings(),
                        onSaveRiskSettings = { _, _ -> },
                        onLogPaperFill = {},
                        onTookThisTrade = {}
                    )
                    // WAIT state + small account + Replay pool notice + Fundamentals
                    ExecutiveDecisionResultCard(
                        quote = quote,
                        synthesis = null,
                        uiState = UiState(verdictState = waitVerdict, tradeSetupPlan = waitVerdict.plan),
                        viewModel = vm
                    )
                    DayTraderRiskSizerAndActionsCard(
                        verdict = SignalVerdictState.WAIT,
                        plan = dtPlan,
                        risk = smallRisk,
                        riskSettings = RiskSettings(accountBalance = 200.0, maxRiskPerTradeDollars = 2.0, isConfigured = true),
                        onSaveRiskSettings = { _, _ -> },
                        onLogPaperFill = {},
                        onTookThisTrade = {}
                    )
                    ReplayAgentCard(
                        replayResult = sampleReplay(),
                        entryPrice = 111.0,
                        stopLossPrice = 108.0,
                        targetPrice = 116.0
                    )
                    StockFundamentalsCard(result = xbrlResult)
                    StockFundamentalsCard(result = unavailableFund)
                    // NO_TRADE state + empty state
                    NoPlanCard(verdictState = noTradeVerdict)
                    NoPlanCard(verdictState = noTradeVerdict.copy(plan = null, vetoes = emptyList()))
                }
            }
        }

        assertNoForbiddenUiStrings("TerminalCards")
    }

    @Test
    fun testLint_ScannerAndSettingsDiagnosticsScreens_DefaultAndPopulatedStates() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = MainViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    CryptoScannerScreen(uiState = vm.uiState.value, viewModel = vm)
                    ApiManagerScreen(uiState = vm.uiState.value, viewModel = vm)
                }
            }
        }

        assertNoForbiddenUiStrings("ScannerAndSettingsDiagnostics")
    }
}
