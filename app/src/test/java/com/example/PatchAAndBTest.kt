package com.example

import com.example.data.api.MarketDataService
import com.example.domain.daytrader.fundamentals.CompanyFactsEngine
import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.model.MarketProfileType
import com.example.domain.daytrader.model.TimeWindow
import com.example.domain.daytrader.plan.ExitPlanner
import com.example.domain.daytrader.plan.StopRuleType
import com.example.domain.daytrader.replay.SessionPool
import com.example.domain.daytrader.risk.RiskBudgetEngine
import com.example.domain.daytrader.risk.RiskSettings
import com.example.domain.daytrader.scanner.DayTraderScannerEngine
import com.example.domain.engine.SignalCalibrationEngine
import com.example.domain.engine.SignalEngineV2
import com.example.domain.engine.SignalVetoDetector
import com.example.domain.engine.UnifiedEntryEngine
import com.example.domain.model.AgentStance
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.MarketQuote
import com.example.domain.model.OffHoursMode
import com.example.domain.model.OrderFlowReport
import com.example.domain.model.PositionSide
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.SoftFlagType
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import com.example.domain.analytics.QuantitativeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

class PatchAAndBTest {

    private fun buildClosedCandles(
        startPrice: Double,
        count: Int,
        timeframe: Timeframe,
        nowMs: Long = 1_763_130_600_000L, // Friday 14:30 UTC (09:30 ET - US market open)
        lastBarCloseAgeMs: Long = timeframe.stepMillis / 2L,
        stepPct: Double = -0.001
    ): List<CandlePoint> {
        val interval = timeframe.stepMillis.coerceAtLeast(60_000L)
        val lastCloseTs = nowMs - lastBarCloseAgeMs
        val lastOpenTs = lastCloseTs - interval
        val firstOpenTs = lastOpenTs - ((count - 1).toLong() * interval)
        val list = mutableListOf<CandlePoint>()
        var price = startPrice
        for (i in 0 until count) {
            val openTs = firstOpenTs + (i * interval)
            val closeTs = openTs + interval
            val close = price * (1.0 + stepPct)
            val high = maxOf(price, close) * 1.004
            val low = minOf(price, close) * 0.996
            list.add(
                CandlePoint(
                    timestamp = openTs,
                    open = price,
                    high = high,
                    low = low,
                    close = close,
                    volume = 50_000.0 + (i % 4) * 5_000.0,
                    isClosed = true,
                    closeTimestamp = closeTs
                )
            )
            price = close
        }
        return list
    }

    // ==================== PATCH A #1: Shorts route to Futures ====================

    @Test
    fun testPatchA1_SpotShortRoutesToFuturesPerp_HardVetoOnlyIfNoPerp() {
        val nowMs = 1_763_130_600_000L
        val candles = buildClosedCandles(startPrice = 100.0, count = 130, timeframe = Timeframe.ONE_HOUR, nowMs = nowMs, stepPct = -0.0015)
        val livePrice = candles.last().close

        // 1. Crypto/Stock with perp available: Spot + Short routes cleanly to Futures (perp)
        val routedOutput = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "SOL",
                currentPrice = livePrice,
                timeframe = Timeframe.ONE_HOUR,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.CRYPTO,
                candles = candles,
                futuresPerpAvailable = true,
                preferredSide = PositionSide.SHORT,
                currentTimeMillis = nowMs
            )
        )
        assertTrue("Routed short must set routedViaFuturesShort = true", routedOutput.routedViaFuturesShort)
        assertFalse(
            "Spot Short veto must be deleted when perp exists",
            routedOutput.vetoReasons.any { it.contains("Spot Short", ignoreCase = true) || it.contains("Spot only supports Longs", ignoreCase = true) }
        )
        assertTrue(
            "Why line must label SHORT via Futures (perp)",
            routedOutput.oneLineWhy.contains("SHORT via Futures (perp)")
        )

        // 2. UnifiedEntryEngine also routes to FUTURES market and exposes non-restricted setup
        val liveNowMs = System.currentTimeMillis()
        val liveCandles = buildClosedCandles(startPrice = 100.0, count = 130, timeframe = Timeframe.ONE_HOUR, nowMs = liveNowMs, stepPct = -0.0015)
        val routedPlan = UnifiedEntryEngine.buildTradeSetupPlan(
            UnifiedEntryEngine.EntryEngineInputs(
                symbol = "SOL",
                currentPrice = liveCandles.last().close,
                side = PositionSide.SHORT,
                timeframe = Timeframe.ONE_HOUR,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.CRYPTO,
                candles = liveCandles,
                futuresPerpAvailable = true
            )
        )
        assertTrue(routedPlan.routedViaFuturesShort)
        assertEquals(TradingMarketType.FUTURES, routedPlan.marketType)
        assertFalse(routedPlan.isSpotRestricted)

        // 3. Symbol with NO perp market -> hard veto "No futures market for <symbol>"
        val noPerpOutput = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "TINYCOIN",
                currentPrice = livePrice,
                timeframe = Timeframe.ONE_HOUR,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.CRYPTO,
                candles = candles,
                futuresPerpAvailable = false,
                preferredSide = PositionSide.SHORT,
                currentTimeMillis = nowMs
            )
        )
        assertEquals(SignalVerdictState.NO_TRADE, noPerpOutput.verdictState)
        assertTrue(noPerpOutput.vetoReasons.any { it == "No futures market for TINYCOIN" })
    }

    @Test
    fun testPatchA1_ScannerSurfacesShortsOnSellOffInSpotModeViaPerpRouting() {
        val nowMs = System.currentTimeMillis()
        val selloffDaily = buildClosedCandles(startPrice = 140.0, count = 35, timeframe = Timeframe.ONE_DAY, nowMs = nowMs, stepPct = -0.012)
        val selloff15m = buildClosedCandles(startPrice = 100.0, count = 130, timeframe = Timeframe.FIFTEEN_MIN, nowMs = nowMs, stepPct = -0.002)
        val selloff1h = buildClosedCandles(startPrice = 110.0, count = 130, timeframe = Timeframe.ONE_HOUR, nowMs = nowMs, stepPct = -0.002)

        val bundle = DayTraderScannerEngine.SymbolChartBundle(
            symbol = "AVAX",
            name = "Avalanche",
            assetType = AssetType.CRYPTO,
            dailyCandles = selloffDaily,
            fiveMinuteCandles = selloff15m,
            volume24hUsd = 800_000_000.0,
            currentPrice = selloff15m.last().close,
            setupCandles15m = selloff15m,
            htfCandles1h = selloff1h,
            change24hPercent = -6.5
        )

        val scan = DayTraderScannerEngine.scanSetupsAndMarketSummary(
            bundles = listOf(bundle),
            marketType = TradingMarketType.SPOT,
            universeLabel = "Crypto Spot Universe",
            futuresPerpChecker = { sym, _ -> sym == "AVAX" }
        )

        val avaxSetup = scan.setups.firstOrNull { it.symbol == "AVAX" }
        assertNotNull("Scanner must evaluate AVAX bundle", avaxSetup)
        assertNotEquals("Sell-off with perp available must not become NO_TRADE due to Spot", SignalVerdictState.NO_TRADE, avaxSetup!!.verdict)
    }

    // ==================== PATCH A #2: R:R is geometry, not a veto ====================

    @Test
    fun testPatchA2_RrGeometrySolverComputesRequiredEntryOrOpposingWatchPlan() {
        // Case 1: Live entry gives < 1.5R (entry=100, stop=96 -> risk=4, tp1=104 -> R=1.0).
        // Solver computes (104 + 1.5*96)/2.5 = 99.20, which gives R=(104-99.2)/(99.2-96) = 4.8/3.2 = 1.50!
        val solved = SignalEngineV2.resolveGeometryForTargetR(
            entryPrice = 100.0,
            stopLossPrice = 96.0,
            takeProfit1 = 104.0,
            takeProfit2 = 108.0,
            takeProfit3 = 112.0,
            oppExtreme = 104.0,
            livePrice = 100.0,
            atr = 2.0,
            isLong = true,
            targetR = 1.50
        )
        assertTrue("Entry should be solved downward for LONG pullback", solved.wasAdjusted)
        assertEquals(99.20, solved.entryPrice, 0.001)
        assertEquals(1.50, solved.netRrTp1, 0.01)
        assertEquals(SetupGrade.C_WATCH, solved.grade)
        assertTrue("Condition must state needs price at ...", solved.needsCondition.contains("needs price at", ignoreCase = true))
        assertTrue(solved.softFlags.any { it.type == SoftFlagType.RR_BELOW_1_5 })

        // Case 2: Required entry is beyond reach (max drift = min(5*ATR=2.5, 25% of 100) = 2.5;
        // entry=100, stop=90, tp1=106 -> solvedEntry=(106+135)/2.5=96.4 -> drift 3.6 > 2.5).
        // Falls back to nearest opposing level with real R:R and grade C / WATCH (no NO_TRADE).
        val outOfReach = SignalEngineV2.resolveGeometryForTargetR(
            entryPrice = 100.0,
            stopLossPrice = 90.0,
            takeProfit1 = 106.0,
            takeProfit2 = 110.0,
            takeProfit3 = 115.0,
            oppExtreme = 105.0,
            livePrice = 100.0,
            atr = 0.5,
            isLong = true,
            targetR = 1.50
        )
        assertFalse(outOfReach.wasAdjusted)
        assertEquals(105.0, outOfReach.tp1, 0.001)
        assertEquals(0.5, outOfReach.netRrTp1, 0.01)
        assertEquals(SetupGrade.C_WATCH, outOfReach.grade)
        assertTrue(outOfReach.softFlags.any { it.type == SoftFlagType.RR_BELOW_1_5 })
    }

    // ==================== PATCH A #3: Basis informational + mixed flag ====================

    @Test
    fun testPatchA3_BasisNeverVetoesFutures_MixedSpotDataFlagsSoftFlag() {
        val nowMs = 1_763_130_600_000L
        val candles = buildClosedCandles(startPrice = 100.0, count = 130, timeframe = Timeframe.ONE_HOUR, nowMs = nowMs)
        val indicators = QuantitativeEngine.computeIndicators(candles)

        // Large basis (spot=90, perp=100 vs ~2 ATR stop) must NOT veto in Futures mode
        val vetoResult = SignalVetoDetector.evaluateL0Vetoes(
            symbol = "SOL",
            side = PositionSide.LONG,
            assetType = AssetType.CRYPTO,
            marketType = TradingMarketType.FUTURES,
            livePrice = 100.0,
            candles = candles,
            indicators = indicators,
            timeframe = Timeframe.ONE_HOUR,
            perpPrice = 100.0,
            spotPrice = 90.0,
            dataMixedFromSpot = false,
            currentTimeMillis = nowMs
        )
        assertFalse("Basis in Futures mode must never cause a hard veto", vetoResult.isVetoed)
        assertFalse(vetoResult.softFlags.any { it.type == SoftFlagType.MIXED_DATA_EXECUTION })

        // Mixing spot data with perp execution emits soft flag MIXED_DATA_EXECUTION (still not NO_TRADE)
        val mixedResult = SignalVetoDetector.evaluateL0Vetoes(
            symbol = "SOL",
            side = PositionSide.LONG,
            assetType = AssetType.CRYPTO,
            marketType = TradingMarketType.FUTURES,
            livePrice = 100.0,
            candles = candles,
            indicators = indicators,
            timeframe = Timeframe.ONE_HOUR,
            perpPrice = 100.0,
            spotPrice = 100.0,
            dataMixedFromSpot = true,
            currentTimeMillis = nowMs
        )
        assertFalse("Mixed data is a soft flag, not a hard veto", mixedResult.isVetoed)
        assertTrue(mixedResult.softFlags.any { it.type == SoftFlagType.MIXED_DATA_EXECUTION })
    }

    // ==================== PATCH A #4: Stale-bar check 1.9 intervals fresh ====================

    @Test
    fun testPatchA4_StaleBarCheck_1Point9IntervalsIsFreshOn1m5m1h_OverLimitIsStale() {
        val nowMs = 1_763_130_600_000L
        for (tf in listOf(Timeframe.ONE_MIN, Timeframe.FIVE_MIN, Timeframe.ONE_HOUR)) {
            val freshAge = (tf.stepMillis * 1.9).toLong()
            val freshCandles = buildClosedCandles(
                startPrice = 100.0,
                count = 120,
                timeframe = tf,
                nowMs = nowMs,
                lastBarCloseAgeMs = freshAge
            )
            val freshIndicators = QuantitativeEngine.computeIndicators(freshCandles)
            val freshResult = SignalVetoDetector.evaluateL0Vetoes(
                symbol = "BTC",
                side = PositionSide.LONG,
                assetType = AssetType.CRYPTO,
                marketType = TradingMarketType.FUTURES,
                livePrice = freshCandles.last().close,
                candles = freshCandles,
                indicators = freshIndicators,
                timeframe = tf,
                currentTimeMillis = nowMs
            )
            assertFalse(
                "Bar closed 1.9 intervals ago on ${tf.label} must be FRESH",
                freshResult.hardVetoes.any { it.contains("Stale data", ignoreCase = true) }
            )

            // Past 2 * interval + 15s -> Stale hard veto
            val staleAge = (tf.stepMillis * 2.2).toLong() + 20_000L
            val staleCandles = buildClosedCandles(
                startPrice = 100.0,
                count = 120,
                timeframe = tf,
                nowMs = nowMs,
                lastBarCloseAgeMs = staleAge
            )
            val staleIndicators = QuantitativeEngine.computeIndicators(staleCandles)
            val staleResult = SignalVetoDetector.evaluateL0Vetoes(
                symbol = "BTC",
                side = PositionSide.LONG,
                assetType = AssetType.CRYPTO,
                marketType = TradingMarketType.FUTURES,
                livePrice = staleCandles.last().close,
                candles = staleCandles,
                indicators = staleIndicators,
                timeframe = tf,
                currentTimeMillis = nowMs
            )
            assertTrue(
                "Bar older than 2x interval + 15s on ${tf.label} must veto cleanly",
                staleResult.isVetoed && staleResult.hardVetoes.any { it.contains("Stale data", ignoreCase = true) }
            )
        }
    }

    // ==================== PATCH A #5: Context-only timeframes ====================

    @Test
    fun testPatchA5_ContextOnlyTimeframesStayEntryFreeWaitAndBestNowWorks() {
        val nowMs = 1_763_130_600_000L
        for (tf in listOf(Timeframe.FIVE_DAYS, Timeframe.ONE_MONTH, Timeframe.THREE_MONTHS, Timeframe.ONE_YEAR)) {
            val candles = buildClosedCandles(startPrice = 100.0, count = 120, timeframe = Timeframe.ONE_DAY, nowMs = nowMs)
            val output = SignalEngineV2.generateSignal(
                SignalEngineV2.SignalEngineInputs(
                    symbol = "BTC",
                    currentPrice = candles.last().close,
                    timeframe = tf,
                    marketType = TradingMarketType.SPOT,
                    assetType = AssetType.CRYPTO,
                    candles = candles,
                    currentTimeMillis = nowMs
                )
            )
            assertEquals("${tf.label} must emit WAIT instead of bare NO_TRADE", SignalVerdictState.WAIT, output.verdictState)
            assertTrue("${tf.label} must mark isContextOnly = true", output.isContextOnly)
            assertEquals("Context-only TF must remain entry-free", null, output.primarySetup)
            assertTrue("${tf.label} must mention Best now", output.oneLineWhy.contains("Best now", ignoreCase = true))
        }
    }

    // ==================== PATCH A #6: Small accounts ====================

    @Test
    fun testPatchA6_SmallAccountsNeverDefaultTo25k_OverrideAndPaperFollowWork() {
        // 1. Default RiskSettings has 0.0 / isConfigured = false (never $25,000)
        val defaultSettings = RiskSettings()
        assertEquals(0.0, defaultSettings.accountBalance, 0.0001)
        assertFalse(defaultSettings.isConfigured)

        val profile = MarketProfile.defaultUsStock()
        val plan = ExitPlanner.createPlan(
            symbol = "NVDA",
            side = PositionSide.LONG,
            entryPrice = 200.0,
            stopRule = StopRuleType.MANUAL_PRICE,
            rawStopPrice = 190.0, // $10 risk/share
            profile = profile
        )

        val unconfiguredEval = RiskBudgetEngine.evaluateRiskAndSize(plan = plan, profile = profile, settings = defaultSettings)
        assertFalse(unconfiguredEval.isAllowed)
        assertTrue(unconfiguredEval.isAccountUnconfigured)
        assertEquals("account not set", unconfiguredEval.statusBanner)
        assertFalse("Unconfigured banner must contain NO dollar amounts", unconfiguredEval.statusBanner.contains("$"))
        assertTrue("Paper follow allowed when daily/cooldown limits clear", unconfiguredEval.paperFollowAllowed)

        // 2. Small configured account ($500, 1% risk = $5, 1 share risk = $10 -> min notional exceeds risk budget)
        val smallSettings = RiskSettings(
            accountBalance = 500.0,
            maxRiskPerTradeDollars = 5.0,
            riskPercent = 1.0,
            isConfigured = true,
            availableCashOrMargin = 500.0
        )
        val blockedSmallEval = RiskBudgetEngine.evaluateRiskAndSize(
            plan = plan,
            profile = profile,
            settings = smallSettings,
            lotStepSize = 1.0,
            acceptHigherRisk = false
        )
        assertFalse(blockedSmallEval.isAllowed)
        assertTrue(blockedSmallEval.isMinNotionalExceeded)
        assertTrue(blockedSmallEval.blockedReasons.any { it.contains("Minimum order exceeds your risk budget") && it.contains("% of account") })
        assertTrue(blockedSmallEval.paperFollowAllowed)

        // 3. Accepting higher risk ("this trade only") enables execution & generates ticket
        val overrideEval = RiskBudgetEngine.evaluateRiskAndSize(
            plan = plan,
            profile = profile,
            settings = smallSettings,
            lotStepSize = 1.0,
            acceptHigherRisk = true
        )
        assertTrue(overrideEval.isAllowed)
        assertNotNull(overrideEval.orderTicket)

        // 4. Daily loss limit -3R still hard blocks BOTH execution & Paper Follow
        val dailyLossEval = RiskBudgetEngine.evaluateRiskAndSize(
            plan = plan,
            profile = profile,
            settings = smallSettings,
            realizedRToday = -3.2,
            acceptHigherRisk = true
        )
        assertFalse(dailyLossEval.isAllowed)
        assertFalse(dailyLossEval.paperFollowAllowed)
    }

    // ==================== PATCH A #7: Entry windows ====================

    @Test
    fun testPatchA7_StockOutsideEntryWindowEmitsGradeCWatchNotRiskBlock_CryptoEmptyWindows() {
        assertTrue("Crypto profile must have empty entry windows", MarketProfile.defaultCrypto().entryWindows.isEmpty())

        // Wednesday 12:30 ET (between morning 09:45-11:30 and afternoon 14:00-15:30 ET windows, session is OPEN)
        val middayEt = Calendar.getInstance(TimeZone.getTimeZone("America/New_York")).apply {
            set(2026, Calendar.NOVEMBER, 11, 12, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val candles = buildClosedCandles(startPrice = 120.0, count = 130, timeframe = Timeframe.FIFTEEN_MIN, nowMs = middayEt)
        val out = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "AAPL",
                currentPrice = candles.last().close,
                timeframe = Timeframe.FIFTEEN_MIN,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.STOCK,
                candles = candles,
                currentTimeMillis = middayEt
            )
        )
        assertEquals(SignalVerdictState.WAIT, out.verdictState)
        assertEquals(SetupGrade.C_WATCH, out.setupGrade)
        assertTrue(out.softFlags.any { it.type == SoftFlagType.OUTSIDE_ENTRY_WINDOW })
        assertTrue("Next window time should appear ('opens at 14:00')", out.softFlags.any { it.label.contains("opens at 14:00") })

        // RiskBudgetEngine NEVER blocks on entry window
        val profile = MarketProfile.defaultUsStock()
        val dtPlan = ExitPlanner.createPlan("AAPL", PositionSide.LONG, 120.0, StopRuleType.MANUAL_PRICE, 118.0, profile = profile)
        val riskEval = RiskBudgetEngine.evaluateRiskAndSize(
            plan = dtPlan,
            profile = profile,
            settings = RiskSettings(accountBalance = 25_000.0, maxRiskPerTradeDollars = 250.0, isConfigured = true, availableCashOrMargin = 25_000.0),
            currentTimestamp = middayEt
        )
        assertTrue("Entry window must NOT block RiskBudgetEngine", riskEval.isAllowed)
        assertFalse(riskEval.blockedReasons.any { it.contains("window", ignoreCase = true) })
    }

    // ==================== PATCH A #8: One off-hours setting ====================

    @Test
    fun testPatchA8_UnifiedOffHoursPolicyModes() {
        // Saturday 15:00 UTC -> OFF-HOURS / WEEKEND for equity perp
        val weekendMs = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2026, Calendar.NOVEMBER, 14, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val candles = buildClosedCandles(startPrice = 130.0, count = 130, timeframe = Timeframe.ONE_HOUR, nowMs = weekendMs)
        val alignedOrderFlow = OrderFlowReport(
            symbol = "NVDA",
            biasScore = 35.0,
            confidenceScore = 70,
            stance = AgentStance.BULLISH,
            spreadTicks = 1.0,
            spreadBps = 1.0,
            microPrice = candles.last().close,
            vwapMid = candles.last().close,
            depthBands = emptyList(),
            supportWalls = emptyList(),
            resistanceWalls = emptyList(),
            cvdDelta1m = 100.0,
            cvdDelta5m = 200.0,
            cvdDelta15m = 400.0,
            aggressorBuyRatio = 0.62,
            absorptionDetected = false,
            absorptionDetails = "",
            exhaustionDetected = false,
            oiQuadrantState = "Long Buildup",
            expectedSlippageBps = 2.0,
            doNotTradeFlags = emptyList(),
            exactTriggerCondition = ""
        )

        val blockOut = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "NVDA",
                currentPrice = candles.last().close,
                timeframe = Timeframe.ONE_HOUR,
                marketType = TradingMarketType.FUTURES,
                assetType = AssetType.STOCK,
                candles = candles,
                orderFlowReport = alignedOrderFlow,
                offHoursMode = OffHoursMode.BLOCK,
                currentTimeMillis = weekendMs
            )
        )
        assertEquals(SignalVerdictState.NO_TRADE, blockOut.verdictState)

        val cautionOut = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "NVDA",
                currentPrice = candles.last().close,
                timeframe = Timeframe.ONE_HOUR,
                marketType = TradingMarketType.FUTURES,
                assetType = AssetType.STOCK,
                candles = candles,
                orderFlowReport = alignedOrderFlow,
                offHoursMode = OffHoursMode.CAUTION,
                currentTimeMillis = weekendMs
            )
        )
        assertEquals(OffHoursMode.CAUTION, cautionOut.offHoursMode)
        assertEquals(SignalVerdictState.WAIT, cautionOut.verdictState)
        assertTrue(cautionOut.softFlags.any { it.type == SoftFlagType.OFF_HOURS_CAUTION })

        val mode247Out = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "NVDA",
                currentPrice = candles.last().close,
                timeframe = Timeframe.ONE_HOUR,
                marketType = TradingMarketType.FUTURES,
                assetType = AssetType.STOCK,
                candles = candles,
                orderFlowReport = alignedOrderFlow,
                offHoursMode = OffHoursMode.TREAT_AS_24_7,
                currentTimeMillis = weekendMs
            )
        )
        assertEquals(OffHoursMode.TREAT_AS_24_7, mode247Out.offHoursMode)
        assertFalse(mode247Out.softFlags.any { it.type == SoftFlagType.OFF_HOURS_CAUTION })
    }

    // ==================== PATCH B #1, #2, #4, #5 ====================

    @Test
    fun testPatchB1_SecCompanyFactsNormalizeAndParse8Quarters() {
        assertEquals("NVDA", CompanyFactsEngine.normalizeEquityTickerForSec("NVDA-PERP"))
        assertEquals("TSLA", CompanyFactsEngine.normalizeEquityTickerForSec("TSLAUSDT"))
        assertEquals("AAPL", CompanyFactsEngine.normalizeEquityTickerForSec("aapl/usd"))

        val sampleJson = """
        {
          "facts": {
            "us-gaap": {
              "Revenues": {
                "units": {
                  "USD": [
                    {"form": "10-Q", "frame": "CY2026Q2", "val": 35000000000},
                    {"form": "10-Q", "frame": "CY2026Q1", "val": 30000000000},
                    {"form": "10-K", "frame": "CY2025Q4", "val": 28000000000},
                    {"form": "10-Q", "frame": "CY2025Q3", "val": 26000000000},
                    {"form": "10-Q", "frame": "CY2025Q2", "val": 25000000000}
                  ]
                }
              },
              "NetIncomeLoss": {
                "units": {
                  "USD": [
                    {"form": "10-Q", "frame": "CY2026Q2", "val": 18000000000},
                    {"form": "10-Q", "frame": "CY2026Q1", "val": 15000000000}
                  ]
                }
              },
              "EarningsPerShareDiluted": {
                "units": {
                  "USD/shares": [
                    {"form": "10-Q", "frame": "CY2026Q2", "val": 0.85},
                    {"form": "10-Q", "frame": "CY2026Q1", "val": 0.72}
                  ]
                }
              },
              "GrossProfit": {
                "units": {
                  "USD": [
                    {"form": "10-Q", "frame": "CY2026Q2", "val": 26250000000},
                    {"form": "10-Q", "frame": "CY2026Q1", "val": 22200000000}
                  ]
                }
              }
            }
          }
        }
        """.trimIndent()

        val parsed = CompanyFactsEngine.parseCompanyFactsJson("NVDA-PERP", "0001045810", sampleJson)
        assertEquals("NVDA", parsed.symbol)
        assertEquals("0001045810", parsed.cik)
        assertEquals(5, parsed.quarters.size)
        assertEquals("CY2026Q2", parsed.quarters.first().quarterLabel)
        assertEquals(75.0, parsed.latestGrossMarginPercent ?: 0.0, 0.1)
        assertEquals(40.0, parsed.latestYoYGrowthPercent ?: 0.0, 0.1) // 35B vs 25B = +40%
        assertTrue(parsed.whatChangedNumbersOnly.any { it.contains("Revenue:") && it.contains("35.00B") })
    }

    @Test
    fun testPatchB2_AiShiftClampsPlusMinus15AndWilsonIntervalWorks() {
        val nowMs = 1_763_130_600_000L
        val candles = buildClosedCandles(startPrice = 100.0, count = 130, timeframe = Timeframe.ONE_HOUR, nowMs = nowMs)

        val neutralOut = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "SOL",
                currentPrice = candles.last().close,
                timeframe = Timeframe.ONE_HOUR,
                candles = candles,
                aiShiftPoints = 0,
                currentTimeMillis = nowMs
            )
        )
        val plusClampedOut = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "SOL",
                currentPrice = candles.last().close,
                timeframe = Timeframe.ONE_HOUR,
                candles = candles,
                aiShiftPoints = 40, // clamps to +15
                aiShiftModelName = "gemini-2.5-flash",
                currentTimeMillis = nowMs
            )
        )
        val minusClampedOut = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "SOL",
                currentPrice = candles.last().close,
                timeframe = Timeframe.ONE_HOUR,
                candles = candles,
                aiShiftPoints = -30, // clamps to -15
                aiShiftModelName = "gemini-2.5-flash",
                currentTimeMillis = nowMs
            )
        )

        assertEquals(15, plusClampedOut.aiShiftPoints)
        assertEquals(-15, minusClampedOut.aiShiftPoints)
        assertTrue(plusClampedOut.calibratedConfidence >= neutralOut.calibratedConfidence)
        assertTrue(minusClampedOut.calibratedConfidence <= neutralOut.calibratedConfidence)

        val (lo, hi) = SignalCalibrationEngine.wilsonConfidenceInterval95(wins = 24, total = 40)
        assertTrue("Wilson 95% CI must bracket 60%: [$lo, $hi]", lo in 43.0..50.0 && hi in 70.0..77.0)
        assertEquals(Pair(0.0, 0.0), SignalCalibrationEngine.wilsonConfidenceInterval95(0, 0))
    }

    @Test
    fun testPatchB4_WeekendReplayPoolCombinesWithWeekdayWhenBelow20AndEmitsNotice() {
        val candles = mutableListOf<CandlePoint>()
        val utc = TimeZone.getTimeZone("UTC")
        // Build 25 full UTC days (288 bars/day) ending before Saturday Nov 14, 2026:
        // that includes ~7 weekend days (< 20) and ~18 weekday days (25 total >= 20)
        val satTodayMs = Calendar.getInstance(utc).apply {
            set(2026, Calendar.NOVEMBER, 14, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        for (dayOffset in 1..25) {
            val dayStartCal = Calendar.getInstance(utc).apply {
                timeInMillis = satTodayMs
                add(Calendar.DAY_OF_YEAR, -dayOffset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMs = dayStartCal.timeInMillis
            var price = 100.0
            for (barIdx in 0 until 288) {
                val close = price * 1.0001
                candles.add(
                    CandlePoint(
                        timestamp = startMs + barIdx * 300_000L,
                        open = price,
                        high = close * 1.001,
                        low = price * 0.999,
                        close = close,
                        volume = 1000.0,
                        isClosed = true
                    )
                )
                price = close
            }
        }

        val pool = SessionPool.buildFromCandlesWithWeekendFallback(
            symbol = "BTC",
            profileType = MarketProfileType.CRYPTO,
            candles = candles,
            todayTimestamp = satTodayMs,
            preferWeekendOnly = true
        )
        assertTrue("Must combine weekend with weekday pool when weekend < 20 sessions", pool.isWeekendFallbackCombined)
        assertTrue("Combined pool must have >= 20 sessions", pool.totalSessions >= 20)
        assertNotNull(pool.poolNotice)
        assertTrue(pool.poolNotice!!.contains("combined with weekday pool", ignoreCase = true))
    }

    @Test
    fun testPatchB5_DiagnosticsApiErrorRedactorStripsKeysAndTokens() {
        val raw = "Failed calling https://example.com?apiKey=mySecret999 Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9 key=AIzaSyA1234567890abcdefghij and sk-proj-abcdef1234567890"
        val redacted = MarketDataService.redactSensitiveTokens(raw)
        assertFalse(redacted.contains("AIzaSyA1234567890"))
        assertFalse(redacted.contains("sk-proj-abcdef1234567890"))
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
        assertFalse(redacted.contains("mySecret999"))
        assertTrue(redacted.contains("[REDACTED"))
    }

    private fun assertNotEquals(message: String, unexpected: Any?, actual: Any?) {
        org.junit.Assert.assertNotEquals(message, unexpected, actual)
    }
}
