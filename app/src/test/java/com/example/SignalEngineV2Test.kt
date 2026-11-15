package com.example

import com.example.domain.analytics.QuantitativeEngine
import com.example.domain.engine.SignalCalibrationEngine
import com.example.domain.engine.SignalEngineV2
import com.example.domain.engine.SignalFamilyScorer
import com.example.domain.engine.SignalVetoDetector
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.MarketQuote
import com.example.domain.model.OffHoursMode
import com.example.domain.model.PositionSide
import com.example.domain.model.SignalFamily
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SignalEngineV2Test {

    private fun generateCandles(
        startPrice: Double,
        count: Int = 110,
        trendStepPercent: Double = 0.002,
        volatilityPercent: Double = 0.005
    ): List<CandlePoint> {
        val list = mutableListOf<CandlePoint>()
        var price = startPrice
        val startTs = 1700000000000L
        val interval = 5 * 60 * 1000L
        for (i in 0 until count) {
            val open = price
            price = open * (1.0 + trendStepPercent)
            val high = maxOf(open, price) * (1.0 + volatilityPercent)
            val low = minOf(open, price) * (1.0 - volatilityPercent)
            val close = price
            list.add(
                CandlePoint(
                    timestamp = startTs + (i * interval),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = 20000.0 * (1.0 + (i % 3) * 0.1)
                )
            )
        }
        return list
    }

    @Test
    fun testDefect1_SNDK_StructureBreakVsMovingAveragesConflict_OutputsWait() {
        val candles = generateCandles(startPrice = 100.0, count = 110, trendStepPercent = -0.001)

        val output = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "SNDK",
                currentPrice = 96.0,
                candles = candles,
                timeframe = Timeframe.FIVE_MIN,
                marketType = TradingMarketType.FUTURES,
                assetType = AssetType.STOCK,
                offHoursMode = OffHoursMode.BLOCK
            )
        )

        assertTrue(
            "SNDK off-hours stock perp or conflicting structure must output WAIT or NO_TRADE",
            output.verdictState == SignalVerdictState.NO_TRADE || output.verdictState == SignalVerdictState.WAIT
        )
    }

    @Test
    fun testDefect2_MaxOneVotePerFamily() {
        val candles = generateCandles(startPrice = 50000.0, count = 110, trendStepPercent = 0.003)
        val indicators = QuantitativeEngine.computeIndicators(candles)
        val quote = MarketQuote(
            symbol = "BTCUSDT",
            name = "Bitcoin",
            assetType = AssetType.CRYPTO,
            marketType = TradingMarketType.FUTURES,
            currentPrice = 55000.0,
            change = 500.0,
            changePercent = 1.0,
            open = 54500.0,
            dayHigh = 55200.0,
            dayLow = 54000.0,
            volume = 1000000.0,
            candles = candles
        )

        val familyResult = SignalFamilyScorer.evaluateFamilies(
            candidateSide = PositionSide.LONG,
            quote = quote,
            candles = candles,
            indicators = indicators,
            htfIndicators = indicators,
            orderFlow = null,
            futuresData = null,
            news = emptyList(),
            scannerResult = null,
            regime = com.example.domain.model.MarketRegime.TREND
        )

        assertEquals("Exactly 7 independent families must be evaluated", 7, familyResult.votes.size)
        val distinctFamilies = familyResult.votes.map { it.family }.distinct()
        assertEquals("Each family must have at most 1 vote", 7, distinctFamilies.size)
    }

    @Test
    fun testDefect3_StopLossBeyondLiquidityAndMin1Point5Atr() {
        val candles = generateCandles(startPrice = 200.0, count = 110, trendStepPercent = 0.002, volatilityPercent = 0.01)
        val currentPrice = candles.last().close
        val atr = QuantitativeEngine.calculateATR(candles)

        val output = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "SOLUSDT",
                currentPrice = currentPrice,
                candles = candles,
                timeframe = Timeframe.FIFTEEN_MIN,
                marketType = TradingMarketType.FUTURES,
                assetType = AssetType.CRYPTO
            )
        )

        val setup = output.primarySetup
        if (setup != null) {
            val stopDist = abs(setup.entryPrice - setup.stopLossPrice)
            assertTrue("Stop distance must be >= 1.45 * ATR to avoid stop-hunting", stopDist >= 1.45 * atr)
            assertTrue("Net R:R to TP1 must be >= 1.4", setup.netRiskRewardTp1 >= 1.4)
        }
    }

    @Test
    fun testDefect4_OffHoursStockPerpsVetoedByDefault() {
        val candles = generateCandles(startPrice = 150.0, count = 110)
        val output = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "AAPL",
                currentPrice = 150.0,
                candles = candles,
                timeframe = Timeframe.FIFTEEN_MIN,
                marketType = TradingMarketType.FUTURES,
                assetType = AssetType.STOCK,
                offHoursMode = OffHoursMode.BLOCK
            )
        )

        if (!output.sessionStatus.isOpen) {
            assertEquals("Off-hours stock perp must veto to NO_TRADE when mode is BLOCK", SignalVerdictState.NO_TRADE, output.verdictState)
            assertTrue("Veto reasons must mention off-hours", output.vetoReasons.any { it.contains("Off-hours", ignoreCase = true) || it.contains("closed", ignoreCase = true) })
        }
    }

    @Test
    fun testDefect5_SpotShortRoutesToFuturesWhenPerpAvailable_VetoesOnlyWhenNoPerp() {
        val candles = generateCandles(startPrice = 3000.0, count = 110, trendStepPercent = -0.005)
        val currentPrice = candles.last().close

        // With futuresPerpAvailable = true, Spot + Short evaluates short candidates via Futures perp
        val routedOutput = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "ETHUSDT",
                currentPrice = currentPrice,
                candles = candles,
                timeframe = Timeframe.FIFTEEN_MIN,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.CRYPTO,
                preferredSide = PositionSide.SHORT,
                futuresPerpAvailable = true
            )
        )
        assertFalse(
            "Spot + Short with perp available must NOT emit Spot Short veto",
            routedOutput.vetoReasons.any { it.contains("Spot Short", ignoreCase = true) }
        )

        // Without perp available, Spot + Short hard-vetoes cleanly
        val noPerpOutput = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "RARECOIN",
                currentPrice = currentPrice,
                candles = candles,
                timeframe = Timeframe.FIFTEEN_MIN,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.CRYPTO,
                preferredSide = PositionSide.SHORT,
                futuresPerpAvailable = false
            )
        )
        assertEquals(SignalVerdictState.NO_TRADE, noPerpOutput.verdictState)
        assertTrue(
            "Missing perp must state 'No futures market for <symbol>'",
            noPerpOutput.vetoReasons.any { it.contains("No futures market for RARECOIN", ignoreCase = true) }
        )
    }

    @Test
    fun testDefect6_MinimumFvgSizeRequirement() {
        val candles = generateCandles(startPrice = 100.0, count = 110, volatilityPercent = 0.02)
        val atr = QuantitativeEngine.calculateATR(candles)
        val smc = QuantitativeEngine.computeSmartMoneyStructure(candles, atr)

        smc.activeFVGs.forEach { fvg ->
            val fvgHeight = abs(fvg.top - fvg.bottom)
            assertTrue("Every detected FVG must be >= 0.28 * ATR", fvgHeight >= 0.28 * atr)
        }
    }

    @Test
    fun testHistoryGuard_LessThan100Bars_OutputsNoTrade() {
        val candles = generateCandles(startPrice = 100.0, count = 60)
        val output = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "SOL",
                currentPrice = 100.0,
                candles = candles,
                timeframe = Timeframe.ONE_HOUR,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.CRYPTO
            )
        )

        assertEquals("Less than 100 bars must strictly output NO_TRADE", SignalVerdictState.NO_TRADE, output.verdictState)
        assertTrue(
            "Veto reason must state insufficient history",
            output.vetoReasons.any { it.contains("Insufficient history", ignoreCase = true) }
        )
    }

    @Test
    fun testTargetDistanceOrdering_Tp1Nearest() {
        val candles = generateCandles(startPrice = 85.0, count = 110, trendStepPercent = 0.001)
        val currentPrice = candles.last().close

        val output = SignalEngineV2.generateSignal(
            SignalEngineV2.SignalEngineInputs(
                symbol = "SOL",
                currentPrice = currentPrice,
                candles = candles,
                timeframe = Timeframe.ONE_HOUR,
                marketType = TradingMarketType.SPOT,
                assetType = AssetType.CRYPTO
            )
        )

        val setup = output.primarySetup
        if (setup != null) {
            val dist1 = abs(setup.takeProfit1 - setup.entryPrice)
            val dist2 = abs(setup.takeProfit2 - setup.entryPrice)
            val dist3 = abs(setup.takeProfit3 - setup.entryPrice)

            assertTrue("TP1 must be nearest in distance: dist1 ($dist1) < dist2 ($dist2)", dist1 < dist2)
            assertTrue("TP2 must be nearer than TP3: dist2 ($dist2) < dist3 ($dist3)", dist2 < dist3)
        }
    }

    @Test
    fun testSignalCalibrationEngine_BaselineAndUncalibrated() {
        val (pWin, _) = SignalCalibrationEngine.calculateCalibratedPWin(
            symbol = "TEST",
            model = com.example.domain.model.EntryModel.D_RANGE_FADE,
            regime = com.example.domain.model.MarketRegime.RANGE,
            isWithTrend = false,
            rMultiple = 2.5,
            sessionStatus = com.example.domain.model.MarketSessionStatus.WEEKEND
        )

        val expectedBaseline = 1.0 / (1.0 + 2.5) // ~0.2857
        assertEquals(expectedBaseline, pWin, 0.001)

        val evidence = SignalCalibrationEngine.getEvidenceForModel(
            symbol = "TEST_UNPROVEN",
            model = com.example.domain.model.EntryModel.D_RANGE_FADE,
            regime = com.example.domain.model.MarketRegime.RANGE,
            isWithTrend = false,
            rMultiple = 2.5,
            sessionStatus = com.example.domain.model.MarketSessionStatus.WEEKEND
        )
        assertTrue("Sample size < 30 must be marked unproven", evidence.isUnproven)
        assertEquals(expectedBaseline * 100.0, evidence.baselinePercent, 0.1)
    }
}
