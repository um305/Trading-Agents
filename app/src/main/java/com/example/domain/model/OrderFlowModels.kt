package com.example.domain.model

/**
 * Order Book & Order Flow Data Models for Binance USDS-M Perpetuals
 */

data class OrderBookLevel(
    val price: Double,
    val size: Double,
    val notional: Double
)

data class OrderBookSnapshot(
    val bids: List<OrderBookLevel>,
    val asks: List<OrderBookLevel>,
    val lastUpdateId: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

data class OrderBookWall(
    val price: Double,
    val notional: Double,
    val distancePercent: Double,
    val isBid: Boolean,
    val spoofScore: Double, // 0.0 to 1.0 (higher = frequently pulled when price approaches)
    val ageSeconds: Int = 30,
    val isPersistent: Boolean = true
)

data class DepthBandMetrics(
    val bandPercent: Double, // e.g. 0.1, 0.25, 0.5, 1.0, 2.0
    val bidNotional: Double,
    val askNotional: Double,
    val imbalanceRatio: Double // (bid - ask) / (bid + ask) from -1.0 to +1.0
)

data class DepthHeatmapPoint(
    val timestamp: Long,
    val price: Double,
    val notional: Double,
    val isBid: Boolean
)

data class AggTradePrint(
    val aggregateTradeId: Long,
    val price: Double,
    val quantity: Double,
    val notional: Double,
    val isBuyerMaker: Boolean, // if true, seller was aggressive (aggressor SELL); if false, buyer was aggressive (aggressor BUY)
    val timestamp: Long
)

data class OrderFlowReport(
    val symbol: String,
    val biasScore: Double, // -100.0 (extreme bearish) to +100.0 (extreme bullish)
    val confidenceScore: Int, // 0 to 100
    val stance: AgentStance,
    val spreadTicks: Double,
    val spreadBps: Double,
    val microPrice: Double,
    val vwapMid: Double,
    val depthBands: List<DepthBandMetrics>,
    val supportWalls: List<OrderBookWall>,
    val resistanceWalls: List<OrderBookWall>,
    val cvdDelta1m: Double,
    val cvdDelta5m: Double,
    val cvdDelta15m: Double,
    val aggressorBuyRatio: Double, // e.g. 0.58 = 58% aggressive buyers
    val absorptionDetected: Boolean,
    val absorptionDetails: String,
    val exhaustionDetected: Boolean,
    val oiQuadrantState: String, // "Long Buildup", "Short Buildup", "Long Liquidation", "Short Covering"
    val expectedSlippageBps: Double,
    val doNotTradeFlags: List<String>,
    val exactTriggerCondition: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isTradeable: Boolean get() = doNotTradeFlags.isEmpty()
}
