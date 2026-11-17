package com.example

import com.example.data.api.MarketDataService
import com.example.domain.master.MasterVerdictEngine
import com.example.domain.master.MasterVerdictInput
import com.example.domain.master.TradingHorizon
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.CryptoExchange
import com.example.domain.model.ExchangePriceQuote
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import com.example.ui.components.computeChartMarginsDp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiAndFallbackFixesTest {

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
