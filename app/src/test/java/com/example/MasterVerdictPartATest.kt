package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.MarketDataService
import com.example.data.cache.MarketCacheRepository
import com.example.data.db.AppDatabase
import com.example.domain.analytics.QuantitativeEngine
import com.example.domain.daytrader.risk.RiskSettings
import com.example.domain.engine.SignalCalibrationEngine
import com.example.domain.master.AgentId
import com.example.domain.master.AgentStatus
import com.example.domain.master.MasterVerdictEngine
import com.example.domain.master.MasterVerdictInput
import com.example.domain.master.SpotActionMode
import com.example.domain.master.TradingHorizon
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.PositionSide
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MasterVerdictPartATest {

    private fun buildTrendBars(
        nowMs: Long,
        tf: Timeframe,
        count: Int,
        startPrice: Double,
        slopePerBar: Double,
        amplitudePct: Double = 0.004
    ): List<CandlePoint> {
        val step = if (tf == Timeframe.FIVE_DAYS) 7L * 86_400_000L else tf.stepMillis
        val firstOpen = nowMs - (count.toLong() * step) - 10_000L
        val out = mutableListOf<CandlePoint>()
        var price = startPrice
        for (i in 0 until count) {
            val openTs = firstOpen + i * step
            val wave = if (i % 4 == 0) -price * amplitudePct * 0.4 else price * amplitudePct * 0.4
            val close = (price + slopePerBar + wave * 0.2).coerceAtLeast(1.0)
            val high = maxOf(price, close) * (1.0 + amplitudePct)
            val low = minOf(price, close) * (1.0 - amplitudePct)
            out.add(
                CandlePoint(
                    timestamp = openTs,
                    open = price,
                    high = high,
                    low = low,
                    close = close,
                    volume = 120_000.0 + (i % 7) * 8_000.0,
                    isClosed = true,
                    closeTimestamp = openTs + step
                )
            )
            price = close
        }
        return out
    }

    @Test
    fun `btc bull 1h 4h with bear 5m produces LONG bias with 0_5x SHORT scalp and non-scalp best action`() = runTest {
        val now = 1_730_000_000_000L
        val livePrice = 92_000.0

        // Higher timeframes steadily bullish toward ~92,000
        val htf15m = buildTrendBars(now, Timeframe.FIFTEEN_MIN, 160, startPrice = 86_000.0, slopePerBar = 37.5)
        val htf1h = buildTrendBars(now, Timeframe.ONE_HOUR, 160, startPrice = 82_000.0, slopePerBar = 62.5)
        val htf4h = buildTrendBars(now, Timeframe.FOUR_HOURS, 160, startPrice = 76_000.0, slopePerBar = 100.0)
        val htf1d = buildTrendBars(now, Timeframe.ONE_DAY, 160, startPrice = 68_000.0, slopePerBar = 150.0)
        val htf1w = buildTrendBars(now, Timeframe.FIVE_DAYS, 80, startPrice = 58_000.0, slopePerBar = 425.0)
        val htf1m = buildTrendBars(now, Timeframe.ONE_MONTH, 48, startPrice = 42_000.0, slopePerBar = 1040.0)

        // 5m trigger candles start at 96,000 and drop sharply to ~92,000 (clearly below 5m EMA50, falling EMA12 < EMA26, RSI < 45)
        val bars5m = buildTrendBars(now, Timeframe.FIVE_MIN, 150, startPrice = 96_000.0, slopePerBar = -42.0)

        val verdict = MasterVerdictEngine.evaluate(
            MasterVerdictInput(
                symbol = "BTC",
                assetType = AssetType.CRYPTO,
                marketType = TradingMarketType.FUTURES,
                livePrice = livePrice,
                candlesByTf = mapOf(
                    Timeframe.FIVE_MIN to bars5m,
                    Timeframe.FIFTEEN_MIN to htf15m,
                    Timeframe.ONE_HOUR to htf1h,
                    Timeframe.FOUR_HOURS to htf4h,
                    Timeframe.ONE_DAY to htf1d,
                    Timeframe.FIVE_DAYS to htf1w,
                    Timeframe.ONE_MONTH to htf1m
                ),
                futuresPerpAvailable = true,
                nowMs = now
            )
        )

        assertEquals(PositionSide.LONG, verdict.weightedBiasSide)
        assertTrue("Headline should mention LONG bias: ${verdict.headline}", verdict.headline.contains("LONG bias"))
        val scalp = verdict.horizonPlans[TradingHorizon.SCALP]!!
        assertEquals(PositionSide.SHORT, scalp.side)
        assertTrue("Scalp SHORT must be tagged counter-trend", scalp.isCounterTrend)
        assertEquals(0.5, scalp.sizeMultiplier, 0.001)
        assertTrue("Conflicts should note Scalp SHORT opposing", verdict.conflicts.any { it.contains("Scalp SHORT opposes", true) })
        assertTrue(
            "Best horizon should prefer Intraday or Swing trend pullback over counter-trend Scalp (was ${verdict.bestHorizon})",
            verdict.bestHorizon == TradingHorizon.INTRADAY || verdict.bestHorizon == TradingHorizon.SWING || verdict.bestHorizon == TradingHorizon.POSITION
        )
    }

    @Test
    fun `spot bearish evaluates sell reduce with numeric next buy zone and futures short toggle`() = runTest {
        val now = 1_730_000_000_000L
        val livePrice = 140.0
        val map = mapOf(
            Timeframe.FIVE_MIN to buildTrendBars(now, Timeframe.FIVE_MIN, 140, 165.0, -0.18),
            Timeframe.FIFTEEN_MIN to buildTrendBars(now, Timeframe.FIFTEEN_MIN, 140, 172.0, -0.22),
            Timeframe.ONE_HOUR to buildTrendBars(now, Timeframe.ONE_HOUR, 140, 180.0, -0.28),
            Timeframe.FOUR_HOURS to buildTrendBars(now, Timeframe.FOUR_HOURS, 140, 195.0, -0.38),
            Timeframe.ONE_DAY to buildTrendBars(now, Timeframe.ONE_DAY, 140, 210.0, -0.48),
            Timeframe.FIVE_DAYS to buildTrendBars(now, Timeframe.FIVE_DAYS, 80, 220.0, -0.95),
            Timeframe.ONE_MONTH to buildTrendBars(now, Timeframe.ONE_MONTH, 48, 240.0, -2.0)
        )

        val spotVerdict = MasterVerdictEngine.evaluate(
            MasterVerdictInput(
                symbol = "SOL",
                assetType = AssetType.CRYPTO,
                marketType = TradingMarketType.SPOT,
                livePrice = livePrice,
                candlesByTf = map,
                futuresPerpAvailable = true,
                nowMs = now
            )
        )

        assertTrue("Spot bearish headline should indicate SELL / REDUCE or Futures short: ${spotVerdict.headline}",
            spotVerdict.headline.contains("SELL / REDUCE") || spotVerdict.headline.contains("BEAR", true) || spotVerdict.weightedBiasSide == PositionSide.SHORT)
        val best = spotVerdict.bestPlan!!
        assertEquals(SpotActionMode.SELL_REDUCE_AND_WAIT_BUY, best.spotView.mode)
        assertNotNull(best.spotView.buyZoneMin)
        assertNotNull(best.spotView.buyZoneMax)
        assertTrue(best.spotView.buyLimitPrice!! > 0.0 && best.spotView.buyLimitPrice!! < livePrice)
        assertTrue(best.spotView.futuresShortAvailable)
        assertFalse("Spot plain summary must not pretend you can open spot short", spotVerdict.plainBestPlanNumbers.contains("Spot SHORT", true))
    }

    @Test
    fun `horizon invariants hold for zone width stop position bsl ssl wilson ci and leverage`() = runTest {
        val now = 1_730_000_000_000L
        val livePrice = 100.0
        val candlesByTf = mapOf(
            Timeframe.FIVE_MIN to buildTrendBars(now, Timeframe.FIVE_MIN, 150, 92.0, 0.055),
            Timeframe.FIFTEEN_MIN to buildTrendBars(now, Timeframe.FIFTEEN_MIN, 150, 90.0, 0.068),
            Timeframe.ONE_HOUR to buildTrendBars(now, Timeframe.ONE_HOUR, 150, 86.0, 0.095),
            Timeframe.FOUR_HOURS to buildTrendBars(now, Timeframe.FOUR_HOURS, 150, 80.0, 0.135),
            Timeframe.ONE_DAY to buildTrendBars(now, Timeframe.ONE_DAY, 150, 72.0, 0.185),
            Timeframe.FIVE_DAYS to buildTrendBars(now, Timeframe.FIVE_DAYS, 80, 62.0, 0.45),
            Timeframe.ONE_MONTH to buildTrendBars(now, Timeframe.ONE_MONTH, 48, 50.0, 1.0)
        )
        val verdict = MasterVerdictEngine.evaluate(
            MasterVerdictInput(
                symbol = "ETH",
                assetType = AssetType.CRYPTO,
                marketType = TradingMarketType.FUTURES,
                livePrice = livePrice,
                candlesByTf = candlesByTf,
                riskSettings = RiskSettings(accountBalance = 10_000.0, maxRiskPerTradeDollars = 100.0, riskPercent = 1.0, isConfigured = true, maxLeverage = 20),
                futuresPerpAvailable = true,
                nowMs = now
            )
        )

        assertEquals(17 * 7, verdict.matrix.cells.size)
        assertEquals(AgentStatus.UNAVAILABLE, verdict.matrix.cell(AgentId.ORDER_FLOW, Timeframe.ONE_HOUR)?.status)

        for ((_, plan) in verdict.horizonPlans) {
            plan.bslLevel?.let { assertTrue("BSL ($it) must be strictly above livePrice ($livePrice)", it > livePrice) }
            plan.sslLevel?.let { assertTrue("SSL ($it) must be strictly below livePrice ($livePrice)", it < livePrice) }
            val setupBars = candlesByTf[plan.setupTf].orEmpty()
            val setupAtr = QuantitativeEngine.computeIndicators(setupBars).averageTrueRange
            val zMin = plan.entryZoneMin
            val zMax = plan.entryZoneMax
            val sl = plan.stopLoss
            if (zMin != null && zMax != null && sl != null && setupAtr > 0.0) {
                assertTrue("Zone width ${zMax - zMin} must be <= 0.75 * ATR ($setupAtr)", (zMax - zMin) <= (setupAtr * 0.7501))
                if (plan.side == PositionSide.LONG) {
                    assertTrue("Long SL $sl must be strictly below zone min $zMin", sl < zMin)
                } else if (plan.side == PositionSide.SHORT) {
                    assertTrue("Short SL $sl must be strictly above zone max $zMax", sl > zMax)
                }
            }
            if (plan.sampleN > 0) {
                val (wLo, wHi) = SignalCalibrationEngine.wilsonConfidenceInterval95(plan.winsK, plan.sampleN)
                assertEquals(wLo, plan.wilsonLowPct, 0.05)
                assertEquals(wHi, plan.wilsonHighPct, 0.05)
            }
            plan.futuresView?.let { fv ->
                val stopDist = abs(fv.limitPrice - fv.stopLoss)
                val liqDist = abs(fv.limitPrice - fv.liquidationPrice)
                assertTrue("Liquidation distance ($liqDist) must stay >= 2.5x stop distance ($stopDist)", liqDist >= stopDist * 2.5)
                assertEquals("Engine rec", fv.leverageSourceLabel)
            }
        }
    }

    @Test
    fun `incremental candle cache fetches only post-last-close bars and flags stale trigger gt 2x`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val cacheRepo = MarketCacheRepository(db, MarketDataService(), dbFile = null)
            val baseTime = 1_730_000_000_000L
            val initialBars = buildTrendBars(baseTime, Timeframe.FIVE_MIN, 120, 100.0, 0.1)

            // Initial backfill (no startTimeMs yet)
            var capturedStart: Long? = -1L
            val first = cacheRepo.getClosedCandlesIncremental(
                symbol = "BTC",
                assetType = AssetType.CRYPTO,
                marketType = TradingMarketType.FUTURES,
                timeframe = Timeframe.FIVE_MIN,
                nowMs = baseTime,
                remoteFetcher = { _, _, startMs, _, _ ->
                    capturedStart = startMs
                    initialBars
                }
            )
            assertEquals(null, capturedStart)
            assertEquals(120, first.candles.size)

            // Advance clock by 2 bars and test incremental request starts at lastClose + 1
            val lastClose = initialBars.maxOf { it.closeTimestamp }
            val nextTime = lastClose + (Timeframe.FIVE_MIN.stepMillis * 2L) + 1_000L
            val deltaBars = listOf(
                CandlePoint(lastClose, 112.0, 113.0, 111.5, 112.5, 10_000.0, isClosed = true, closeTimestamp = lastClose + Timeframe.FIVE_MIN.stepMillis),
                // Also include an open candle to prove it gets filtered out!
                CandlePoint(lastClose + Timeframe.FIVE_MIN.stepMillis, 112.5, 114.0, 112.0, 113.5, 5_000.0, isClosed = false, closeTimestamp = nextTime + 200_000L)
            )
            var incrementalStart: Long? = null
            val second = cacheRepo.getClosedCandlesIncremental(
                symbol = "BTC",
                assetType = AssetType.CRYPTO,
                marketType = TradingMarketType.FUTURES,
                timeframe = Timeframe.FIVE_MIN,
                nowMs = nextTime,
                remoteFetcher = { _, _, startMs, _, _ ->
                    incrementalStart = startMs
                    deltaBars
                }
            )
            assertEquals(lastClose + 1L, incrementalStart)
            assertEquals(121, second.candles.size)

            // Check stale flag when trigger age > 2x triggerTf step (5m * 2 + 15s = 615s)
            val verdict = MasterVerdictEngine.evaluate(
                MasterVerdictInput(
                    symbol = "BTC",
                    assetType = AssetType.CRYPTO,
                    marketType = TradingMarketType.FUTURES,
                    livePrice = 112.5,
                    candlesByTf = mapOf(Timeframe.FIVE_MIN to second.candles),
                    dataAgeByTf = mapOf(Timeframe.FIVE_MIN to 700_000L), // > 2 * 5m + 15s
                    aiCallback = { throw RuntimeException("Simulated timeout") },
                    nowMs = nextTime
                )
            )
            val scalp = verdict.horizonPlans[TradingHorizon.SCALP]!!
            assertTrue("Scalp plan must be flagged stale when 5m age > 2x interval", scalp.isStale)
            assertEquals(SignalVerdictState.NO_TRADE, scalp.state)
            assertEquals(AgentStatus.FAILED, verdict.matrix.cell(AgentId.AI_AGENT, Timeframe.FIVE_MIN)?.status)
        } finally {
            db.close()
        }
    }
}
