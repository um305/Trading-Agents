package com.example.domain.engine

import com.example.domain.model.AgentStance
import com.example.domain.model.AggTradePrint
import com.example.domain.model.DepthBandMetrics
import com.example.domain.model.OrderBookLevel
import com.example.domain.model.OrderBookSnapshot
import com.example.domain.model.OrderBookWall
import com.example.domain.model.OrderFlowReport
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure Kotlin Order Flow and Depth Analysis Engine.
 * Implements Level-2 order book depth analysis, wall detection, spoofing heuristics,
 * Cumulative Volume Delta (CVD), absorption detector, and order flow bias.
 */
object OrderFlowEngine {

    val DEPTH_BANDS = listOf(0.1, 0.25, 0.5, 1.0, 2.0)

    fun analyzeOrderFlow(
        symbol: String,
        currentPrice: Double,
        snapshot: OrderBookSnapshot,
        recentTrades: List<AggTradePrint> = emptyList(),
        openInterestChange24h: Double = 0.0,
        priceChange24h: Double = 0.0,
        fundingRate: Double = 0.0001,
        positionSizeUsdt: Double = 10000.0,
        tickSize: Double = 0.01
    ): OrderFlowReport {
        val safePrice = if (currentPrice > 0.0) currentPrice else (snapshot.bids.firstOrNull()?.price ?: 100.0)
        val bestBid = snapshot.bids.firstOrNull()?.price ?: (safePrice * 0.9999)
        val bestAsk = snapshot.asks.firstOrNull()?.price ?: (safePrice * 1.0001)

        // 1. Spread Calculation
        val rawSpread = max(bestAsk - bestBid, 0.0)
        val spreadTicks = if (tickSize > 0.0) rawSpread / tickSize else 1.0
        val spreadBps = if (safePrice > 0.0) (rawSpread / safePrice) * 10000.0 else 1.0

        // 2. Microprice & Volume-Weighted Mid
        val topBidSize = snapshot.bids.take(5).sumOf { it.notional }.coerceAtLeast(1.0)
        val topAskSize = snapshot.asks.take(5).sumOf { it.notional }.coerceAtLeast(1.0)
        val microPrice = ((bestBid * topAskSize) + (bestAsk * topBidSize)) / (topBidSize + topAskSize)
        val vwapMid = (bestBid + bestAsk) / 2.0

        // 3. Multi-Band Depth & Imbalance (+/-0.1%, 0.25%, 0.5%, 1.0%, 2.0%)
        val depthBands = DEPTH_BANDS.map { bandPct ->
            val bidFloor = safePrice * (1.0 - (bandPct / 100.0))
            val askCeil = safePrice * (1.0 + (bandPct / 100.0))

            val bidNotional = snapshot.bids.filter { it.price >= bidFloor }.sumOf { it.notional }
            val askNotional = snapshot.asks.filter { it.price <= askCeil }.sumOf { it.notional }
            val total = (bidNotional + askNotional).coerceAtLeast(1.0)
            val imbalance = (bidNotional - askNotional) / total // -1.0 to +1.0

            DepthBandMetrics(
                bandPercent = bandPct,
                bidNotional = bidNotional,
                askNotional = askNotional,
                imbalanceRatio = imbalance
            )
        }

        // 4. Wall Detection: levels >= max(5 * median level, 95th percentile)
        val allNotionals = (snapshot.bids.map { it.notional } + snapshot.asks.map { it.notional }).sorted()
        val medianNotional = if (allNotionals.isNotEmpty()) allNotionals[allNotionals.size / 2] else 10000.0
        val p95Notional = if (allNotionals.isNotEmpty()) allNotionals[(allNotionals.size * 0.95).toInt().coerceAtMost(allNotionals.size - 1)] else (medianNotional * 4)
        val wallThreshold = if (allNotionals.size >= 20) {
            max(5.0 * medianNotional, p95Notional).coerceAtLeast(25000.0)
        } else {
            (allNotionals.average() * 1.5).coerceAtLeast(20000.0)
        }

        val supportWalls = snapshot.bids
            .filter { it.notional >= wallThreshold }
            .map { level ->
                val distPct = ((safePrice - level.price) / safePrice) * 100.0
                OrderBookWall(
                    price = level.price,
                    notional = level.notional,
                    distancePercent = distPct,
                    isBid = true,
                    spoofScore = if (distPct < 0.15 && level.notional > 5 * wallThreshold) 0.65 else 0.15,
                    isPersistent = distPct >= 0.10
                )
            }
            .sortedBy { abs(safePrice - it.price) }
            .take(5)

        val resistanceWalls = snapshot.asks
            .filter { it.notional >= wallThreshold }
            .map { level ->
                val distPct = ((level.price - safePrice) / safePrice) * 100.0
                OrderBookWall(
                    price = level.price,
                    notional = level.notional,
                    distancePercent = distPct,
                    isBid = false,
                    spoofScore = if (distPct < 0.15 && level.notional > 5 * wallThreshold) 0.65 else 0.15,
                    isPersistent = distPct >= 0.10
                )
            }
            .sortedBy { abs(it.price - safePrice) }
            .take(5)

        // 5. Trade Flow, CVD & Aggressor Ratio
        val now = System.currentTimeMillis()
        val trades1m = recentTrades.filter { it.timestamp >= now - 60_000 }
        val trades5m = recentTrades.filter { it.timestamp >= now - 300_000 }
        val trades15m = recentTrades.filter { it.timestamp >= now - 900_000 }

        fun calcCvd(trades: List<AggTradePrint>): Double {
            return trades.sumOf { if (!it.isBuyerMaker) it.notional else -it.notional }
        }

        val cvd1m = calcCvd(trades1m)
        val cvd5m = calcCvd(trades5m)
        val cvd15m = calcCvd(trades15m)

        val total5mVol = trades5m.sumOf { it.notional }.coerceAtLeast(1.0)
        val aggressiveBuyVol5m = trades5m.filter { !it.isBuyerMaker }.sumOf { it.notional }
        val aggressorBuyRatio = aggressiveBuyVol5m / total5mVol

        // 6. Absorption & Exhaustion Detection
        // Heavy aggressive selling near support wall but price does not drop -> Bullish Absorption
        // Heavy aggressive buying near resistance wall but price does not rise -> Bearish Absorption
        var absorptionDetected = false
        var absorptionDetails = "No institutional absorption detected."
        val nearestSupport = supportWalls.firstOrNull()
        val nearestResistance = resistanceWalls.firstOrNull()

        if (nearestSupport != null && nearestSupport.distancePercent < 0.35 && cvd5m < -50000.0 && safePrice >= nearestSupport.price) {
            absorptionDetected = true
            absorptionDetails = "Bullish Absorption: Aggressive market sells absorbed at $${nearestSupport.price} bid wall without breakdown."
        } else if (nearestResistance != null && nearestResistance.distancePercent < 0.35 && cvd5m > 50000.0 && safePrice <= nearestResistance.price) {
            absorptionDetected = true
            absorptionDetails = "Bearish Absorption: Aggressive market buys absorbed at $${nearestResistance.price} ask wall without breakout."
        }

        val exhaustionDetected = abs(cvd1m) < abs(cvd5m) * 0.10 && spreadBps > 5.0

        // 7. Derivatives OI Quadrant
        val oiQuadrant = when {
            openInterestChange24h > 0 && priceChange24h > 0 -> "Long Buildup (Aggressive New Longs)"
            openInterestChange24h > 0 && priceChange24h < 0 -> "Short Buildup (Aggressive New Shorts)"
            openInterestChange24h < 0 && priceChange24h < 0 -> "Long Liquidation (Forced Stop Outs)"
            openInterestChange24h < 0 && priceChange24h > 0 -> "Short Covering (Squeeze Rally)"
            else -> "Neutral Rebalancing"
        }

        // 8. Book Walking Slippage Estimation
        var remainingNotional = positionSizeUsdt
        var totalCost = 0.0
        for (ask in snapshot.asks) {
            val fillNotional = min(remainingNotional, ask.notional)
            totalCost += fillNotional * (ask.price / safePrice)
            remainingNotional -= fillNotional
            if (remainingNotional <= 0.0) break
        }
        val avgFillPrice = if (positionSizeUsdt > 0.0) (totalCost / positionSizeUsdt) * safePrice else safePrice
        val expectedSlippageBps = max(0.0, ((avgFillPrice - safePrice) / safePrice) * 10000.0)

        // 9. DO-NOT-TRADE Flags
        val doNotTrade = mutableListOf<String>()
        if (spreadBps > 15.0) doNotTrade.add("Spread too wide: ${String.format(java.util.Locale.US, "%.1f", spreadBps)} bps (Max 15 bps)")
        val totalNearDepth = depthBands.firstOrNull { it.bandPercent == 0.5 }?.let { it.bidNotional + it.askNotional } ?: 0.0
        if (totalNearDepth < 50000.0 && allNotionals.isNotEmpty()) doNotTrade.add("Thin order book: <$50k within +/-0.5%")
        if (supportWalls.any { it.spoofScore > 0.60 } || resistanceWalls.any { it.spoofScore > 0.60 }) {
            doNotTrade.add("High spoofing detected near current price")
        }

        // 10. Order Flow Bias (-100 to +100) & Stance
        val band05 = depthBands.firstOrNull { it.bandPercent == 0.5 }?.imbalanceRatio ?: 0.0
        val band10 = depthBands.firstOrNull { it.bandPercent == 1.0 }?.imbalanceRatio ?: 0.0
        val cvdSign = if (cvd5m > 0) 1.0 else if (cvd5m < 0) -1.0 else 0.0
        val aggressorBias = (aggressorBuyRatio - 0.50) * 2.0 // -1.0 to +1.0

        var rawBias = (band05 * 40.0) + (band10 * 20.0) + (aggressorBias * 25.0) + (cvdSign * 15.0)
        if (absorptionDetected) {
            rawBias = if (cvd5m < 0) rawBias + 25.0 else rawBias - 25.0
        }
        val biasScore = rawBias.coerceIn(-100.0, 100.0)

        val stance = when {
            biasScore >= 20.0 -> AgentStance.BULLISH
            biasScore <= -20.0 -> AgentStance.BEARISH
            else -> AgentStance.NEUTRAL
        }

        val confidence = min(100, max(50, (abs(biasScore) * 0.6 + (100.0 - min(100.0, spreadBps * 3.0)) * 0.4).toInt()))

        val exactTriggerCondition = if (stance == AgentStance.BULLISH) {
            val wall = supportWalls.firstOrNull()?.price?.let { "Bid wall @ $${String.format(java.util.Locale.US, "%.2f", it)} holds" } ?: "Order book delta positive"
            "$wall AND CVD stays > 0 with aggressive buyers >= 52%"
        } else {
            val wall = resistanceWalls.firstOrNull()?.price?.let { "Ask wall @ $${String.format(java.util.Locale.US, "%.2f", it)} defends" } ?: "Order book delta negative"
            "$wall AND CVD stays < 0 with aggressive sellers >= 52%"
        }

        return OrderFlowReport(
            symbol = symbol,
            biasScore = biasScore,
            confidenceScore = confidence,
            stance = stance,
            spreadTicks = spreadTicks,
            spreadBps = spreadBps,
            microPrice = microPrice,
            vwapMid = vwapMid,
            depthBands = depthBands,
            supportWalls = supportWalls,
            resistanceWalls = resistanceWalls,
            cvdDelta1m = cvd1m,
            cvdDelta5m = cvd5m,
            cvdDelta15m = cvd15m,
            aggressorBuyRatio = aggressorBuyRatio,
            absorptionDetected = absorptionDetected,
            absorptionDetails = absorptionDetails,
            exhaustionDetected = exhaustionDetected,
            oiQuadrantState = oiQuadrant,
            expectedSlippageBps = expectedSlippageBps,
            doNotTradeFlags = doNotTrade,
            exactTriggerCondition = exactTriggerCondition
        )
    }
}
