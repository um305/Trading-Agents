package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.background.BackgroundMonitorMode
import com.example.data.background.BackgroundPreferences
import com.example.data.background.BinanceKlineWebSocketClient
import com.example.data.background.BootCompletedReceiver
import com.example.data.background.MarketMonitorWorker
import com.example.data.background.QuietHoursConfig
import com.example.data.notification.PriceAlertNotificationManager
import com.example.domain.master.MasterVerdictEngine
import com.example.domain.master.MasterVerdictInput
import com.example.domain.master.TradingHorizon
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MasterVerdictPartBTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder().build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        BackgroundPreferences.setQuietHours(context, QuietHoursConfig(enabled = false))
        BackgroundPreferences.setMode(context, BackgroundMonitorMode.STANDARD)
    }

    private fun buildCandles(nowMs: Long, tf: Timeframe, count: Int, start: Double, slope: Double): List<CandlePoint> {
        val step = if (tf == Timeframe.FIVE_DAYS) 7L * 86_400_000L else tf.stepMillis
        val first = nowMs - (count.toLong() * step) - 5_000L
        var p = start
        return (0 until count).map { i ->
            val ts = first + i * step
            val close = (p + slope).coerceAtLeast(1.0)
            val bar = CandlePoint(
                timestamp = ts,
                open = p,
                high = maxOf(p, close) * 1.003,
                low = minOf(p, close) * 0.997,
                close = close,
                volume = 50_000.0,
                isClosed = true,
                closeTimestamp = ts + step
            )
            p = close
            bar
        }
    }

    @Test
    fun `closed bar selectively recomputes only affected horizons`() = runTest {
        assertEquals(setOf(TradingHorizon.SCALP), TradingHorizon.affectedByClosedTimeframe(Timeframe.FIVE_MIN))
        assertEquals(setOf(TradingHorizon.SCALP, TradingHorizon.INTRADAY), TradingHorizon.affectedByClosedTimeframe(Timeframe.ONE_HOUR))
        assertEquals(setOf(TradingHorizon.SWING), TradingHorizon.affectedByClosedTimeframe(Timeframe.FOUR_HOURS))
        assertEquals(setOf(TradingHorizon.POSITION, TradingHorizon.OUTLOOK), TradingHorizon.affectedByClosedTimeframe(Timeframe.ONE_DAY))

        val t0 = 1_730_000_000_000L
        val map = mapOf(
            Timeframe.FIVE_MIN to buildCandles(t0, Timeframe.FIVE_MIN, 130, 90.0, 0.08),
            Timeframe.FIFTEEN_MIN to buildCandles(t0, Timeframe.FIFTEEN_MIN, 130, 88.0, 0.10),
            Timeframe.ONE_HOUR to buildCandles(t0, Timeframe.ONE_HOUR, 130, 85.0, 0.12),
            Timeframe.FOUR_HOURS to buildCandles(t0, Timeframe.FOUR_HOURS, 130, 80.0, 0.16),
            Timeframe.ONE_DAY to buildCandles(t0, Timeframe.ONE_DAY, 130, 72.0, 0.22),
            Timeframe.FIVE_DAYS to buildCandles(t0, Timeframe.FIVE_DAYS, 70, 62.0, 0.45)
        )
        val initial = MasterVerdictEngine.evaluate(
            MasterVerdictInput(
                symbol = "BTC",
                assetType = AssetType.CRYPTO,
                marketType = TradingMarketType.FUTURES,
                livePrice = 100.5,
                candlesByTf = map,
                nowMs = t0
            )
        )

        val t1 = t0 + 300_000L
        val updated = MasterVerdictEngine.evaluate(
            input = MasterVerdictInput(
                symbol = "BTC",
                assetType = AssetType.CRYPTO,
                marketType = TradingMarketType.FUTURES,
                livePrice = 101.0,
                candlesByTf = map,
                nowMs = t1
            ),
            existingVerdict = initial,
            horizonsToRecompute = TradingHorizon.affectedByClosedTimeframe(Timeframe.FIVE_MIN)
        )

        assertEquals(t1, updated.horizonPlans[TradingHorizon.SCALP]!!.computedAtMs)
        assertEquals(t0, updated.horizonPlans[TradingHorizon.SWING]!!.computedAtMs)
        assertEquals(t0, updated.horizonPlans[TradingHorizon.POSITION]!!.computedAtMs)
    }

    @Test
    fun `quiet hours, 6 per hour cap, and 30m per symbol cooldown are enforced`() {
        val mgr = PriceAlertNotificationManager(context)
        mgr.clearThrottleHistoryForTest()

        // Quiet hours 23..7 wraps midnight
        BackgroundPreferences.setQuietHours(context, QuietHoursConfig(enabled = true, startHour = 23, endHour = 7))
        val (allowedAt2Am, reason2Am) = mgr.canDispatchBackgroundAlert("BTC", nowMs = 1_730_000_000_000L, hourOfDayOverride = 2)
        assertFalse(allowedAt2Am)
        assertTrue(reason2Am!!.contains("quiet hours", true))

        val (allowedAt2Pm, _) = mgr.canDispatchBackgroundAlert("BTC", nowMs = 1_730_000_000_000L, hourOfDayOverride = 14)
        assertTrue(allowedAt2Pm)

        // Disable quiet hours, test per-symbol 30 min cooldown & 6/hour global cap
        BackgroundPreferences.setQuietHours(context, QuietHoursConfig(enabled = false))
        val now = 1_730_000_000_000L
        val method = PriceAlertNotificationManager::class.java.getDeclaredMethod("recordNotificationDispatched", String::class.java, Long::class.javaPrimitiveType)
        method.isAccessible = true

        // Dispatch BTC once -> immediate duplicate within 30m is blocked
        method.invoke(mgr, "BTC", now)
        val (btcTooSoon, btcReason) = mgr.canDispatchBackgroundAlert("BTC", now + 5 * 60_000L, hourOfDayOverride = 12)
        assertFalse(btcTooSoon)
        assertTrue(btcReason!!.contains("cooldown", true))

        // Allowed after 31m for same symbol
        val (btcAfter31m, _) = mgr.canDispatchBackgroundAlert("BTC", now + 31 * 60_000L, hourOfDayOverride = 12)
        assertTrue(btcAfter31m)

        // Fill remaining 5 slots in global 6/hr cap with other symbols -> 7th blocked
        listOf("ETH", "SOL", "ADA", "XRP", "DOGE").forEachIndexed { idx, sym ->
            method.invoke(mgr, sym, now + (idx + 1) * 60_000L)
        }
        val (seventhAllowed, seventhReason) = mgr.canDispatchBackgroundAlert("LINK", now + 10 * 60_000L, hourOfDayOverride = 12)
        assertFalse(seventhAllowed)
        assertTrue(seventhReason!!.contains("6/hour"))
    }

    @Test
    fun `websocket parser ignores forming candles and emits only closed klines`() {
        val openKlineJson = """
            {"stream":"btcusdt@kline_5m","data":{"e":"kline","s":"BTCUSDT","k":{"t":1730000000000,"T":1730000299999,"i":"5m","o":"91000","c":"91200","h":"91300","l":"90900","v":"120","x":false}}}
        """.trimIndent()
        assertNull(BinanceKlineWebSocketClient.parseClosedKlineMessage(openKlineJson))

        val closedKlineJson = """
            {"stream":"btcusdt@kline_5m","data":{"e":"kline","s":"BTCUSDT","k":{"t":1730000000000,"T":1730000299999,"i":"5m","o":"91000","c":"91500","h":"91600","l":"90950","v":"450","x":true}}}
        """.trimIndent()
        val parsed = BinanceKlineWebSocketClient.parseClosedKlineMessage(closedKlineJson)
        assertNotNull(parsed)
        assertEquals("BTC", parsed!!.symbol)
        assertEquals(Timeframe.FIVE_MIN, parsed.timeframe)
        assertTrue(parsed.candle.isClosed)
        assertEquals(91_500.0, parsed.candle.close, 0.01)
    }

    @Test
    fun `boot receiver restores periodic standard worker and off mode worker succeeds immediately`() = runTest {
        BackgroundPreferences.setMode(context, BackgroundMonitorMode.STANDARD)
        BootCompletedReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(MarketMonitorWorker.UNIQUE_WORK_NAME).get()
        assertTrue("Expected periodic MarketMonitorWorker enqueued after boot", workInfos.isNotEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)

        // Setting mode OFF makes worker exit cleanly with Result.success()
        BackgroundPreferences.setMode(context, BackgroundMonitorMode.OFF)
        val worker = TestListenableWorkerBuilder<MarketMonitorWorker>(context).build()
        val res = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), res)
    }
}
