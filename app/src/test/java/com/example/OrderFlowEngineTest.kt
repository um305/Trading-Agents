package com.example

import com.example.domain.engine.OrderFlowEngine
import com.example.domain.model.AgentStance
import com.example.domain.model.AggTradePrint
import com.example.domain.model.OrderBookLevel
import com.example.domain.model.OrderBookSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderFlowEngineTest {

    @Test
    fun testOrderFlowEngine_spreadAndDepthCalculations() {
        val bids = listOf(
            OrderBookLevel(100.0, 10.0, 1000.0),
            OrderBookLevel(99.8, 50.0, 4990.0),
            OrderBookLevel(99.5, 200.0, 19900.0),
            OrderBookLevel(99.0, 1000.0, 99000.0) // Big support wall
        )
        val asks = listOf(
            OrderBookLevel(100.1, 8.0, 800.8),
            OrderBookLevel(100.3, 30.0, 3009.0),
            OrderBookLevel(100.5, 100.0, 10050.0),
            OrderBookLevel(101.0, 200.0, 20200.0)
        )
        val snapshot = OrderBookSnapshot(bids, asks)

        val report = OrderFlowEngine.analyzeOrderFlow(
            symbol = "SOLUSDT",
            currentPrice = 100.05,
            snapshot = snapshot,
            tickSize = 0.01
        )

        // Spread is 100.1 - 100.0 = 0.1, which is 10 ticks (0.1 / 0.01)
        assertEquals(10.0, report.spreadTicks, 0.01)
        assertTrue(report.depthBands.isNotEmpty())
        assertTrue(report.supportWalls.any { it.price == 99.0 })
    }

    @Test
    fun testOrderFlowEngine_bullishAbsorption() {
        val bids = listOf(
            OrderBookLevel(100.0, 5000.0, 500000.0) // Massive wall at 100.0
        )
        val asks = listOf(
            OrderBookLevel(100.05, 50.0, 5002.5)
        )
        val snapshot = OrderBookSnapshot(bids, asks)

        val now = System.currentTimeMillis()
        val trades = listOf(
            AggTradePrint(1L, 100.01, 1000.0, 100010.0, isBuyerMaker = true, timestamp = now - 10_000), // Heavy aggressive market selling into the bid
            AggTradePrint(2L, 100.01, 1000.0, 100010.0, isBuyerMaker = true, timestamp = now - 5_000)
        )

        val report = OrderFlowEngine.analyzeOrderFlow(
            symbol = "SOLUSDT",
            currentPrice = 100.02,
            snapshot = snapshot,
            recentTrades = trades
        )

        assertTrue("Should detect bullish absorption", report.absorptionDetected)
        assertTrue(report.absorptionDetails.contains("Bullish Absorption"))
    }
}
