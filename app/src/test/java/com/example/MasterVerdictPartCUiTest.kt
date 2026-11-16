package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.MasterVerdictEntity
import com.example.data.db.WatchlistItem
import com.example.domain.daytrader.replay.ReplayDistributionPoint
import com.example.domain.master.AgentId
import com.example.domain.master.AgentMatrix
import com.example.domain.master.AgentMatrixCell
import com.example.domain.master.AgentSourceTag
import com.example.domain.master.AgentStatus
import com.example.domain.master.BiasEntry
import com.example.domain.master.HorizonPlan
import com.example.domain.master.MasterVerdict
import com.example.domain.master.OutlookSummary
import com.example.domain.master.OutlookZoneLevel
import com.example.domain.master.ProjectionFuturesView
import com.example.domain.master.ProjectionSpotView
import com.example.domain.master.SpotActionMode
import com.example.domain.master.TimeframeConsensus
import com.example.domain.master.TradingHorizon
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.EntryOrderType
import com.example.domain.model.MarketQuote
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSessionStatus
import com.example.domain.model.PositionSide
import com.example.domain.model.ScenarioPlan
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.SoftFlag
import com.example.domain.model.SoftFlagType
import com.example.domain.model.Timeframe
import com.example.domain.model.TradeSetupPlan
import com.example.domain.model.TradingMarketType
import com.example.domain.model.VerdictState
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.AgentMatrixGrid
import com.example.ui.components.CandlestickChart
import com.example.ui.components.ChartShareHelper
import com.example.ui.components.HorizonLadderTable
import com.example.ui.components.MasterVerdictCard
import com.example.ui.components.OutlookCard
import com.example.ui.components.layoutNonOverlappingLabelYs
import com.example.ui.screens.ApiManagerScreen
import com.example.ui.screens.ExecutiveDecisionResultCard
import com.example.ui.screens.FavoritesScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MasterVerdictPartCUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val forbiddenPatterns = listOf(
        "dollar zero (\$0.00)" to Regex("""\$0\.00"""),
        "NaN literal" to Regex("""\bNaN\b"""),
        "Infinity literal" to Regex("""Infinity"""),
        "null literal" to Regex("""\bnull\b""")
    )

    private fun collectAllStrings(node: SemanticsNode, out: MutableList<String>) {
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { out.add(it.text) }
        node.config.getOrNull(SemanticsProperties.EditableText)?.let { out.add(it.text) }
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { out.add(it) }
        node.children.forEach { collectAllStrings(it, out) }
    }

    private fun assertNoForbiddenStrings(label: String): List<String> {
        composeTestRule.waitForIdle()
        val roots = composeTestRule.onAllNodes(isRoot()).fetchSemanticsNodes()
        val texts = mutableListOf<String>()
        roots.forEach { collectAllStrings(it, texts) }
        val violations = mutableListOf<String>()
        for (txt in texts) {
            for ((name, regex) in forbiddenPatterns) {
                if (regex.containsMatchIn(txt)) {
                    violations.add("$label -> matched $name in \"$txt\"")
                }
            }
        }
        assertTrue("Forbidden UI strings found:\n${violations.joinToString("\n")}", violations.isEmpty())
        return texts
    }

    private fun buildSampleHorizonPlan(
        horizon: TradingHorizon,
        state: SignalVerdictState = SignalVerdictState.TRADE,
        side: PositionSide? = PositionSide.LONG,
        marketType: TradingMarketType = TradingMarketType.FUTURES,
        spotMode: SpotActionMode = SpotActionMode.BUY
    ): HorizonPlan {
        val now = System.currentTimeMillis()
        val entry = if (state == SignalVerdictState.NO_TRADE) null else 68400.0
        val stop = if (state == SignalVerdictState.NO_TRADE) null else if (side == PositionSide.SHORT) 69500.0 else 67200.0
        val tp1 = if (state == SignalVerdictState.NO_TRADE) null else if (side == PositionSide.SHORT) 66500.0 else 70800.0
        val tp2 = if (state == SignalVerdictState.NO_TRADE) null else if (side == PositionSide.SHORT) 65000.0 else 72200.0
        val tp3 = if (state == SignalVerdictState.NO_TRADE) null else if (side == PositionSide.SHORT) 63500.0 else 74000.0
        val futView = if (marketType == TradingMarketType.FUTURES && entry != null && stop != null && tp1 != null && tp2 != null && tp3 != null && side != null) {
            ProjectionFuturesView(
                side = side,
                entryZoneMin = entry - 150.0,
                entryZoneMax = entry + 150.0,
                limitPrice = entry,
                stopLoss = stop,
                tp1 = tp1,
                tp2 = tp2,
                tp3 = tp3,
                tp1R = 2.0,
                tp2R = 3.2,
                tp3R = 4.6,
                tp1UnleveragedPct = 3.5,
                tp2UnleveragedPct = 5.5,
                tp3UnleveragedPct = 8.1,
                recommendedLeverage = 5,
                liquidationPrice = if (side == PositionSide.LONG) 56000.0 else 81000.0,
                positionSizeContracts = 0.25,
                positionSizeUsd = 17100.0,
                leverageSourceLabel = "Engine rec"
            )
        } else null
        val spotView = when (spotMode) {
            SpotActionMode.BUY -> ProjectionSpotView(
                mode = SpotActionMode.BUY,
                headline = "Spot BUY near $68,250–$68,550",
                buyZoneMin = 68250.0,
                buyZoneMax = 68550.0,
                buyLimitPrice = 68400.0,
                invalidationPrice = 67200.0,
                doNotBuyAbove = 69100.0,
                sellTargets = listOf(70800.0, 72200.0, 74000.0)
            )
            SpotActionMode.SELL_REDUCE_AND_WAIT_BUY -> ProjectionSpotView(
                mode = SpotActionMode.SELL_REDUCE_AND_WAIT_BUY,
                headline = "Spot: reduce/avoid longs; short thesis belongs on Futures",
                reduceZoneMin = 68900.0,
                reduceZoneMax = 69400.0,
                waitBuyWhy = "Downside liquidity sweep in progress",
                waitBuyTrigger = "Wait for 1h bullish reclaim above $69,500",
                futuresShortAvailable = true
            )
            SpotActionMode.NO_SPOT_PLAN -> ProjectionSpotView(
                mode = SpotActionMode.NO_SPOT_PLAN,
                headline = "Spot: wait for clean structure",
                reasonIfNone = "Regime compressed"
            )
        }
        return HorizonPlan(
            horizon = horizon,
            biasTf = horizon.defaultBiasTf,
            setupTf = horizon.defaultSetupTf,
            triggerTf = horizon.defaultTriggerTf,
            side = side,
            state = state,
            grade = if (state == SignalVerdictState.TRADE) SetupGrade.A else SetupGrade.C_WATCH,
            entryModel = "A_TREND_PULLBACK",
            regime = MarketRegime.TREND,
            sessionStatus = MarketSessionStatus.OPEN,
            triggerPrice = entry,
            triggerCondition = if (side == PositionSide.SHORT) "Break & 15m close below $68,250" else "Pullback into $68,250–$68,550 with bullish displacement",
            entryZoneMin = entry?.minus(150.0),
            entryZoneMax = entry?.plus(150.0),
            limitPrice = entry,
            stopLoss = stop,
            tp1 = tp1,
            tp2 = tp2,
            tp3 = tp3,
            tp1Rr = tp1?.let { 2.0 },
            tp2Rr = tp2?.let { 3.2 },
            tp3Rr = tp3?.let { 4.6 },
            tp1TouchProb = 0.62,
            tp2TouchProb = 0.44,
            tp3TouchProb = 0.28,
            pWin = 0.61,
            winsK = 22,
            sampleN = 36,
            wilsonLowPct = 45.0,
            wilsonHighPct = 75.0,
            evR = 0.58,
            replaySeed = 19L,
            replayResolvedN = 10_000,
            replayHeadline = "Replay (n=10000, Seed 19L): TP1 first 61% vs SL 39%",
            replayConservativeMode = "Drift-neutral",
            replayTargetFirstPct = 61.0,
            replayStopFirstPct = 39.0,
            replayFanBands = listOf(
                ReplayDistributionPoint(barIndex = 0, p5 = 68400.0, p25 = 68400.0, p50 = 68400.0, p75 = 68400.0, p95 = 68400.0),
                ReplayDistributionPoint(barIndex = 8, p5 = 67000.0, p25 = 68200.0, p50 = 69100.0, p75 = 70200.0, p95 = 71600.0)
            ),
            validUntilMs = now + 12L * 3600_000L,
            needs = if (state == SignalVerdictState.TRADE) "Ready when limit fills" else "Needs 15m MSS above $68,550",
            conflicts = listOf("4h supply overhead near $71,000"),
            vetoes = if (state == SignalVerdictState.NO_TRADE) listOf("Data integrity: gap detected") else emptyList(),
            softFlags = if (state == SignalVerdictState.WAIT) listOf(SoftFlag(SoftFlagType.LOW_RVOL, "RVOL 0.72x", "Volume slightly light", "Wait for RVOL >= 1.0")) else emptyList(),
            agentVotes = mapOf(AgentId.MTF_BIAS to 0.75, AgentId.STRUCTURE_SMC to 0.68),
            dataAgeMs = 15_000L,
            computedAtMs = now,
            isCounterTrend = false,
            sizeMultiplier = 1.0,
            isStale = false,
            farZones = emptyList(),
            bslLevel = 70900.0,
            sslLevel = 67100.0,
            futuresView = futView,
            spotView = spotView
        )
    }

    private fun buildSampleMatrix(): AgentMatrix {
        val tfs = listOf(
            Timeframe.FIVE_MIN,
            Timeframe.FIFTEEN_MIN,
            Timeframe.ONE_HOUR,
            Timeframe.FOUR_HOURS,
            Timeframe.ONE_DAY,
            Timeframe.FIVE_DAYS,
            Timeframe.ONE_MONTH
        )
        val cells = mutableListOf<AgentMatrixCell>()
        for (agent in AgentId.values()) {
            for (tf in tfs) {
                val isFailed = agent == AgentId.DERIVATIVES && tf == Timeframe.FIVE_MIN
                cells += AgentMatrixCell(
                    agentId = agent,
                    timeframe = tf,
                    stance = if (isFailed) 0.0 else 0.65,
                    strength = if (isFailed) 0.0 else 0.75,
                    freshnessMs = 12_000L,
                    reasons = if (isFailed) emptyList() else listOf("${agent.shortName} aligned at 68,400"),
                    vetoes = emptyList(),
                    sourceTag = if (agent == AgentId.AI_AGENT) AgentSourceTag.AI else AgentSourceTag.ENGINE,
                    status = if (isFailed) AgentStatus.FAILED else AgentStatus.OK,
                    errorMessage = if (isFailed) "Funding endpoint HTTP 429 timeout" else null
                )
            }
        }
        val consensus = tfs.associateWith { tf ->
            TimeframeConsensus(
                timeframe = tf,
                tfDisplayLabel = if (tf == Timeframe.FIVE_DAYS) "1W" else tf.label,
                biasLabel = "BULL",
                agreementPercent = 78,
                netScore = 0.62
            )
        }
        return AgentMatrix(
            timeframes = tfs,
            cells = cells,
            consensusByTf = consensus,
            failedCount = 1,
            whatWouldChangeThis = listOf("Loss of $67,200 invalidates bullish ladder")
        )
    }

    private fun buildSampleMasterVerdict(
        state: SignalVerdictState = SignalVerdictState.TRADE,
        marketType: TradingMarketType = TradingMarketType.FUTURES,
        side: PositionSide? = PositionSide.LONG,
        spotMode: SpotActionMode = SpotActionMode.BUY
    ): MasterVerdict {
        val plans = TradingHorizon.ACTIONABLE.associateWith { h ->
            buildSampleHorizonPlan(h, state = state, side = side, marketType = marketType, spotMode = spotMode)
        }
        return MasterVerdict(
            symbol = "BTC",
            assetType = AssetType.CRYPTO,
            marketType = marketType,
            livePrice = 68500.0,
            headline = when {
                marketType == TradingMarketType.SPOT && spotMode == SpotActionMode.SELL_REDUCE_AND_WAIT_BUY ->
                    "BTC Spot: SELL / REDUCE bias, 75% aligned"
                side == PositionSide.SHORT -> "BTC Futures: SHORT bias, 78% aligned"
                else -> "BTC Futures: LONG bias, 78% aligned"
            },
            weightedBiasSide = side,
            alignmentPercent = 78,
            biasLadder = listOf(
                BiasEntry(Timeframe.ONE_HOUR, "1h", "BULL", 0.72, "EMA20 > EMA50")
            ),
            horizonPlans = plans,
            bestHorizon = TradingHorizon.INTRADAY,
            bestPlan = plans[TradingHorizon.INTRADAY],
            outlook = OutlookSummary(
                timeframesCovered = listOf("1M", "3M", "1Y"),
                trendBias = "BULL",
                biasStrength = 0.74,
                keySupports = listOf(64200.0, 61000.0),
                keyResistances = listOf(72500.0, 76000.0),
                accumulationZones = listOf(OutlookZoneLevel(63500.0, 65000.0, "Weekly Demand", "ACCUMULATION")),
                distributionZones = listOf(OutlookZoneLevel(74000.0, 76500.0, "ATH Supply", "DISTRIBUTION")),
                summaryText = "Higher-timeframe bull trend intact above $64,200."
            ),
            matrix = buildSampleMatrix(),
            conflicts = listOf("5m derivatives rate-limited"),
            plainSummaryHeadline = "BTC bullish pullback setup across Intraday & Swing",
            plainBestPlanNumbers = "Best Intraday: Entry $68,250–$68,550 • Stop $67,200 • TP1 $70,800 (2.0R)",
            plainInvalidation = "Thesis invalidates below $67,200 close.",
            whatFlipsBias = "Daily close below $65,000 shifts bias bearish.",
            standardModeNote = "Standard 15m mode: 5m triggers update on 15m cycles.",
            isStale = false,
            seed = 19L
        )
    }

    @Test
    fun masterVerdictComponents_renderZeroForbiddenStringsAcrossTradeWaitNoTradeAndSpot() {
        val variants = listOf(
            "TRADE Futures LONG" to buildSampleMasterVerdict(SignalVerdictState.TRADE, TradingMarketType.FUTURES, PositionSide.LONG),
            "WAIT Futures LONG" to buildSampleMasterVerdict(SignalVerdictState.WAIT, TradingMarketType.FUTURES, PositionSide.LONG),
            "NO_TRADE Futures" to buildSampleMasterVerdict(SignalVerdictState.NO_TRADE, TradingMarketType.FUTURES, null, SpotActionMode.NO_SPOT_PLAN),
            "Spot Bearish Switch" to buildSampleMasterVerdict(SignalVerdictState.WAIT, TradingMarketType.SPOT, PositionSide.SHORT, SpotActionMode.SELL_REDUCE_AND_WAIT_BUY)
        )

        var activeHorizon = TradingHorizon.INTRADAY
        val stateHolder = androidx.compose.runtime.mutableStateOf(variants.first().second)

        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    val mv = stateHolder.value
                    MasterVerdictCard(
                        verdict = mv,
                        selectedHorizon = activeHorizon,
                        isArmedForSelectedHorizon = true,
                        futuresPerpAvailable = true,
                        isEngineOnly = true,
                        onSelectHorizon = { activeHorizon = it },
                        onArmAlert = {},
                        onPaperFollow = {},
                        onSwitchToFuturesShort = {},
                        onShareFeedback = {}
                    )
                    HorizonLadderTable(
                        verdict = mv,
                        selectedHorizon = activeHorizon,
                        onSelectHorizon = { activeHorizon = it },
                        onShareFeedback = {}
                    )
                    AgentMatrixGrid(matrix = mv.matrix)
                    OutlookCard(outlook = mv.outlook)
                }
            }
        }

        for ((label, mv) in variants) {
            composeTestRule.runOnUiThread {
                stateHolder.value = mv
            }
            assertNoForbiddenStrings(label)
        }
    }

    @Test
    fun horizonRowClick_updatesSelectedHorizon() {
        val mv = buildSampleMasterVerdict()
        val selected = androidx.compose.runtime.mutableStateOf(TradingHorizon.INTRADAY)

        composeTestRule.setContent {
            MyApplicationTheme {
                HorizonLadderTable(
                    verdict = mv,
                    selectedHorizon = selected.value,
                    onSelectHorizon = { selected.value = it },
                    onShareFeedback = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("horizon_row_swing").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(TradingHorizon.SWING, selected.value)
    }

    @Test
    fun tappingFailedMatrixCell_opensAgentDetailDialog() {
        val mv = buildSampleMasterVerdict()

        composeTestRule.setContent {
            MyApplicationTheme {
                AgentMatrixGrid(matrix = mv.matrix)
            }
        }

        composeTestRule.onNodeWithTag("matrix_cell_derivatives_five_min").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            composeTestRule.onAllNodesWithTag("agent_cell_detail_dialog")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("agent_cell_detail_dialog").assertExists()
        assertTrue(
            composeTestRule.onAllNodesWithText("Funding endpoint HTTP 429 timeout", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            composeTestRule.onAllNodesWithText("Engine", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun chartLabelCollisionHelper_maintainsMinimumSpacingAndBounds() {
        val adjusted = layoutNonOverlappingLabelYs(
            idealYs = listOf(100f, 102f, 104f, 105f, 380f, 382f),
            minY = 40f,
            maxY = 400f,
            minSpacingPx = 32f
        )
        assertEquals(6, adjusted.size)
        val sorted = adjusted.sorted()
        assertTrue("Lowest label should stay >= minY", sorted.first() >= 39.9f)
        assertTrue("Highest label should stay <= maxY", sorted.last() <= 400.1f)
        for (i in 1 until sorted.size) {
            val gap = sorted[i] - sorted[i - 1]
            assertTrue("Adjacent chart labels collided: gap=$gap < 31.5f", gap >= 31.5f)
        }
    }

    @Test
    fun waitPlanInZone_neverDisplaysActiveInZoneBadge() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = MainViewModel(context)
        val now = System.currentTimeMillis()
        val step = Timeframe.ONE_HOUR.stepMillis
        val candles = (0 until 120).map { i ->
            val close = 100.0 + (i * 0.05)
            CandlePoint(now - (120 - i) * step, close - 0.1, close + 0.3, close - 0.3, close, 10_000.0, true, now - (119 - i) * step)
        }
        val quote = MarketQuote(
            symbol = "ETH",
            name = "Ethereum",
            assetType = AssetType.CRYPTO,
            marketType = TradingMarketType.FUTURES,
            currentPrice = 105.0,
            change = 1.2,
            changePercent = 1.15,
            open = 103.8,
            dayHigh = 106.5,
            dayLow = 102.5,
            volume = 500_000.0,
            marketCap = 300e9,
            candles = candles,
            primarySource = "Binance Futures"
        )
        // Price is 105.0 which is INSIDE [104.0..106.0], yet verdict is WAIT (unarmed)
        val waitSetup = TradeSetupPlan(
            side = PositionSide.LONG,
            pairSymbol = "ETH",
            marketType = TradingMarketType.FUTURES,
            currentPrice = 105.0,
            entryZoneStart = 104.0,
            entryZoneEnd = 106.0,
            recommendedEntry = 105.0,
            recommendedLeverage = 10,
            takeProfit1 = 109.0,
            takeProfit2 = 112.0,
            takeProfit3 = 116.0,
            stopLoss = 103.0,
            estimatedLiquidationPrice = 95.0,
            riskRewardRatio = "1 : 2.0",
            maxLossPercent = 1.9,
            expectedGainPercent = 3.8,
            reasoning = "Conditional pullback wait",
            invalidationPrice = 103.0,
            timeframe = Timeframe.ONE_HOUR,
            isAvailable = true,
            entryType = EntryOrderType.LIMIT,
            verdictState = SignalVerdictState.WAIT
        )
        val vs = VerdictState(
            symbol = "ETH",
            marketType = TradingMarketType.FUTURES,
            timeframe = Timeframe.ONE_HOUR,
            verdict = SignalVerdictState.WAIT,
            plan = waitSetup,
            bullScenario = ScenarioPlan(true, true, "Break above $106.0", 103.0, 109.0, "Bull"),
            bearScenario = ScenarioPlan(false, false, "Break below $103.0", 106.0, 99.0, "Bear"),
            waitingForCondition = "1h close above $106.0",
            livePrice = 105.0,
            barsLoaded = 120
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    ExecutiveDecisionResultCard(
                        quote = quote,
                        synthesis = null,
                        uiState = UiState(
                            selectedSymbol = "ETH",
                            selectedTimeframe = Timeframe.ONE_HOUR,
                            selectedMarketType = TradingMarketType.FUTURES,
                            currentQuote = quote,
                            tradeSetupPlan = waitSetup,
                            verdictState = vs,
                            masterVerdict = null
                        ),
                        viewModel = viewModel
                    )
                }
            }
        }

        val allTexts = assertNoForbiddenStrings("WAIT in-zone badge check")
        assertFalse("WAIT plan must never display 'IN ZONE' badge", allTexts.any { it == "IN ZONE" || it == "LIVE IN ENTRY ZONE — ACTIONABLE NOW" })
        assertTrue("Expected 'Engine rec' when engine-only", allTexts.any { it.contains("Engine rec") })
        assertTrue("Expected renamed 'Price action flow:' label", allTexts.any { it.contains("Price action flow:") })
    }

    private fun buildSampleFavoritesAndSettingsState(): Pair<MainViewModel, UiState> {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = MainViewModel(context)
        val mv = buildSampleMasterVerdict()
        val now = System.currentTimeMillis()
        val sampleWatchlist = listOf(
            WatchlistItem(symbol = "BTC", name = "Bitcoin", assetType = AssetType.CRYPTO.name),
            WatchlistItem(symbol = "NVDA", name = "NVIDIA", assetType = AssetType.STOCK.name),
            WatchlistItem(symbol = "SOL", name = "Solana", assetType = AssetType.CRYPTO.name)
        )
        val cached = listOf(
            MasterVerdictEntity("BTC", "FUTURES", "{}", "BTC Futures: LONG bias", "LONG", "A", 78, "FRESH", null, 19L, now - 120_000L),
            MasterVerdictEntity("NVDA", "SPOT", "{}", "NVDA Spot: WAIT", "LONG", "B", 62, "STALE", null, 19L, now - 3600_000L),
            MasterVerdictEntity("SOL", "FUTURES", "{}", "SOL Futures: FAILED", "NONE", "C", 0, "FAILED", "HTTP 429 rate limited", 19L, now - 60_000L)
        )
        val state = UiState(
            watchlist = sampleWatchlist,
            cachedMasterVerdicts = cached,
            masterVerdict = mv
        )
        return viewModel to state
    }

    @Test
    fun favoritesScreen_rendersCachedMasterVerdictsAndRefreshButton() {
        val (viewModel, state) = buildSampleFavoritesAndSettingsState()

        composeTestRule.setContent {
            MyApplicationTheme {
                FavoritesScreen(uiState = state, viewModel = viewModel)
            }
        }
        val favTexts = assertNoForbiddenStrings("FavoritesScreen")
        assertTrue(favTexts.any { it.contains("BTC Futures: LONG bias") })
        assertTrue(favTexts.any { it.contains("HTTP 429 rate limited") })
        composeTestRule.onNodeWithTag("btn_refresh_watchlist").assertIsDisplayed()
    }

    @Test
    fun apiManagerScreen_rendersHorizonTripletsBackgroundControlsAndAttribution() {
        val (viewModel, state) = buildSampleFavoritesAndSettingsState()

        composeTestRule.setContent {
            MyApplicationTheme {
                ApiManagerScreen(uiState = state, viewModel = viewModel)
            }
        }
        composeTestRule.onNodeWithTag("horizon_settings_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("background_monitoring_settings_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Ideas adapted from day-trader-skill (MIT).").performScrollTo().assertIsDisplayed()
        assertNoForbiddenStrings("ApiManagerScreen")
    }

    @Test
    fun chartShareHelper_formatsTextAndRendersValidBitmapAndCandlestickChartRendersOverlays() {
        val mv = buildSampleMasterVerdict()
        val formatted = ChartShareHelper.formatMasterVerdictAsText(mv)
        assertTrue(formatted.contains("BTC Futures: LONG bias"))
        assertTrue(formatted.contains("Probabilistic scenario with measured odds"))
        assertFalse(formatted.contains("$0.00"))
        assertFalse(formatted.contains("NaN"))
        assertFalse(formatted.contains("Infinity"))

        val bitmap = ChartShareHelper.renderHorizonLadderBitmap(mv)
        assertTrue("Rendered share bitmap must have width > 0", bitmap.width > 0)
        assertTrue("Rendered share bitmap must have height > 0", bitmap.height > 0)

        val now = System.currentTimeMillis()
        val step = Timeframe.ONE_HOUR.stepMillis
        val candles = (0 until 60).map { i ->
            val close = 68_000.0 + i * 12.0
            CandlePoint(now - (60 - i) * step, close - 25.0, close + 40.0, close - 45.0, close, 1200.0 + i * 10, true, now - (59 - i) * step)
        }
        composeTestRule.setContent {
            MyApplicationTheme {
                CandlestickChart(
                    candles = candles,
                    currentPrice = 68500.0,
                    masterVerdict = mv,
                    selectedHorizon = TradingHorizon.INTRADAY
                )
            }
        }
        val chartTexts = assertNoForbiddenStrings("CandlestickChart with MasterVerdict overlays")
        assertTrue(chartTexts.any { it.contains("Replay range, not a prediction") || it.contains("Replay cone") || it.contains("Zones") })
    }
}
