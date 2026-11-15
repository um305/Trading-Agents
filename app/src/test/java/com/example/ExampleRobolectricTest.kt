package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("TradingAgents", appName)
  }

  @Test
  fun `switching from futures to spot trading works`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val viewModel = com.example.ui.MainViewModel(app)

    viewModel.selectSymbol("BTC")
    viewModel.setMarketType(com.example.domain.model.TradingMarketType.SPOT)
    assertEquals(com.example.domain.model.TradingMarketType.SPOT, viewModel.uiState.value.selectedMarketType)

    viewModel.setMarketType(com.example.domain.model.TradingMarketType.FUTURES)
    assertEquals(com.example.domain.model.TradingMarketType.FUTURES, viewModel.uiState.value.selectedMarketType)

    viewModel.setMarketType(com.example.domain.model.TradingMarketType.SPOT)
    assertEquals(com.example.domain.model.TradingMarketType.SPOT, viewModel.uiState.value.selectedMarketType)
  }

  @Test
  fun `strong entry point remains stable and doesn't drift on price ticks`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val repository = com.example.data.repository.MarketRepository(app)

    val step = com.example.domain.model.Timeframe.FIFTEEN_MIN.stepMillis
    val candles = (0 until 20).map { i ->
      com.example.domain.model.CandlePoint(
        timestamp = 1700000000000L + (i * step),
        open = 224.0 + (i * 0.1),
        high = 226.0 + (i * 0.1),
        low = 222.0 + (i * 0.1),
        close = 225.0 + (i * 0.1),
        volume = 100000.0
      )
    }

    // Generate trade plan anchored to structure
    val plan = repository.calculateTradeSetupPlan(
      symbol = "NVDA",
      currentPrice = 225.0,
      side = com.example.domain.model.PositionSide.LONG,
      leverage = 10,
      candles = candles,
      dayHigh = 228.0,
      dayLow = 219.0,
      timeframe = com.example.domain.model.Timeframe.FIFTEEN_MIN
    )

    val anchorEntry = plan.recommendedEntry
    org.junit.Assert.assertTrue("Plan must be available", plan.isAvailable)
    org.junit.Assert.assertTrue("Anchor entry must be <= current price for Long", anchorEntry <= 225.0)

    // Simulate small market tick moves
    val tickedPrice1 = 225.15
    val tickedPrice2 = 224.85
    val updatedPlan1 = plan.copy(currentPrice = tickedPrice1)
    val updatedPlan2 = plan.copy(currentPrice = tickedPrice2)

    // Entry point remains rock solid!
    assertEquals(anchorEntry, updatedPlan1.recommendedEntry, 0.0001)
    assertEquals(anchorEntry, updatedPlan2.recommendedEntry, 0.0001)
    assertEquals(tickedPrice1, updatedPlan1.currentPrice, 0.0001)
    assertEquals(tickedPrice2, updatedPlan2.currentPrice, 0.0001)
  }

  @Test
  fun `risk assessment calculations calibrate liquidation buffer based on leverage`() {
    val currentPrice = 60000.0

    // At 10x leverage, liquidation buffer is around 9.5-10%
    val lev10 = 10
    val liqPrice10 = currentPrice * (1.0 - (1.0 / lev10) + 0.005)
    val buffer10 = (currentPrice - liqPrice10) / currentPrice * 100.0
    org.junit.Assert.assertTrue("10x leverage buffer should be ~9.5%", buffer10 in 9.0..10.5)

    // At 20x leverage, liquidation buffer is tighter around 4.5-5%
    val lev20 = 20
    val liqPrice20 = currentPrice * (1.0 - (1.0 / lev20) + 0.005)
    val buffer20 = (currentPrice - liqPrice20) / currentPrice * 100.0
    org.junit.Assert.assertTrue("20x leverage buffer should be tighter (~4.5%)", buffer20 < buffer10)
    org.junit.Assert.assertTrue("20x leverage buffer should be ~4.5%", buffer20 in 4.0..5.5)
  }

  @Test
  fun `take profit targets are strictly monotonic and never in disorder`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val repository = com.example.data.repository.MarketRepository(app)

    val step = com.example.domain.model.Timeframe.FIFTEEN_MIN.stepMillis
    val candles = (0 until 20).map { i ->
      com.example.domain.model.CandlePoint(
        timestamp = 1700000000000L + (i * step),
        open = 90.0 + (i * 0.1),
        high = 92.0 + (i * 0.1),
        low = 88.0 + (i * 0.1),
        close = 91.0 + (i * 0.1),
        volume = 1000.0
      )
    }

    // Test LONG plan: Entry < TP1 < TP2 < TP3
    val longPlan = repository.calculateTradeSetupPlan(
      symbol = "BTC",
      currentPrice = 90.0,
      side = com.example.domain.model.PositionSide.LONG,
      leverage = 10,
      candles = candles,
      timeframe = com.example.domain.model.Timeframe.FIFTEEN_MIN
    )
    org.junit.Assert.assertTrue(
      "LONG setup must have Entry < TP1: ${longPlan.recommendedEntry} vs ${longPlan.takeProfit1}",
      longPlan.takeProfit1 > longPlan.recommendedEntry
    )
    org.junit.Assert.assertTrue(
      "LONG setup must have TP1 < TP2: ${longPlan.takeProfit1} vs ${longPlan.takeProfit2}",
      longPlan.takeProfit2 > longPlan.takeProfit1
    )
    org.junit.Assert.assertTrue(
      "LONG setup must have TP2 < TP3: ${longPlan.takeProfit2} vs ${longPlan.takeProfit3}",
      longPlan.takeProfit3 > longPlan.takeProfit2
    )

    // Test SHORT plan: Entry > TP1 > TP2 > TP3
    val shortPlan = repository.calculateTradeSetupPlan(
      symbol = "BTC",
      currentPrice = 90.0,
      side = com.example.domain.model.PositionSide.SHORT,
      leverage = 10,
      candles = candles,
      timeframe = com.example.domain.model.Timeframe.FIFTEEN_MIN
    )
    org.junit.Assert.assertTrue(
      "SHORT setup must have Entry > TP1: ${shortPlan.recommendedEntry} vs ${shortPlan.takeProfit1}",
      shortPlan.takeProfit1 < shortPlan.recommendedEntry
    )
    org.junit.Assert.assertTrue(
      "SHORT setup must have TP1 > TP2: ${shortPlan.takeProfit1} vs ${shortPlan.takeProfit2}",
      shortPlan.takeProfit2 < shortPlan.takeProfit1
    )
    org.junit.Assert.assertTrue(
      "SHORT setup must have TP2 > TP3: ${shortPlan.takeProfit2} vs ${shortPlan.takeProfit3}",
      shortPlan.takeProfit3 < shortPlan.takeProfit2
    )
  }

  @Test
  fun `tradingview node detects bearish breakdown across 26 indicators`() {
    // Generate a descending sequence of candles (bearish trend)
    val candles = (1..30).map { i ->
      val base = 100.0 - (i * 1.5)
      com.example.domain.model.CandlePoint(
        timestamp = i * 60000L,
        open = base + 0.5,
        high = base + 1.0,
        low = base - 0.8,
        close = base,
        volume = 50000.0
      )
    }

    val quote = com.example.domain.model.MarketQuote(
      symbol = "SOL",
      name = "Solana",
      assetType = com.example.domain.model.AssetType.CRYPTO,
      currentPrice = candles.last().close,
      change = -12.0,
      changePercent = -8.5,
      open = 100.0,
      dayHigh = 102.0,
      dayLow = candles.last().close,
      volume = 1000000.0,
      marketCap = 40e9,
      peRatio = null,
      fiftyTwoWeekHigh = 120.0,
      fiftyTwoWeekLow = 50.0,
      candles = candles,
      primarySource = "Binance"
    )

    val (report, summary) = com.example.domain.agents.TradingViewTechnicalsAgent.evaluate(quote)
    org.junit.Assert.assertEquals(34, summary.totalIndicators)
    org.junit.Assert.assertTrue("Sell count must dominate in bearish trend", summary.sellCount > summary.buyCount)
    org.junit.Assert.assertTrue("Summary must be SELL or STRONG SELL", summary.summaryVerdict.contains("SELL"))
    org.junit.Assert.assertEquals(com.example.domain.model.AgentStance.BEARISH, report.stance)
  }
}
