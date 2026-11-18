package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.example.data.api.MarketDataService
import com.example.domain.master.MasterVerdictEngine
import com.example.domain.master.MasterVerdictInput
import com.example.domain.master.TradingHorizon
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.CryptoExchange
import com.example.domain.model.EntryOrderType
import com.example.domain.model.ExchangePriceQuote
import com.example.domain.model.PositionSide
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.Timeframe
import com.example.domain.model.TradeSetupPlan
import com.example.domain.model.TradingMarketType
import com.example.ui.components.CandlestickChart
import com.example.ui.components.computeChartMarginsDp
import com.example.ui.components.computeUpdatedPanOffset
import com.example.ui.components.computeUpdatedVerticalPanPercent
import kotlinx.coroutines.test.runTest
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
class UiAndFallbackFixesTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `cleanUnavailableReason strips duplicated prefixes and normalizes HTTP codes`() {
        assertEquals("Geo-blocked (HTTP 451)", MarketDataService.cleanUnavailableReason("HTTP 451"))
        assertEquals("Geo-blocked (HTTP 451)", MarketDataService.cleanUnavailableReason("Unavailable - HTTP 451"))
        assertEquals(
            "Geo-blocked (HTTP 451)",
            MarketDataService.cleanUnavailableReason("Unavailable - Unavailable - HTTP 451")
        )
        assertEquals("Restricted (HTTP 403)", MarketDataService.cleanUnavailableReason("Unavailable - HTTP 403"))
        assertEquals("No quote", MarketDataService.cleanUnavailableReason(null))
        assertEquals("No quote", MarketDataService.cleanUnavailableReason("  Unavailable -   "))
        assertEquals("Symbol not listed", MarketDataService.cleanUnavailableReason("Unavailable • Symbol not listed"))
    }

    @Test
    fun `sortExchangeQuotesLiveFirst moves OKX and Coinbase ahead of unavailable feeds`() {
        val unavailableBinance = ExchangePriceQuote(
            exchange = CryptoExchange.BINANCE,
            price = 0.0,
            change24h = 0.0,
            volume24hUsd = 0.0,
            spreadPercent = 0.0,
            unavailableReason = "Geo-blocked (HTTP 451)"
        )
        val unavailableBybit = ExchangePriceQuote(
            exchange = CryptoExchange.BYBIT,
            price = 0.0,
            change24h = 0.0,
            volume24hUsd = 0.0,
            spreadPercent = 0.0,
            unavailableReason = "Restricted (HTTP 403)"
        )
        val liveOkx = ExchangePriceQuote(
            exchange = CryptoExchange.OKX,
            price = 64212.5,
            change24h = -0.45,
            volume24hUsd = 980_000_000.0,
            spreadPercent = 0.002,
            unavailableReason = null
        )
        val liveCoinbase = ExchangePriceQuote(
            exchange = CryptoExchange.COINBASE,
            price = 64215.0,
            change24h = -0.42,
            volume24hUsd = 510_000_000.0,
            spreadPercent = 0.005,
            unavailableReason = null
        )

        val sorted = MarketDataService.sortExchangeQuotesLiveFirst(
            listOf(unavailableBinance, unavailableBybit, liveOkx, liveCoinbase)
        )

        assertEquals(
            listOf(CryptoExchange.OKX, CryptoExchange.COINBASE, CryptoExchange.BINANCE, CryptoExchange.BYBIT),
            sorted.map { it.exchange }
        )
    }

    @Test
    fun `parseOkxCandlesJson filters open bar and sorts descending OKX payload ascending`() {
        assertEquals("1H", MarketDataService.toOkxBarInterval(Timeframe.ONE_HOUR))
        assertEquals("4H", MarketDataService.toOkxBarInterval(Timeframe.FOUR_HOURS))
        assertEquals("1W", MarketDataService.toOkxBarInterval(Timeframe.FIVE_DAYS))

        val okxJson = """
            {
              "code": "0",
              "data": [
                ["1730007200000", "64200", "64300", "64100", "64250", "12.5", "803125", "803125", "0"],
                ["1730003600000", "64100", "64250", "64050", "64200", "22.0", "1412400", "1412400", "1"],
                ["1730000000000", "63950", "64150", "63900", "64100", "18.5", "1185850", "1185850", "1"]
              ]
            }
        """.trimIndent()

        val parsed = MarketDataService.parseOkxCandlesJson(
            jsonString = okxJson,
            timeframe = Timeframe.ONE_HOUR,
            nowMs = 1730007300000L
        )

        assertEquals(2, parsed.size)
        assertEquals(1730000000000L, parsed[0].timestamp)
        assertEquals(64100.0, parsed[0].close, 0.001)
        assertEquals(1730003600000L, parsed[1].timestamp)
        assertEquals(64200.0, parsed[1].close, 0.001)
        assertTrue(parsed.all { it.isClosed })
    }

    @Test
    fun `computeChartMarginsDp maximizes canvas width when trade badges are hidden`() {
        assertEquals(54f to 0f, computeChartMarginsDp(hasLevelBadges = false, hasCone = false))
        assertEquals(74f to 0f, computeChartMarginsDp(hasLevelBadges = true, hasCone = false))
        assertEquals(74f to 28f, computeChartMarginsDp(hasLevelBadges = true, hasCone = true))
    }

    @Test
    fun `computeUpdatedPanOffset pans horizontally from right edge into older bars`() {
        // 100 candles total, 20 visible => maxPan = 80f; plotWidth = 200f => 10px per candle
        val afterRightDrag = computeUpdatedPanOffset(
            currentPanOffset = -1f,
            dragDeltaX = 50f, // drag finger right by 5 candles -> pan backwards by 5 bars
            plotWidthPx = 200f,
            visibleCandleCount = 20,
            totalCandles = 100
        )
        assertEquals(75f, afterRightDrag, 0.01f)

        val clampedStart = computeUpdatedPanOffset(
            currentPanOffset = 3f,
            dragDeltaX = 100f,
            plotWidthPx = 200f,
            visibleCandleCount = 20,
            totalCandles = 100
        )
        assertEquals(0f, clampedStart, 0.01f)

        val clampedEnd = computeUpdatedPanOffset(
            currentPanOffset = 78f,
            dragDeltaX = -100f,
            plotWidthPx = 200f,
            visibleCandleCount = 20,
            totalCandles = 100
        )
        assertEquals(80f, clampedEnd, 0.01f)
    }

    @Test
    fun `computeUpdatedVerticalPanPercent shifts price window up on downward drag and clamps cleanly`() {
        val panUpToSeeTp = computeUpdatedVerticalPanPercent(
            currentVerticalPanPercent = 0f,
            dragDeltaY = 100f, // drag finger down on chart -> reveal higher prices (TP targets)
            chartHeightPx = 200f
        )
        assertEquals(0.5f, panUpToSeeTp, 0.01f)

        val panDownToSeeSl = computeUpdatedVerticalPanPercent(
            currentVerticalPanPercent = 0f,
            dragDeltaY = -100f, // drag finger up on chart -> reveal lower prices (Entry / SL)
            chartHeightPx = 200f
        )
        assertEquals(-0.5f, panDownToSeeSl, 0.01f)

        val clampedHigh = computeUpdatedVerticalPanPercent(3.8f, 200f, 200f)
        assertEquals(4.0f, clampedHigh, 0.01f)

        val clampedLow = computeUpdatedVerticalPanPercent(-3.8f, -200f, 200f)
        assertEquals(-4.0f, clampedLow, 0.01f)
    }

    @Test
    fun `candlestickChart captures vertical touch swipe without triggering parent verticalScroll`() {
        val baseTs = 1_730_000_000_000L
        val step = Timeframe.ONE_HOUR.stepMillis
        val candles = (0 until 60).map { i ->
            val p = 60_000.0 + i * 50.0
            CandlePoint(baseTs + i * step, p - 10.0, p + 25.0, p - 25.0, p, 300.0, isClosed = true, closeTimestamp = baseTs + (i + 1) * step)
        }
        lateinit var scrollState: androidx.compose.foundation.ScrollState

        composeRule.setContent {
            scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                Box(modifier = Modifier.fillMaxWidth().height(500.dp))
                CandlestickChart(
                    candles = candles,
                    modifier = Modifier.fillMaxWidth(),
                    currentPrice = candles.last().close
                )
                Box(modifier = Modifier.fillMaxWidth().height(1200.dp))
            }
        }
        composeRule.waitForIdle()
        val initialScroll = scrollState.value

        composeRule.onNodeWithTag("candlestick_canvas").performTouchInput {
            swipeUp()
        }
        composeRule.waitForIdle()

        assertEquals("Vertical swipe inside chart canvas must pan chart, not scroll outer page", initialScroll, scrollState.value)
    }

    @Test
    fun `candlestickChart scrolls horizontally via touch inside verticalScroll even after tradeSetupPlan arrives`() {
        val baseTs = 1_730_000_000_000L
        val step = Timeframe.ONE_HOUR.stepMillis
        val candles = (0 until 100).map { i ->
            val closePrice = 1_000.0 + i * 100.0
            CandlePoint(
                timestamp = baseTs + i * step,
                open = closePrice - 10.0,
                high = closePrice + 20.0,
                low = closePrice - 20.0,
                close = closePrice,
                volume = 500.0 + i,
                isClosed = true,
                closeTimestamp = baseTs + (i + 1) * step
            )
        }
        var planState by mutableStateOf<TradeSetupPlan?>(null)

        composeRule.setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CandlestickChart(
                    candles = candles,
                    modifier = Modifier.fillMaxWidth(),
                    tradeSetupPlan = planState,
                    currentPrice = candles.last().close
                )
            }
        }
        composeRule.waitForIdle()

        // Latest candle close is $10,900.00 before async TradeSetupPlan loads
        composeRule.onNodeWithText("C: $10,900.00", substring = true).assertExists()

        // Async TradeSetupPlan arrives (previously caused panOffset state re-creation while pointerInput kept old state!)
        planState = TradeSetupPlan(
            side = PositionSide.LONG,
            pairSymbol = "BTCUSDT",
            currentPrice = 10_900.0,
            entryZoneStart = 10_800.0,
            entryZoneEnd = 10_850.0,
            recommendedEntry = 10_825.0,
            recommendedLeverage = 5,
            takeProfit1 = 11_100.0,
            takeProfit2 = 11_300.0,
            stopLoss = 10_650.0,
            verdictState = SignalVerdictState.TRADE,
            entryType = EntryOrderType.LIMIT,
            timeframe = Timeframe.ONE_HOUR,
            isAvailable = true
        )
        composeRule.waitForIdle()

        // Swipe right horizontally across the chart to scroll into older candles
        composeRule.onNodeWithTag("candlestick_canvas").performTouchInput {
            swipeRight()
        }
        composeRule.waitForIdle()

        // Header close readout updates to an older candle rather than remaining frozen on C: $10,900.00
        composeRule.onNodeWithText("C: $10,900.00", substring = true).assertDoesNotExist()
    }

    @Test
    fun `seeded 1h chart candles prevent 0 of 100 History Guard veto on Intraday horizon`() = runTest {
        val now = 1_730_000_000_000L
        val step = Timeframe.ONE_HOUR.stepMillis
        val firstOpen = now - 130L * step - 10_000L
        val seeded1h = (0 until 125).map { i ->
            val openTs = firstOpen + i * step
            val o = 63_000.0 + i * 10.0
            val c = o + 8.0
            CandlePoint(
                timestamp = openTs,
                open = o,
                high = c + 40.0,
                low = o - 40.0,
                close = c,
                volume = 450.0,
                isClosed = true,
                closeTimestamp = openTs + step
            )
        }

        val verdict = MasterVerdictEngine.evaluate(
            MasterVerdictInput(
                symbol = "BTC",
                assetType = AssetType.CRYPTO,
                marketType = TradingMarketType.FUTURES,
                livePrice = seeded1h.last().close,
                candlesByTf = mapOf(Timeframe.ONE_HOUR to seeded1h),
                dataAgeByTf = mapOf(Timeframe.ONE_HOUR to 60_000L),
                futuresPerpAvailable = true,
                nowMs = now
            )
        )

        val intraday = verdict.horizonPlans[TradingHorizon.INTRADAY]!!
        assertFalse(
            "Intraday must not report 0/100 bars when 125 closed 1h chart bars are seeded",
            intraday.vetoes.any { it.contains("0/100") } ||
                intraday.needs.contains("0/100") ||
                intraday.softFlags.any { it.label.contains("0/100") }
        )
        assertTrue(
            "Intraday state should be TRADE or WAIT rather than unseeded NO_TRADE",
            intraday.state != SignalVerdictState.NO_TRADE || intraday.vetoes.none { it.contains("0/100") }
        )
    }
}
