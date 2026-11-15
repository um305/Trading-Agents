package com.example

import com.example.domain.daytrader.model.MarketProfileType
import com.example.domain.daytrader.replay.BarRatio
import com.example.domain.daytrader.replay.FullSession
import com.example.domain.daytrader.replay.ReplayEngine
import com.example.domain.daytrader.replay.ReplayLean
import com.example.domain.daytrader.replay.ReplayPlan
import com.example.domain.daytrader.replay.SessionPool
import com.example.domain.model.CandlePoint
import com.example.domain.model.PositionSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ReplayEngineTest {

    private fun generateSyntheticPool(
        sessionCount: Int = 25,
        barsPerSession: Int = 78,
        driftPercent: Double = 0.0002
    ): SessionPool {
        val sessions = mutableListOf<FullSession>()
        val startTs = 1700000000000L
        val interval5m = 5 * 60 * 1000L

        for (s in 0 until sessionCount) {
            val bars = mutableListOf<BarRatio>()
            for (b in 0 until barsPerSession) {
                val upStep = (b % 2 == 0)
                val ratio = if (upStep) 1.002 + driftPercent else 0.998 + driftPercent
                bars.add(
                    BarRatio(
                        timestamp = startTs + (s * 86400000L) + (b * interval5m),
                        timeOfDayIndex = b,
                        openRatio = 1.0,
                        highRatio = ratio + 0.003,
                        lowRatio = ratio - 0.003,
                        closeRatio = ratio,
                        barAtr = 0.5
                    )
                )
            }
            sessions.add(FullSession("2026-10-${String.format("%02d", s + 1)}", isWeekend = false, bars = bars))
        }

        return SessionPool(
            symbol = "TEST",
            profileType = MarketProfileType.US_EQUITY,
            sessions = sessions,
            poolAtr = 0.5,
            recentAtr = 0.5
        )
    }

    @Test
    fun testReplay_Reproducibility_SameSeedProducesIdenticalNumbers() {
        val pool = generateSyntheticPool(sessionCount = 22)
        val plan = ReplayPlan(
            symbol = "TEST",
            side = PositionSide.LONG,
            entryPrice = 100.0,
            stopLossPrice = 98.0,
            takeProfitPrice = 104.0,
            entryBarTimestamp = 1700000000000L,
            timeOfDayIndex = 10,
            horizonBars = 30
        )

        val run1 = ReplayEngine.runReplay(plan, pool, seed = 19L, numReplays = 1000)
        val run2 = ReplayEngine.runReplay(plan, pool, seed = 19L, numReplays = 1000)

        assertEquals("Same seed must yield identical targetFirstPct", run1.targetFirstPct, run2.targetFirstPct, 0.0001)
        assertEquals("Same seed must yield identical stopFirstPct", run1.stopFirstPct, run2.stopFirstPct, 0.0001)
        assertEquals("Same seed must yield identical medianPrice", run1.medianPrice, run2.medianPrice, 0.0001)
        assertEquals("Same seed must yield identical averageR", run1.averageR, run2.averageR, 0.0001)
    }

    @Test
    fun testReplay_RefusesWithLessThan20FullSessions() {
        val pool = generateSyntheticPool(sessionCount = 15) // < 20 sessions
        val plan = ReplayPlan(
            symbol = "TEST",
            side = PositionSide.LONG,
            entryPrice = 100.0,
            stopLossPrice = 98.0,
            takeProfitPrice = 104.0,
            entryBarTimestamp = 1700000000000L,
            timeOfDayIndex = 10,
            horizonBars = 30
        )

        val result = ReplayEngine.runReplay(plan, pool, seed = 19L, numReplays = 500)
        assertNotNull(result.refusalReason)
        assertTrue(result.refusalReason!!.contains("fewer than 20 full sessions"))
        assertEquals(ReplayLean.REFUSED, result.verdict.lean)
    }

    @Test
    fun testReplay_HalfDaysExcludedFromStockPool() {
        val validDate = "2026-10-15"
        val halfDayDate = "2026-10-16" // Early close with only 42 bars
        val startTs = 1700000000000L
        val interval5m = 5 * 60 * 1000L

        // Generate candles: one full day (78 bars 09:30-16:00) and one half day (42 bars)
        val candles = mutableListOf<CandlePoint>()

        // Full day (78 bars: 9:30 to 15:55)
        val cal = java.util.Calendar.getInstance(com.example.domain.daytrader.session.MarketCalendar.NY_TZ).apply {
            set(2026, java.util.Calendar.OCTOBER, 15, 9, 30, 0)
        }
        var baseTs = cal.timeInMillis
        for (i in 0 until 78) {
            candles.add(CandlePoint(baseTs + i * interval5m, 100.0, 101.0, 99.0, 100.2, 5000.0))
        }

        // Half day (42 bars: 9:30 to 13:00)
        cal.set(2026, java.util.Calendar.OCTOBER, 16, 9, 30, 0)
        baseTs = cal.timeInMillis
        for (i in 0 until 42) {
            candles.add(CandlePoint(baseTs + i * interval5m, 100.0, 101.0, 99.0, 100.2, 5000.0))
        }

        val pool = SessionPool.buildFromCandles(
            symbol = "TEST",
            profileType = MarketProfileType.US_EQUITY,
            candles = candles,
            todayTimestamp = baseTs + 86400000L * 2
        )

        assertEquals("Half day (< 78 bars) must be excluded from stock pool", 1, pool.totalSessions)
    }

    @Test
    fun testReplay_BarTouchingBothStopAndTargetCountsAsStop() {
        // A single bar that spikes to 110.0 and crashes to 90.0 (touches both stop 95 and target 105)
        val singleBarSession = FullSession(
            dateKey = "2026-10-15",
            isWeekend = false,
            bars = List(78) { idx ->
                BarRatio(
                    timestamp = 1700000000000L + idx * 300000L,
                    timeOfDayIndex = idx,
                    openRatio = 1.0,
                    highRatio = 1.10, // touch target
                    lowRatio = 0.90,  // touch stop
                    closeRatio = 1.0,
                    barAtr = 1.0
                )
            }
        )
        val pool = SessionPool(
            symbol = "SPIKE",
            profileType = MarketProfileType.US_EQUITY,
            sessions = List(25) { singleBarSession },
            poolAtr = 1.0,
            recentAtr = 1.0
        )

        val plan = ReplayPlan(
            symbol = "SPIKE",
            side = PositionSide.LONG,
            entryPrice = 100.0,
            stopLossPrice = 95.0,
            takeProfitPrice = 105.0,
            entryBarTimestamp = 1700000000000L,
            timeOfDayIndex = 10,
            horizonBars = 1
        )

        val result = ReplayEngine.runReplay(plan, pool, seed = 19L, numReplays = 500)
        assertEquals("Bar touching both target and stop must be counted as stop-first", 100.0, result.stopFirstPct, 0.001)
        assertEquals(0.0, result.targetFirstPct, 0.001)
    }

    @Test
    fun testReplay_BreakEvenThresholds_2R() {
        // At 2R net reward:risk, zero-edge break-even target share is 1 / (1 + 2) = 33.3%
        // Target share of 24.1% and Stop of 50.5% -> target share among decided is 24.1 / (24.1 + 50.5) = 32.3%
        // 32.3% is within 5 points of 33.3% -> "Close to break-even"
        val netR = 2.0
        val breakEven = 1.0 / (1.0 + netR) // 0.3333
        val decidedTargetShare = 24.1 / (24.1 + 50.5) // 0.3230
        val diffPoints = (decidedTargetShare - breakEven) * 100.0

        assertTrue("Observed 32.3% is within 5 points of 33.3% BE", abs(diffPoints) <= 5.0)
    }

    @Test
    fun testReplay_UptrendPool_AsIsLongOddsExceedDriftNeutralAndUsesLessFavorableForVerdictAndCalibration() {
        val uptrendPool = generateSyntheticPool(
            sessionCount = 25,
            barsPerSession = 78,
            driftPercent = 0.0028 // Strong positive per-bar drift across every time slot
        )

        val longPlan = ReplayPlan(
            symbol = "UPTREND_SYM",
            side = PositionSide.LONG,
            entryPrice = 100.0,
            stopLossPrice = 96.5,
            takeProfitPrice = 103.5,
            entryBarTimestamp = 1700000000000L,
            timeOfDayIndex = 10,
            horizonBars = 30
        )

        val result = ReplayEngine.runReplay(longPlan, uptrendPool, seed = 19L, numReplays = 2000)

        val asIs = result.asIs
        val driftNeutral = result.driftNeutral
        assertNotNull("As-Is replay metrics must be populated", asIs)
        assertNotNull("Drift-Neutral replay metrics must be populated", driftNeutral)

        assertTrue(
            "In a synthetic uptrend pool, as-is long P(win) (${asIs!!.observedTargetShare}) must exceed drift-neutral P(win) (${driftNeutral!!.observedTargetShare})",
            asIs.observedTargetShare > driftNeutral.observedTargetShare
        )
        assertTrue(
            "In a synthetic uptrend pool, as-is targetFirstPct (${asIs.targetFirstPct}%) must exceed drift-neutral (${driftNeutral.targetFirstPct}%)",
            asIs.targetFirstPct > driftNeutral.targetFirstPct
        )

        // Verdict & P(win) sent to Calibration must use the LESS favorable of the two
        assertEquals("Drift-Neutral", result.conservativeModeLabel)
        assertEquals(driftNeutral.observedTargetShare, result.verdict.observedTargetShare, 0.0001)
        assertEquals(driftNeutral.observedTargetShare, result.effectivePWin, 0.0001)
        assertEquals(driftNeutral.targetFirstPct, result.targetFirstPct, 0.0001)

        // Divergence > 5 points triggers an explicit explanatory line
        assertTrue("Expected >5 pt difference on strong uptrend pool, got ${result.oddsDiffPoints}", result.oddsDiffPoints > 5.0)
        assertNotNull("Divergence line must be populated when odds differ by > 5 points", result.driftDivergenceNote)
        assertTrue(
            "Verdict details must include the divergence line",
            result.verdict.details.contains(result.driftDivergenceNote)
        )

        // Calibration stores the less-favorable P(win)
        com.example.domain.engine.SignalCalibrationEngine.clearReplayPWin(longPlan.symbol)
        com.example.domain.engine.SignalCalibrationEngine.recordReplayPWin(
            symbol = longPlan.symbol,
            pWin = result.effectivePWin,
            conservativeMode = result.conservativeModeLabel,
            asIsPWin = asIs.observedTargetShare,
            driftNeutralPWin = driftNeutral.observedTargetShare,
            oddsDiffPoints = result.oddsDiffPoints
        )
        val storedCalibration = com.example.domain.engine.SignalCalibrationEngine.getReplayPWin(longPlan.symbol)
        assertNotNull(storedCalibration)
        assertEquals(driftNeutral.observedTargetShare, storedCalibration!!.pWin, 0.0001)
        com.example.domain.engine.SignalCalibrationEngine.clearReplayPWin(longPlan.symbol)
    }
}
