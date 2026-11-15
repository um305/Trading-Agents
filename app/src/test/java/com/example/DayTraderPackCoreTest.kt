package com.example

import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.plan.ExitPlanner
import com.example.domain.daytrader.plan.StopRuleType
import com.example.domain.daytrader.plan.TargetRuleType
import com.example.domain.daytrader.risk.RiskBudgetEngine
import com.example.domain.daytrader.risk.RiskSettings
import com.example.domain.daytrader.scanner.DayTraderScannerEngine
import com.example.domain.daytrader.scanner.ScanRuleType
import com.example.domain.daytrader.session.MarketCalendar
import com.example.domain.daytrader.timer.EntryTimerState
import com.example.domain.daytrader.timer.EntryTimerStateMachine
import com.example.domain.model.CandlePoint
import com.example.domain.model.PositionSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DayTraderPackCoreTest {

    @Test
    fun testExitPlanner_RefusalsAndTickRounding() {
        val stockProfile = MarketProfile.defaultUsStock()

        // 1. Long stop at or above entry -> Refused
        val plan1 = ExitPlanner.createPlan(
            symbol = "AAPL",
            side = PositionSide.LONG,
            entryPrice = 150.0,
            stopRule = StopRuleType.MANUAL_PRICE,
            rawStopPrice = 150.50, // above entry
            tickSize = 0.01,
            profile = stockProfile
        )
        assertNotNull("Stop above entry for LONG must be refused", plan1.validationError)
        assertTrue(plan1.validationError!!.contains("Refused"))

        // 2. Risk less than minimum breathing room (max(0.05% of 100 = 0.05, 3x spread)) -> Refused
        val plan2 = ExitPlanner.createPlan(
            symbol = "AAPL",
            side = PositionSide.LONG,
            entryPrice = 100.0,
            stopRule = StopRuleType.MANUAL_PRICE,
            rawStopPrice = 99.98, // risk is only 0.02
            spread = 0.02,        // 3x spread = 0.06
            tickSize = 0.01,
            profile = stockProfile
        )
        assertNotNull("Micro-stop below min allowed risk must be refused", plan2.validationError)

        // 3. Round stop AWAY from entry to tick size
        // LONG: entry 100.0, raw stop 98.456 -> floor to 0.05 tick size = 98.45
        val plan3 = ExitPlanner.createPlan(
            symbol = "AAPL",
            side = PositionSide.LONG,
            entryPrice = 100.0,
            stopRule = StopRuleType.MANUAL_PRICE,
            rawStopPrice = 98.456,
            tickSize = 0.05,
            profile = stockProfile
        )
        assertNull("Valid plan must have no validation error", plan3.validationError)
        assertEquals(98.45, plan3.stopPrice, 0.0001)

        // SHORT: entry 100.0, raw stop 101.431 -> ceil to 0.05 tick size = 101.45 (rounded away from entry)
        val plan4 = ExitPlanner.createPlan(
            symbol = "AAPL",
            side = PositionSide.SHORT,
            entryPrice = 100.0,
            stopRule = StopRuleType.MANUAL_PRICE,
            rawStopPrice = 101.431,
            tickSize = 0.05,
            profile = stockProfile
        )
        assertNull(plan4.validationError)
        assertEquals(101.45, plan4.stopPrice, 0.0001)
    }

    @Test
    fun testRiskBudgetEngine_SizerFloorAndBlockOnZeroSize() {
        val stockProfile = MarketProfile.defaultUsStock()
        val plan = ExitPlanner.createPlan(
            symbol = "BRK",
            side = PositionSide.LONG,
            entryPrice = 500000.0,
            stopRule = StopRuleType.MANUAL_PRICE,
            rawStopPrice = 490000.0, // $10,000 risk per share
            profile = stockProfile
        )

        // Max risk budget is $250, but risk per unit is $10,000 -> quantity rounds to 0 shares!
        val settings = RiskSettings(
            accountBalance = 25000.0,
            maxRiskPerTradeDollars = 250.0,
            isConfigured = true
        )

        val eval = RiskBudgetEngine.evaluateRiskAndSize(
            plan = plan,
            profile = stockProfile,
            settings = settings,
            lotStepSize = 1.0 // 1 share
        )

        assertFalse("Size exceeding budget must disable execution until user accepts higher risk", eval.isAllowed)
        assertTrue("Min notional flag must be set", eval.isMinNotionalExceeded)
        assertTrue("Paper follow remains allowed for small account min-notional warning", eval.paperFollowAllowed)
        assertTrue(
            "Blocked reasons must explain minimum order exceeds risk budget",
            eval.blockedReasons.any { it.contains("Minimum order exceeds your risk budget") }
        )
        assertNull("No order ticket should be generated without override", eval.orderTicket)
    }

    @Test
    fun testScanner_RulesOnFixtures_BreakoutAndBreakdown() {
        // Build 25 daily candles
        val startTs = 1700000000000L
        val dayMs = 86400000L

        // Bullish 20-day high breakout fixture
        val bullCandles = (0..24).map { i ->
            val p = 100.0 + i * 0.5
            CandlePoint(startTs + i * dayMs, p - 0.2, p + 0.8, p - 0.5, p, 10000.0)
        }

        val bullHits = DayTraderScannerEngine.evaluateRulesForSymbol("BULL", bullCandles, emptyList())
        assertTrue("Bullish 20-day breakout must be detected", bullHits.any { it.matchedRule == ScanRuleType.BREAKOUT_20D_HIGH })

        // Bearish 20-day low breakdown fixture
        val bearCandles = (0..24).map { i ->
            val p = 150.0 - i * 0.8
            CandlePoint(startTs + i * dayMs, p + 0.2, p + 0.5, p - 0.8, p, 10000.0)
        }

        val bearHits = DayTraderScannerEngine.evaluateRulesForSymbol("BEAR", bearCandles, emptyList())
        assertTrue("Bearish 20-day breakdown must be detected", bearHits.any { it.matchedRule == ScanRuleType.BREAKDOWN_20D_LOW })
    }

    @Test
    fun testEntryTimer_TransitionsOnlyOnClosedBars() {
        val profile = MarketProfile.defaultUsStock()
        val plan = ExitPlanner.createPlan(
            symbol = "NVDA",
            side = PositionSide.LONG,
            entryPrice = 120.0,
            stopRule = StopRuleType.MANUAL_PRICE,
            rawStopPrice = 117.0,
            profile = profile
        )

        val barOverEntry = CandlePoint(1700000000000L, 119.5, 121.0, 119.0, 120.5, 50000.0)

        // 1. Bar is still forming (isClosed = false): Must NOT trigger!
        val formingStatus = EntryTimerStateMachine.evaluate(
            plan = plan,
            latestCompletedBar = barOverEntry,
            isCurrentBarClosed = false,
            profile = profile
        )
        assertEquals("In-progress bar must show waiting: bar still forming notice", "waiting: bar still forming", formingStatus.isBarStillFormingNotice)

        // 2. Bar is completed and closed (isClosed = true): TRIGGERED!
        val closedStatus = EntryTimerStateMachine.evaluate(
            plan = plan,
            latestCompletedBar = barOverEntry,
            isCurrentBarClosed = true,
            profile = profile
        )
        assertEquals("Completed bar closed beyond level must trigger", EntryTimerState.TRIGGERED, closedStatus.state)
        assertTrue(closedStatus.isActionableNow)
    }

    @Test
    fun testMarketCalendar_FlatByOnEarlyCloseDays() {
        // Black Friday 2026: Nov 27, 2026 is an NYSE 13:00 Early Close day!
        val cal = Calendar.getInstance(MarketCalendar.NY_TZ).apply {
            set(2026, Calendar.NOVEMBER, 27, 10, 0, 0)
        }
        val isEarly = MarketCalendar.isNyseEarlyCloseDay(cal)
        assertTrue("Black Friday must be recognized as NYSE Early Close", isEarly)

        val flatByTs = MarketCalendar.getStockFlatByTimestamp(cal.timeInMillis)
        val flatCal = Calendar.getInstance(MarketCalendar.NY_TZ).apply { timeInMillis = flatByTs }

        assertEquals("Early close flat-by hour must be 12", 12, flatCal.get(Calendar.HOUR_OF_DAY))
        assertEquals("Early close flat-by minute must be 55", 55, flatCal.get(Calendar.MINUTE))
    }
}
