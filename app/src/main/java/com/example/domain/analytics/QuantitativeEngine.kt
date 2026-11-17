package com.example.domain.analytics

import com.example.domain.model.BollingerBands
import com.example.domain.model.CandlePoint
import com.example.domain.model.MacdData
import com.example.domain.model.TechnicalIndicators
import com.example.domain.util.PriceFormatter
import kotlin.math.abs
import kotlin.math.sqrt

object QuantitativeEngine {

    fun computeIndicators(candles: List<CandlePoint>, includeSmc: Boolean = true): TechnicalIndicators {
        if (candles.isEmpty()) {
            return TechnicalIndicators(
                rsi14 = 50.0,
                sma20 = 0.0,
                sma50 = 0.0,
                ema12 = 0.0,
                ema26 = 0.0,
                macd = MacdData(0.0, 0.0, 0.0),
                bollingerBands = BollingerBands(0.0, 0.0, 0.0),
                supportLevel = 0.0,
                resistanceLevel = 0.0,
                averageTrueRange = 0.0,
                volumeRatio = 1.0
            )
        }

        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val volumes = candles.map { it.volume }

        val rsi14 = calculateRSI(closes, 14)
        val sma20 = calculateSMA(closes, 20)
        val sma50 = calculateSMA(closes, minOf(50, closes.size))
        val ema12 = calculateEMA(closes, 12)
        val ema26 = calculateEMA(closes, 26)

        val macdVal = ema12 - ema26
        val signalVal = macdVal * 0.85 // Approximate smoothed signal line
        val histogram = macdVal - signalVal
        val macd = MacdData(macdVal, signalVal, histogram)

        val bollinger = calculateBollingerBands(closes, 20, 2.0)
        val support = lows.takeLast(minOf(20, lows.size)).minOrNull() ?: closes.last() * 0.95
        val resistance = highs.takeLast(minOf(20, highs.size)).maxOrNull() ?: closes.last() * 1.05

        val atr = calculateATR(candles, 14)
        val atrPct = calculateAtrPercentile(candles, 14, 50)
        val nowTs = candles.last().timestamp
        val bars24h = candles.filter { nowTs - it.timestamp <= 86_400_000L }.ifEmpty { candles.takeLast(minOf(48, candles.size)) }
        val range24hHigh = bars24h.maxOfOrNull { it.high } ?: highs.last()
        val range24hLow = bars24h.minOfOrNull { it.low } ?: lows.last()

        val ema50 = calculateEMA(closes, minOf(50, closes.size))
        val ema200 = if (closes.size >= 250) calculateEMA(closes, 200) else 0.0
        val (plusDi, minusDi, adx) = calculateADX(candles, 14)
        val er = calculateEfficiencyRatio(closes, 14)
        val bwPercentile = calculateBandwidthPercentile(closes, 20, 50)
        val vwap = calculateSessionVWAP(candles)
        val volProfile = computeVolumeProfile(candles)

        val recentVols = volumes.takeLast(minOf(20, volumes.size))
        val avgVol = if (recentVols.isNotEmpty()) recentVols.average() else 1.0
        val volRatio = if (avgVol > 0) volumes.last() / avgVol else 1.0

        val smcStructure = if (includeSmc) computeSmartMoneyStructure(candles, atr) else null

        return TechnicalIndicators(
            rsi14 = rsi14,
            sma20 = sma20,
            sma50 = sma50,
            ema12 = ema12,
            ema26 = ema26,
            macd = macd,
            bollingerBands = bollinger,
            supportLevel = support,
            resistanceLevel = resistance,
            averageTrueRange = atr,
            volumeRatio = volRatio,
            smc = smcStructure,
            ema50 = ema50,
            ema200 = ema200,
            adx = adx,
            plusDi = plusDi,
            minusDi = minusDi,
            bandwidthPercentile = bwPercentile,
            efficiencyRatio = er,
            volumeProfile = volProfile,
            rvol = volRatio,
            sessionVwap = vwap,
            atrPercentile = atrPct,
            range24hHigh = range24hHigh,
            range24hLow = range24hLow
        )
    }

    fun computeSmartMoneyStructure(candles: List<CandlePoint>, atrParam: Double? = null): com.example.domain.model.SmcMarketStructure {
        if (candles.size < 5) {
            return com.example.domain.model.SmcMarketStructure(
                trend = "Neutral Compression",
                lastBOS = null,
                lastCHoCH = null,
                activeFVGs = emptyList(),
                activeOrderBlocks = emptyList(),
                liquidityPools = emptyList(),
                recentLiquiditySweep = null,
                institutionalImbalanceSummary = "Insufficient candle history for SMC decomposition."
            )
        }

        val effectiveAtr = atrParam ?: calculateATR(candles, 14)
        val minFvgSize = (effectiveAtr * 0.30).coerceAtLeast(0.000001)

        val n = candles.size
        val workStart = (n - 300).coerceAtLeast(0)
        val workCandles = if (workStart > 0) candles.subList(workStart, n) else candles
        val wSize = workCandles.size

        // Precompute suffix min low and max high in O(N) for O(1) mitigation checks
        val suffixMinLow = DoubleArray(wSize)
        val suffixMaxHigh = DoubleArray(wSize)
        var runMin = Double.POSITIVE_INFINITY
        var runMax = Double.NEGATIVE_INFINITY
        for (k in wSize - 1 downTo 0) {
            val wc = workCandles[k]
            if (wc.low < runMin) runMin = wc.low
            if (wc.high > runMax) runMax = wc.high
            suffixMinLow[k] = runMin
            suffixMaxHigh[k] = runMax
        }

        val currentPrice = workCandles.last().close
        val fvgs = mutableListOf<com.example.domain.model.FairValueGap>()
        val orderBlocks = mutableListOf<com.example.domain.model.OrderBlock>()
        val liquidityPools = mutableListOf<com.example.domain.model.LiquidityPool>()
        var recentSweep: com.example.domain.model.LiquidityPool? = null

        val tick = PriceFormatter.inferTickSize(currentPrice)

        // 1. Detect 3-Candle Fair Value Gaps (FVG) >= 0.3 * ATR
        for (i in 2 until wSize) {
            val c1 = workCandles[i - 2]
            val c2 = workCandles[i - 1]
            val c3 = workCandles[i]

            // Bullish FVG (BISI): c1.high < c3.low
            if (c3.low > c1.high && c2.close > c2.open && c1.high > 0.0 && c3.low > 0.0) {
                val gapBottom = c1.high
                val gapTop = c3.low
                val gapSize = gapTop - gapBottom
                if (gapSize >= minFvgSize) {
                    val isMitigated = i + 1 < wSize && suffixMinLow[i + 1] <= gapBottom
                    if (!isMitigated) {
                        fvgs.add(
                            com.example.domain.model.FairValueGap(
                                type = com.example.domain.model.FvgType.BULLISH_IMBALANCE,
                                top = gapTop,
                                bottom = gapBottom,
                                midPrice = (gapTop + gapBottom) / 2.0,
                                isMitigated = false,
                                candleIndex = workStart + i - 1,
                                description = "Bullish Imbalance ${PriceFormatter.formatZoneRange(gapBottom, gapTop, tick)}"
                            )
                        )
                    }
                }
            }

            // Bearish FVG (SIBI): c1.low > c3.high
            if (c3.high < c1.low && c2.close < c2.open && c3.high > 0.0 && c1.low > 0.0) {
                val gapTop = c1.low
                val gapBottom = c3.high
                val gapSize = gapTop - gapBottom
                if (gapSize >= minFvgSize) {
                    val isMitigated = i + 1 < wSize && suffixMaxHigh[i + 1] >= gapTop
                    if (!isMitigated) {
                        fvgs.add(
                            com.example.domain.model.FairValueGap(
                                type = com.example.domain.model.FvgType.BEARISH_IMBALANCE,
                                top = gapTop,
                                bottom = gapBottom,
                                midPrice = (gapTop + gapBottom) / 2.0,
                                isMitigated = false,
                                candleIndex = workStart + i - 1,
                                description = "Bearish Imbalance ${PriceFormatter.formatZoneRange(gapBottom, gapTop, tick)}"
                            )
                        )
                    }
                }
            }
        }

        // 2. Detect Institutional Order Blocks (OB)
        val avgVol = workCandles.sumOf { it.volume } / wSize.coerceAtLeast(1)
        for (i in 1 until wSize - 2) {
            val candle = workCandles[i]
            if (candle.low <= 0.0 || candle.high <= 0.0) continue
            val obBottom = minOf(candle.low, candle.high)
            val obTop = maxOf(candle.low, candle.high)
            val next1 = workCandles[i + 1]
            val next2 = workCandles[i + 2]

            // Bullish Order Block (OB+): Down-close candle before impulsive upside displacement
            if (candle.close < candle.open && next1.close > next1.open && (next1.volume > avgVol * 1.1 || next2.close > candle.high)) {
                val isMitigated = suffixMinLow[i + 1] < obBottom
                if (!isMitigated) {
                    orderBlocks.add(
                        com.example.domain.model.OrderBlock(
                            type = com.example.domain.model.OrderBlockType.BULLISH_DEMAND_BLOCK,
                            top = obTop,
                            bottom = obBottom,
                            volume = candle.volume,
                            isMitigated = false,
                            candleIndex = workStart + i,
                            description = "Institutional Demand Block ${PriceFormatter.formatZoneRange(obBottom, obTop, tick)}"
                        )
                    )
                }
            }

            // Bearish Order Block (OB-): Up-close candle before impulsive downside displacement
            if (candle.close > candle.open && next1.close < next1.open && (next1.volume > avgVol * 1.1 || next2.close < candle.low)) {
                val isMitigated = suffixMaxHigh[i + 1] > obTop
                if (!isMitigated) {
                    orderBlocks.add(
                        com.example.domain.model.OrderBlock(
                            type = com.example.domain.model.OrderBlockType.BEARISH_SUPPLY_BLOCK,
                            top = obTop,
                            bottom = obBottom,
                            volume = candle.volume,
                            isMitigated = false,
                            candleIndex = workStart + i,
                            description = "Institutional Supply Block ${PriceFormatter.formatZoneRange(obBottom, obTop, tick)}"
                        )
                    )
                }
            }
        }

        // 3. Detect Swing Points & Liquidity Pools (BSL / SSL) + Sweeps
        for (i in 2 until wSize - 2) {
            val c = workCandles[i]
            val isSwingHigh = c.high > workCandles[i - 1].high && c.high > workCandles[i - 2].high &&
                              c.high > workCandles[i + 1].high && c.high > workCandles[i + 2].high

            val isSwingLow = c.low < workCandles[i - 1].low && c.low < workCandles[i - 2].low &&
                             c.low < workCandles[i + 1].low && c.low < workCandles[i + 2].low

            if (isSwingHigh && c.high > currentPrice) {
                var sweep = false
                if (suffixMaxHigh[i + 1] > c.high) {
                    for (j in i + 1 until wSize) {
                        val later = workCandles[j]
                        if (later.high > c.high && later.close < c.high) {
                            sweep = true
                            break
                        }
                    }
                }
                val pool = com.example.domain.model.LiquidityPool(
                    type = com.example.domain.model.LiquidityType.BUY_SIDE_LIQUIDITY_BSL,
                    priceLevel = c.high,
                    sweepDetected = sweep,
                    description = "Buy-Side Liquidity Pool at ${PriceFormatter.formatCurrency(c.high, tick)}${if (sweep) " [SWEEP GRABBED]" else ""}"
                )
                liquidityPools.add(pool)
                if (sweep) recentSweep = pool
            }

            if (isSwingLow && c.low > 0.0 && c.low < currentPrice) {
                var sweep = false
                if (suffixMinLow[i + 1] < c.low) {
                    for (j in i + 1 until wSize) {
                        val later = workCandles[j]
                        if (later.low < c.low && later.close > c.low) {
                            sweep = true
                            break
                        }
                    }
                }
                val pool = com.example.domain.model.LiquidityPool(
                    type = com.example.domain.model.LiquidityType.SELL_SIDE_LIQUIDITY_SSL,
                    priceLevel = c.low,
                    sweepDetected = sweep,
                    description = "Sell-Side Liquidity Pool at ${PriceFormatter.formatCurrency(c.low, tick)}${if (sweep) " [SWEEP GRABBED]" else ""}"
                )
                liquidityPools.add(pool)
                if (sweep) recentSweep = pool
            }
        }

        // Keep BSL clusters strictly > currentPrice (nearest first) and SSL clusters strictly < currentPrice (nearest first)
        val sortedBsl = liquidityPools
            .filter { it.type == com.example.domain.model.LiquidityType.BUY_SIDE_LIQUIDITY_BSL && it.priceLevel > currentPrice }
            .sortedBy { it.priceLevel - currentPrice }
        val sortedSsl = liquidityPools
            .filter { it.type == com.example.domain.model.LiquidityType.SELL_SIDE_LIQUIDITY_SSL && it.priceLevel < currentPrice }
            .sortedBy { currentPrice - it.priceLevel }
        val orderedPools = (sortedBsl.take(3) + sortedSsl.take(3)).ifEmpty { liquidityPools }

        // 4. Market Structure Classification (Built-in SMC Smart Money Framework)
        val recentHighs = candles.takeLast(15).map { it.high }
        val recentLows = candles.takeLast(15).map { it.low }
        val maxHigh = recentHighs.maxOrNull() ?: currentPrice
        val minLow = recentLows.minOrNull() ?: currentPrice

        val lastBos = if (currentPrice >= maxHigh * 0.99) maxHigh else null
        val lastChoch = if (currentPrice <= minLow * 1.01) minLow else null

        // Premium vs Discount Zone (Equilibrium at 50% of the recent swing range)
        val swingRange = maxHigh - minLow
        val pdPercent = if (swingRange > 0) ((currentPrice - minLow) / swingRange) * 100.0 else 50.0
        val pdZone = when {
            pdPercent < 45.0 -> "Discount Zone (<50% - Institutional Accumulation)"
            pdPercent > 55.0 -> "Premium Zone (>50% - Institutional Distribution)"
            else -> "Equilibrium (50% Fair Value Zone)"
        }

        // Built-in Smart Money Concepts Signals:
        // - Internal Structure: Minor swing breaks
        // - Swing Structure: Major swing BOS / CHoCH
        val hasBullSweep = recentSweep?.type == com.example.domain.model.LiquidityType.SELL_SIDE_LIQUIDITY_SSL
        val hasBearSweep = recentSweep?.type == com.example.domain.model.LiquidityType.BUY_SIDE_LIQUIDITY_BSL

        val luxInternal = when {
            hasBullSweep -> "Internal Bullish CHoCH (Change of Character)"
            hasBearSweep -> "Internal Bearish CHoCH (Change of Character)"
            currentPrice > candles.takeLast(5).first().close -> "Internal Bullish BOS (Break of Structure)"
            else -> "Internal Bearish BOS (Break of Structure)"
        }

        val luxSwing = when {
            lastBos != null -> "Swing BOS Bullish Expansion"
            lastChoch != null -> "Swing CHoCH Bearish Reversal"
            currentPrice >= (maxHigh + minLow) / 2.0 -> "Bullish Swing Expansion"
            else -> "Bearish Swing Pullback"
        }

        val luxSignal = when {
            hasBullSweep || (lastBos != null && pdPercent < 60.0) -> "BULLISH ACCUMULATION"
            hasBearSweep || (lastChoch != null && pdPercent > 40.0) -> "BEARISH DISTRIBUTION"
            pdPercent < 45.0 -> "DISCOUNT ACCUMULATION"
            pdPercent > 55.0 -> "PREMIUM DISTRIBUTION"
            else -> "EQUILIBRIUM CONSOLIDATION"
        }

        // Built-in Smart Money Structure:
        // - Strong High/Low vs Weak High/Low:
        // A "Strong Low" causes a break of a swing high (BOS). A "Weak High" fails to break structure and becomes a liquidity magnet.
        val gainzStrongHighLow = when {
            lastBos != null || hasBullSweep -> "Strong Low Protected @ ${PriceFormatter.formatCurrency(minLow, tick)} (Weak High Targeted @ ${PriceFormatter.formatCurrency(maxHigh, tick)})"
            lastChoch != null || hasBearSweep -> "Strong High Protected @ ${PriceFormatter.formatCurrency(maxHigh, tick)} (Weak Low Targeted @ ${PriceFormatter.formatCurrency(minLow, tick)})"
            currentPrice >= (maxHigh + minLow) / 2.0 -> "Strong Low Holding (Targeting Equal Highs)"
            else -> "Strong High Guarded (Targeting Equal Lows)"
        }

        val gainzTrend = when {
            lastBos != null -> "Built-in SMC: Bullish Order Flow Continuation"
            hasBullSweep -> "Built-in SMC: Institutional Liquidity Absorption"
            hasBearSweep -> "Built-in SMC: Liquidity Sweep Exhaustion"
            lastChoch != null -> "Built-in SMC: Bearish Market Structure Shift (MSS)"
            currentPrice > candles.first().close -> "Built-in SMC: Bullish Trending Structure"
            else -> "Built-in SMC: Corrective Structure Retracement"
        }

        val trend = when {
            lastBos != null -> "Bullish Structure Expansion (BOS)"
            hasBullSweep -> "Bullish Reversal Post-SSL Liquidity Sweep"
            hasBearSweep -> "Bearish Rejection Post-BSL Liquidity Sweep"
            currentPrice > candles.first().close -> "Bullish Order Flow"
            else -> "Corrective Institutional Retracement"
        }

        val unmitigatedFvgs = fvgs.filter { !it.isMitigated && it.bottom > 0.0 && it.top > 0.0 }.reversed()
        val unmitigatedObs = orderBlocks.filter { !it.isMitigated && it.bottom > 0.0 && it.top > 0.0 }.reversed()

        val summary = buildString {
            append("Built-in SMC: $trend ($pdZone). ")
            append("Signal: $luxSignal. ")
            if (unmitigatedFvgs.isNotEmpty()) {
                val nearestFvg = unmitigatedFvgs.last()
                append("Active FVG ${nearestFvg.type.label} at ${PriceFormatter.formatCurrency(nearestFvg.midPrice, tick)}. ")
            }
            if (unmitigatedObs.isNotEmpty()) {
                val nearestOb = unmitigatedObs.last()
                append("Primary ${nearestOb.type.label} zone ${PriceFormatter.formatZoneRange(nearestOb.bottom, nearestOb.top, tick)}. ")
            }
            if (recentSweep != null) {
                append("Institutional Stop Sweep at ${PriceFormatter.formatCurrency(recentSweep.priceLevel, tick)} (${recentSweep.type.label}). ")
            }
            append("Structure: $gainzStrongHighLow.")
        }

        return com.example.domain.model.SmcMarketStructure(
            trend = trend,
            lastBOS = lastBos,
            lastCHoCH = lastChoch,
            activeFVGs = unmitigatedFvgs.takeLast(3),
            activeOrderBlocks = unmitigatedObs.takeLast(3),
            liquidityPools = orderedPools,
            recentLiquiditySweep = recentSweep,
            institutionalImbalanceSummary = summary,
            builtInSmcSignal = luxSignal,
            smcInternalStructure = luxInternal,
            smcSwingStructure = luxSwing,
            smcStructureTrend = gainzTrend,
            smcStrongHighLow = gainzStrongHighLow,
            premiumDiscountZone = pdZone,
            premiumDiscountPercent = pdPercent
        )
    }

    /**
     * Clips any zone wider than 0.75 * ATR to its displacement leg edge so wide blocks remain valid
     * tight zones rather than collapsing to a single price point or swallowing stop distance.
     */
    fun clipZoneToDisplacementEdge(
        rawMin: Double,
        rawMax: Double,
        isLongDemand: Boolean,
        atr: Double
    ): Pair<Double, Double> {
        val lo = minOf(rawMin, rawMax)
        val hi = maxOf(rawMin, rawMax)
        val maxWidth = (atr * 0.75).coerceAtLeast(1e-8)
        if (hi - lo <= maxWidth) return lo to hi
        return if (isLongDemand) {
            // Keep upper displacement edge of demand block
            (hi - maxWidth).coerceAtLeast(lo) to hi
        } else {
            // Keep lower displacement edge of supply block
            lo to (lo + maxWidth).coerceAtMost(hi)
        }
    }

    private fun calculateRSI(prices: List<Double>, period: Int): Double {
        if (prices.size <= period) return 50.0

        var gains = 0.0
        var losses = 0.0

        for (i in 1..period) {
            val change = prices[i] - prices[i - 1]
            if (change > 0) gains += change else losses += abs(change)
        }

        var avgGain = gains / period
        var avgLoss = losses / period

        for (i in (period + 1) until prices.size) {
            val change = prices[i] - prices[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) abs(change) else 0.0

            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun calculateSMA(prices: List<Double>, period: Int): Double {
        val window = prices.takeLast(minOf(period, prices.size))
        return if (window.isNotEmpty()) window.average() else 0.0
    }

    fun calculateEMA(prices: List<Double>, period: Int): Double {
        if (prices.isEmpty()) return 0.0
        val k = 2.0 / (period + 1)
        var ema = prices.first()
        for (i in 1 until prices.size) {
            ema = (prices[i] * k) + (ema * (1.0 - k))
        }
        return ema
    }

    private fun calculateBollingerBands(prices: List<Double>, period: Int, numStdDev: Double): BollingerBands {
        val window = prices.takeLast(minOf(period, prices.size))
        val mean = if (window.isNotEmpty()) window.average() else 0.0
        val variance = if (window.isNotEmpty()) {
            window.sumOf { (it - mean) * (it - mean) } / window.size
        } else 0.0
        val stdDev = sqrt(variance)

        return BollingerBands(
            upper = mean + (numStdDev * stdDev),
            middle = mean,
            lower = mean - (numStdDev * stdDev)
        )
    }

    fun calculateSessionVWAP(candles: List<CandlePoint>): Double {
        if (candles.isEmpty()) return 0.0
        val sumVol = candles.sumOf { it.volume }
        if (sumVol <= 0.0) return candles.last().close
        val sumPv = candles.sumOf { ((it.high + it.low + it.close) / 3.0) * it.volume }
        return sumPv / sumVol
    }

    fun calculateEfficiencyRatio(closes: List<Double>, period: Int = 14): Double {
        if (closes.size <= period) return 0.5
        val window = closes.takeLast(period + 1)
        val netChange = abs(window.last() - window.first())
        var totalSum = 0.0
        for (i in 1 until window.size) {
            totalSum += abs(window[i] - window[i - 1])
        }
        return if (totalSum > 0.0) (netChange / totalSum).coerceIn(0.0, 1.0) else 0.5
    }

    fun calculateBandwidthPercentile(closes: List<Double>, period: Int = 20, lookback: Int = 50): Double {
        if (closes.size < period) return 50.0
        val bandwidths = mutableListOf<Double>()
        val startIdx = (closes.size - lookback).coerceAtLeast(period)
        for (end in startIdx..closes.size) {
            val slice = closes.subList(end - period, end)
            val mean = slice.average()
            if (mean <= 0.0) continue
            val variance = slice.sumOf { (it - mean) * (it - mean) } / slice.size
            val std = sqrt(variance)
            val bw = (4.0 * std) / mean
            bandwidths.add(bw)
        }
        if (bandwidths.isEmpty()) return 50.0
        val currentBw = bandwidths.last()
        val eps = currentBw * 1e-6
        val countBelow = bandwidths.count { it < currentBw - eps }
        return ((countBelow.toDouble() / bandwidths.size) * 100.0).coerceIn(0.0, 100.0)
    }

    fun calculateADX(candles: List<CandlePoint>, period: Int = 14): Triple<Double, Double, Double> {
        if (candles.size <= period + 1) return Triple(25.0, 25.0, 25.0)

        val trList = mutableListOf<Double>()
        val plusDmList = mutableListOf<Double>()
        val minusDmList = mutableListOf<Double>()

        for (i in 1 until candles.size) {
            val curr = candles[i]
            val prev = candles[i - 1]

            val tr = maxOf(
                curr.high - curr.low,
                abs(curr.high - prev.close),
                abs(curr.low - prev.close)
            )
            trList.add(tr)

            val upMove = curr.high - prev.high
            val downMove = prev.low - curr.low

            val plusDm = if (upMove > downMove && upMove > 0) upMove else 0.0
            val minusDm = if (downMove > upMove && downMove > 0) downMove else 0.0

            plusDmList.add(plusDm)
            minusDmList.add(minusDm)
        }

        if (trList.size < period) return Triple(25.0, 25.0, 25.0)

        // Wilder smoothing initial
        var smoothedTr = trList.take(period).sum()
        var smoothedPlusDm = plusDmList.take(period).sum()
        var smoothedMinusDm = minusDmList.take(period).sum()

        val dxList = mutableListOf<Double>()
        for (i in period until trList.size) {
            smoothedTr = smoothedTr - (smoothedTr / period) + trList[i]
            smoothedPlusDm = smoothedPlusDm - (smoothedPlusDm / period) + plusDmList[i]
            smoothedMinusDm = smoothedMinusDm - (smoothedMinusDm / period) + minusDmList[i]

            val plusDi = if (smoothedTr > 0) (smoothedPlusDm / smoothedTr) * 100.0 else 0.0
            val minusDi = if (smoothedTr > 0) (smoothedMinusDm / smoothedTr) * 100.0 else 0.0
            val diSum = plusDi + minusDi
            val dx = if (diSum > 0) (abs(plusDi - minusDi) / diSum) * 100.0 else 0.0
            dxList.add(dx)
        }

        val lastPlusDi = if (smoothedTr > 0) (smoothedPlusDm / smoothedTr) * 100.0 else 25.0
        val lastMinusDi = if (smoothedTr > 0) (smoothedMinusDm / smoothedTr) * 100.0 else 25.0
        val adx = if (dxList.size >= period) {
            dxList.takeLast(period).average()
        } else if (dxList.isNotEmpty()) {
            dxList.average()
        } else 25.0

        return Triple(lastPlusDi, lastMinusDi, adx)
    }

    fun computeVolumeProfile(candles: List<CandlePoint>, numBuckets: Int = 24): com.example.domain.model.VolumeProfileData {
        if (candles.isEmpty()) return com.example.domain.model.VolumeProfileData(0.0, 0.0, 0.0)
        val high = candles.maxOf { it.high }
        val low = candles.minOf { it.low }
        val range = high - low
        if (range <= 0.0) return com.example.domain.model.VolumeProfileData(candles.last().close, candles.last().close, candles.last().close)

        val bucketSize = range / numBuckets
        val buckets = DoubleArray(numBuckets)

        candles.forEach { c ->
            val mid = (c.high + c.low + c.close) / 3.0
            val idx = ((mid - low) / bucketSize).toInt().coerceIn(0, numBuckets - 1)
            buckets[idx] += c.volume
        }

        // POC: Bucket with maximum volume
        var maxVol = -1.0
        var pocIdx = 0
        for (i in buckets.indices) {
            if (buckets[i] > maxVol) {
                maxVol = buckets[i]
                pocIdx = i
            }
        }
        val pocPrice = low + (pocIdx + 0.5) * bucketSize

        // Value Area: 70% of total volume expanding outward from POC
        val totalVol = buckets.sum()
        val targetVaVol = totalVol * 0.70
        var currentVaVol = buckets[pocIdx]
        var left = pocIdx
        var right = pocIdx

        while (currentVaVol < targetVaVol && (left > 0 || right < numBuckets - 1)) {
            val nextLeftVol = if (left > 0) buckets[left - 1] else -1.0
            val nextRightVol = if (right < numBuckets - 1) buckets[right + 1] else -1.0

            if (nextLeftVol >= nextRightVol && nextLeftVol >= 0) {
                left--
                currentVaVol += nextLeftVol
            } else if (nextRightVol >= 0) {
                right++
                currentVaVol += nextRightVol
            } else {
                break
            }
        }

        val valLevel = low + left * bucketSize
        val vah = low + (right + 1) * bucketSize

        // HVN (High Volume Nodes) and LVN (Low Volume Nodes)
        val avgBucketVol = totalVol / numBuckets
        val hvn = mutableListOf<Double>()
        val lvn = mutableListOf<Double>()
        for (i in buckets.indices) {
            val price = low + (i + 0.5) * bucketSize
            if (buckets[i] > avgBucketVol * 1.5) hvn.add(price)
            if (buckets[i] < avgBucketVol * 0.4) lvn.add(price)
        }

        return com.example.domain.model.VolumeProfileData(
            poc = pocPrice,
            vah = vah,
            valLevel = valLevel,
            hvnList = hvn,
            lvnList = lvn
        )
    }

    fun calculateAtrPercentile(candles: List<CandlePoint>, period: Int = 14, lookback: Int = 50): Double {
        if (candles.size <= period + 2) return 50.0
        val trs = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val current = candles[i]
            val prevClose = candles[i - 1].close
            val tr = maxOf(
                current.high - current.low,
                abs(current.high - prevClose),
                abs(current.low - prevClose)
            )
            trs.add(tr)
        }
        if (trs.size < period) return 50.0
        val rollingAtrs = mutableListOf<Double>()
        val startIdx = (trs.size - lookback).coerceAtLeast(period)
        for (end in startIdx..trs.size) {
            val window = trs.subList(end - period, end)
            rollingAtrs.add(window.average())
        }
        if (rollingAtrs.isEmpty()) return 50.0
        val currentAtr = rollingAtrs.last()
        val eps = currentAtr * 1e-6
        val countBelow = rollingAtrs.count { it < currentAtr - eps }
        return ((countBelow.toDouble() / rollingAtrs.size) * 100.0).coerceIn(0.0, 100.0)
    }

    fun calculateATR(candles: List<CandlePoint>, period: Int = 14): Double {
        if (candles.size < 2) return candles.firstOrNull()?.let { it.high - it.low } ?: 0.0
        val trs = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val current = candles[i]
            val prevClose = candles[i - 1].close
            val tr = maxOf(
                current.high - current.low,
                abs(current.high - prevClose),
                abs(current.low - prevClose)
            )
            trs.add(tr)
        }
        val window = trs.takeLast(minOf(period, trs.size))
        return if (window.isNotEmpty()) window.average() else 0.0
    }
}
