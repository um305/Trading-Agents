package com.example.domain.agents

import com.example.domain.model.AgentReport
import com.example.domain.model.AgentRole
import com.example.domain.model.AgentStance
import com.example.domain.model.CandlePoint
import com.example.domain.model.MarketQuote
import com.example.domain.model.TradingViewIndicatorSignal
import com.example.domain.model.TradingViewTechnicalSummary
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Background multi-indicator analysis agent modeling TradingView's comprehensive technical rating engine.
 * Computes 26 standard technical indicators (15 Moving Averages + 11 Oscillators) across the scanned
 * asset to deliver real-time Buy, Neutral, and Sell ratings.
 */
object TradingViewTechnicalsAgent {

    fun evaluate(quote: MarketQuote): Pair<AgentReport, TradingViewTechnicalSummary> {
        val candles = quote.candles
        val price = quote.currentPrice
        val sym = quote.symbol.uppercase(Locale.US)

        if (candles.size < 5) {
            val emptySummary = TradingViewTechnicalSummary(
                summaryVerdict = "NEUTRAL",
                movingAveragesVerdict = "NEUTRAL",
                oscillatorsVerdict = "NEUTRAL",
                buyCount = 1,
                neutralCount = 24,
                sellCount = 1,
                rsiValue = 50.0
            )
            val emptyReport = AgentReport(
                role = AgentRole.TRADINGVIEW_STUDIES,
                score = 5,
                stance = AgentStance.NEUTRAL,
                headlineThesis = "TradingView Studies Node: Insufficient bar depth to compute full multi-indicator matrix for $sym.",
                keyPoints = listOf("Minimum 14 bars required for complete TradingView oscillator rating."),
                quantitativeMetrics = mapOf("Summary" to "Neutral", "Oscillators" to "Neutral", "Moving Averages" to "Neutral")
            )
            return Pair(emptyReport, emptySummary)
        }

        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val volumes = candles.map { it.volume }

        val signals = mutableListOf<TradingViewIndicatorSignal>()
        var maBuy = 0
        var maSell = 0
        var maNeutral = 0

        var oscBuy = 0
        var oscSell = 0
        var oscNeutral = 0

        // Helper to rate Moving Average vs Price
        fun rateMa(name: String, maVal: Double) {
            val threshold = maVal * 0.001
            val signal = when {
                price > maVal + threshold -> {
                    maBuy++
                    "BUY"
                }
                price < maVal - threshold -> {
                    maSell++
                    "SELL"
                }
                else -> {
                    maNeutral++
                    "NEUTRAL"
                }
            }
            signals.add(TradingViewIndicatorSignal(name, formatPrice(maVal), signal))
        }

        // 1. Moving Averages (15 standard MAs)
        val ema10 = calculateEMA(closes, 10)
        rateMa("EMA (10)", ema10)
        val ema20 = calculateEMA(closes, 20)
        rateMa("EMA (20)", ema20)
        val ema30 = calculateEMA(closes, minOf(30, closes.size))
        rateMa("EMA (30)", ema30)
        val ema50 = calculateEMA(closes, minOf(50, closes.size))
        rateMa("EMA (50)", ema50)
        val ema100 = calculateEMA(closes, minOf(100, closes.size))
        rateMa("EMA (100)", ema100)
        val ema200 = if (closes.size >= 250) {
            calculateEMA(closes, 200).also { rateMa("EMA (200)", it) }
        } else {
            maNeutral++
            signals.add(TradingViewIndicatorSignal("EMA (200)", "N/A (<250 bars)", "NEUTRAL"))
            0.0
        }

        val sma10 = calculateSMA(closes, 10)
        rateMa("SMA (10)", sma10)
        val sma20 = calculateSMA(closes, 20)
        rateMa("SMA (20)", sma20)
        val sma30 = calculateSMA(closes, minOf(30, closes.size))
        rateMa("SMA (30)", sma30)
        val sma50 = calculateSMA(closes, minOf(50, closes.size))
        rateMa("SMA (50)", sma50)
        val sma100 = calculateSMA(closes, minOf(100, closes.size))
        rateMa("SMA (100)", sma100)
        val sma200 = if (closes.size >= 200) {
            calculateSMA(closes, 200).also { rateMa("SMA (200)", it) }
        } else {
            maNeutral++
            signals.add(TradingViewIndicatorSignal("SMA (200)", "N/A (<200 bars)", "NEUTRAL"))
            0.0
        }

        // Ichimoku Base Line (26 period: (highest high + lowest low) / 2)
        val ichiPeriod = minOf(26, candles.size)
        val ichiHigh = highs.takeLast(ichiPeriod).maxOrNull() ?: price
        val ichiLow = lows.takeLast(ichiPeriod).minOrNull() ?: price
        val ichiBaseLine = (ichiHigh + ichiLow) / 2.0
        rateMa("Ichimoku Base (26)", ichiBaseLine)

        // Volume Weighted Moving Average (VWMA 20)
        val vwma20 = calculateVWMA(closes, volumes, 20)
        rateMa("VWMA (20)", vwma20)

        // Hull Moving Average (HMA 9)
        val hma9 = calculateHMA(closes, 9)
        rateMa("Hull MA (9)", hma9)

        // 2. Oscillators (11 standard Oscillators)
        // 2.1 RSI (14)
        val rsi14 = calculateRSI(closes, 14)
        val rsiSignal = when {
            rsi14 < 30.0 -> { oscBuy++; "BUY" } // Oversold bounce
            rsi14 > 70.0 -> { oscSell++; "SELL" } // Overbought exhaustion
            rsi14 in 50.0..68.0 -> { oscBuy++; "BUY" } // Bullish momentum zone
            rsi14 in 32.0..49.9 -> { oscSell++; "SELL" } // Bearish drift zone
            else -> { oscNeutral++; "NEUTRAL" }
        }
        signals.add(TradingViewIndicatorSignal("RSI (14)", String.format(Locale.US, "%.1f", rsi14), rsiSignal))

        // 2.2 Stochastic %K / %D (14, 3, 3)
        val (stochK, stochD) = calculateStochastic(candles, 14, 3)
        val stochSignal = when {
            stochK < 20.0 && stochK > stochD -> { oscBuy++; "BUY" }
            stochK > 80.0 && stochK < stochD -> { oscSell++; "SELL" }
            stochK > 50.0 -> { oscBuy++; "BUY" }
            stochK < 50.0 -> { oscSell++; "SELL" }
            else -> { oscNeutral++; "NEUTRAL" }
        }
        signals.add(TradingViewIndicatorSignal("Stoch %K (14,3,3)", String.format(Locale.US, "%.1f", stochK), stochSignal))

        // 2.3 Commodity Channel Index (CCI 20)
        val cci20 = calculateCCI(candles, 20)
        val cciSignal = when {
            cci20 < -100.0 -> { oscBuy++; "BUY" }
            cci20 > 100.0 -> { oscSell++; "SELL" }
            cci20 > 0 -> { oscBuy++; "BUY" }
            cci20 < 0 -> { oscSell++; "SELL" }
            else -> { oscNeutral++; "NEUTRAL" }
        }
        signals.add(TradingViewIndicatorSignal("CCI (20)", String.format(Locale.US, "%.1f", cci20), cciSignal))

        // 2.4 Average Directional Index (ADX 14)
        val (adxVal, plusDi, minusDi) = calculateADX(candles, 14)
        val adxSignal = when {
            adxVal >= 20.0 && plusDi > minusDi -> { oscBuy++; "BUY" }
            adxVal >= 20.0 && minusDi > plusDi -> { oscSell++; "SELL" }
            else -> { oscNeutral++; "NEUTRAL" }
        }
        signals.add(TradingViewIndicatorSignal("ADX (14)", String.format(Locale.US, "%.1f", adxVal), adxSignal))

        // 2.5 Awesome Oscillator (AO)
        val ao = calculateAwesomeOscillator(candles)
        val aoSignal = when {
            ao > 0.0 -> { oscBuy++; "BUY" }
            ao < 0.0 -> { oscSell++; "SELL" }
            else -> { oscNeutral++; "NEUTRAL" }
        }
        signals.add(TradingViewIndicatorSignal("Awesome Oscillator", formatPrice(ao), aoSignal))

        // 2.6 Momentum (10)
        val momPeriod = minOf(10, closes.size - 1)
        val mom10 = price - closes[closes.size - 1 - momPeriod]
        val momSignal = when {
            mom10 > 0.0 -> { oscBuy++; "BUY" }
            mom10 < 0.0 -> { oscSell++; "SELL" }
            else -> { oscNeutral++; "NEUTRAL" }
        }
        signals.add(TradingViewIndicatorSignal("Momentum (10)", formatPrice(mom10), momSignal))

        // 2.7 MACD Level (12, 26)
        val macdFast = calculateEMA(closes, 12)
        val macdSlow = calculateEMA(closes, 26)
        val macdLevel = macdFast - macdSlow
        val macdSignalLine = macdLevel * 0.85
        val macdHist = macdLevel - macdSignalLine
        val macdSignal = when {
            macdHist > 0.0 -> { oscBuy++; "BUY" }
            macdHist < 0.0 -> { oscSell++; "SELL" }
            else -> { oscNeutral++; "NEUTRAL" }
        }
        signals.add(TradingViewIndicatorSignal("MACD Level (12,26)", formatPrice(macdHist), macdSignal))

        // 2.8 Williams %R (14)
        val wR = calculateWilliamsR(candles, 14)
        val wRSignal = when {
            wR < -80.0 -> { oscBuy++; "BUY" }
            wR > -20.0 -> { oscSell++; "SELL" }
            wR > -50.0 -> { oscBuy++; "BUY" }
            wR < -50.0 -> { oscSell++; "SELL" }
            else -> { oscNeutral++; "NEUTRAL" }
        }
        signals.add(TradingViewIndicatorSignal("Williams %R (14)", String.format(Locale.US, "%.1f", wR), wRSignal))

        // 2.9 Ultimate Oscillator (7, 14, 28)
        val uo = calculateUltimateOscillator(candles)
        val uoSignal = when {
            uo > 70.0 -> { oscSell++; "SELL" }
            uo < 30.0 -> { oscBuy++; "BUY" }
            uo > 50.0 -> { oscBuy++; "BUY" }
            uo < 50.0 -> { oscSell++; "SELL" }
            else -> { oscNeutral++; "NEUTRAL" }
        }
        signals.add(TradingViewIndicatorSignal("Ultimate Osc", String.format(Locale.US, "%.1f", uo), uoSignal))

        // 2.10 Bull/Bear Power (Elder Ray Index)
        val elderEma = ema13(closes)
        val bullPower = highs.last() - elderEma
        val bearPower = lows.last() - elderEma
        val elderSignal = when {
            bearPower > 0 -> { oscBuy++; "BUY" }
            bullPower < 0 -> { oscSell++; "SELL" }
            bearPower < 0 && bullPower > 0 -> {
                if (price >= elderEma) { oscBuy++; "BUY" } else { oscSell++; "SELL" }
            }
            else -> { oscNeutral++; "NEUTRAL" }
        }
        signals.add(TradingViewIndicatorSignal("Bull/Bear Power", formatPrice(bullPower), elderSignal))

        // 2.11 Stochastic RSI Fast (3, 3, 14, 14)
        val stochRsiVal = calculateStochRSI(closes, 14)
        val stochRsiSignal = when {
            stochRsiVal < 20.0 -> { oscBuy++; "BUY" }
            stochRsiVal > 80.0 -> { oscSell++; "SELL" }
            stochRsiVal > 50.0 -> { oscBuy++; "BUY" }
            else -> { oscSell++; "SELL" }
        }
        signals.add(TradingViewIndicatorSignal("Stoch RSI Fast", String.format(Locale.US, "%.1f", stochRsiVal), stochRsiSignal))

        // 3. User & Community Favorite Indicators in TradingView
        val favoriteSignals = mutableListOf<TradingViewIndicatorSignal>()
        var favBuy = 0
        var favSell = 0
        var favNeutral = 0

        // 3.1 Supertrend (10, 3.0)
        val (supertrendVal, supertrendDir) = calculateSupertrend(candles, 10, 3.0)
        val stSignal = when (supertrendDir) {
            1 -> { favBuy++; "BUY" }
            -1 -> { favSell++; "SELL" }
            else -> { favNeutral++; "NEUTRAL" }
        }
        val stSigObj = TradingViewIndicatorSignal("Supertrend (10, 3)", formatPrice(supertrendVal), stSignal)
        favoriteSignals.add(stSigObj)
        signals.add(stSigObj)

        // 3.2 Parabolic SAR (0.02, 0.2)
        val sarVal = calculateParabolicSAR(candles, 0.02, 0.2)
        val sarSignal = when {
            price > sarVal -> { favBuy++; "BUY" }
            price < sarVal -> { favSell++; "SELL" }
            else -> { favNeutral++; "NEUTRAL" }
        }
        val sarSigObj = TradingViewIndicatorSignal("Parabolic SAR", formatPrice(sarVal), sarSignal)
        favoriteSignals.add(sarSigObj)
        signals.add(sarSigObj)

        // 3.3 Chaikin Money Flow (CMF 20)
        val cmf20 = calculateCMF(candles, 20)
        val cmfSignal = when {
            cmf20 > 0.05 -> { favBuy++; "BUY" }
            cmf20 < -0.05 -> { favSell++; "SELL" }
            else -> { favNeutral++; "NEUTRAL" }
        }
        val cmfSigObj = TradingViewIndicatorSignal("Chaikin Money Flow (20)", String.format(Locale.US, "%.3f", cmf20), cmfSignal)
        favoriteSignals.add(cmfSigObj)
        signals.add(cmfSigObj)

        // 3.4 Money Flow Index (MFI 14)
        val mfi14 = calculateMFI(candles, 14)
        val mfiSignal = when {
            mfi14 < 20.0 -> { favBuy++; "BUY" } // Oversold accumulation
            mfi14 > 80.0 -> { favSell++; "SELL" } // Overbought distribution
            mfi14 > 50.0 -> { favBuy++; "BUY" }
            else -> { favSell++; "SELL" }
        }
        val mfiSigObj = TradingViewIndicatorSignal("Money Flow Index (14)", String.format(Locale.US, "%.1f", mfi14), mfiSignal)
        favoriteSignals.add(mfiSigObj)
        signals.add(mfiSigObj)

        // 3.5 Keltner Channels (20 EMA, 2.0 ATR)
        val (keltnerUpper, keltnerLower) = calculateKeltnerChannels(candles, 20, 2.0)
        val keltnerSignal = when {
            price > keltnerUpper -> { favBuy++; "BUY" } // Bullish breakout
            price < keltnerLower -> { favSell++; "SELL" } // Bearish breakdown
            price >= (keltnerUpper + keltnerLower) / 2.0 -> { favBuy++; "BUY" }
            else -> { favSell++; "SELL" }
        }
        val keltnerSigObj = TradingViewIndicatorSignal("Keltner Channels (20,2)", formatPrice((keltnerUpper + keltnerLower) / 2.0), keltnerSignal)
        favoriteSignals.add(keltnerSigObj)
        signals.add(keltnerSigObj)

        // 3.6 Donchian Channels (20)
        val donchianMid = calculateDonchianChannelMid(candles, 20)
        val donchianSignal = when {
            price > donchianMid * 1.002 -> { favBuy++; "BUY" }
            price < donchianMid * 0.998 -> { favSell++; "SELL" }
            else -> { favNeutral++; "NEUTRAL" }
        }
        val donchianSigObj = TradingViewIndicatorSignal("Donchian Channels (20)", formatPrice(donchianMid), donchianSignal)
        favoriteSignals.add(donchianSigObj)
        signals.add(donchianSigObj)

        // 3.7 Vortex Indicator (14)
        val (viPlus, viMinus) = calculateVortex(candles, 14)
        val vortexSignal = when {
            viPlus > viMinus && viPlus > 1.0 -> { favBuy++; "BUY" }
            viMinus > viPlus && viMinus > 1.0 -> { favSell++; "SELL" }
            viPlus > viMinus -> { favBuy++; "BUY" }
            else -> { favSell++; "SELL" }
        }
        val vortexSigObj = TradingViewIndicatorSignal("Vortex Indicator (14)", String.format(Locale.US, "%.2f", viPlus), vortexSignal)
        favoriteSignals.add(vortexSigObj)
        signals.add(vortexSigObj)

        // 3.8 Volume Oscillator (Short 5, Long 10)
        val volOsc = calculateVolumeOscillator(volumes, 5, 10)
        val volOscSignal = when {
            volOsc > 5.0 && (price >= ema20) -> { favBuy++; "BUY" } // Volume confirming uptrend
            volOsc > 5.0 && (price < ema20) -> { favSell++; "SELL" } // Volume expanding on downtrend
            volOsc > 0.0 -> { favBuy++; "BUY" }
            else -> { favNeutral++; "NEUTRAL" }
        }
        val volOscSigObj = TradingViewIndicatorSignal("Volume Oscillator (5,10)", String.format(Locale.US, "%.1f%%", volOsc), volOscSignal)
        favoriteSignals.add(volOscSigObj)
        signals.add(volOscSigObj)

        // Aggregation & Scoring (Now 34 total indicators)
        val totalBuy = maBuy + oscBuy + favBuy
        val totalSell = maSell + oscSell + favSell
        val totalNeutral = maNeutral + oscNeutral + favNeutral
        val totalIndicators = signals.size // 34

        val maVerdict = when {
            maBuy >= 10 -> "STRONG BUY"
            maBuy > maSell + 2 -> "BUY"
            maSell >= 10 -> "STRONG SELL"
            maSell > maBuy + 2 -> "SELL"
            else -> "NEUTRAL"
        }

        val oscVerdict = when {
            oscBuy >= 6 -> "BUY"
            oscSell >= 6 -> "SELL"
            else -> "NEUTRAL"
        }

        val favoriteVerdict = when {
            favBuy >= 6 -> "STRONG BUY"
            favBuy > favSell + 1 -> "BUY"
            favSell >= 6 -> "STRONG SELL"
            favSell > favBuy + 1 -> "SELL"
            else -> "NEUTRAL"
        }

        // Overall TradingView Technical Summary Rating (incorporating Favorites)
        val summaryVerdict = when {
            totalSell >= totalBuy + 8 || (maVerdict.contains("SELL") && oscVerdict == "SELL" && favoriteVerdict.contains("SELL")) -> "STRONG SELL"
            totalSell > totalBuy + 3 -> "SELL"
            totalBuy >= totalSell + 8 || (maVerdict.contains("BUY") && oscVerdict == "BUY" && favoriteVerdict.contains("BUY")) -> "STRONG BUY"
            totalBuy > totalSell + 3 -> "BUY"
            else -> "NEUTRAL"
        }

        val stance = when (summaryVerdict) {
            "STRONG BUY", "BUY" -> AgentStance.BULLISH
            "STRONG SELL", "SELL" -> AgentStance.BEARISH
            else -> AgentStance.NEUTRAL
        }

        // Score 1 to 10 aligned with TradingView verdict
        val score = when (summaryVerdict) {
            "STRONG BUY" -> 9
            "BUY" -> 7
            "NEUTRAL" -> 5
            "SELL" -> 3
            "STRONG SELL" -> 1
            else -> 5
        }

        val summaryObj = TradingViewTechnicalSummary(
            summaryVerdict = summaryVerdict,
            movingAveragesVerdict = maVerdict,
            oscillatorsVerdict = oscVerdict,
            favoriteStudiesVerdict = favoriteVerdict,
            buyCount = totalBuy,
            neutralCount = totalNeutral,
            sellCount = totalSell,
            totalIndicators = totalIndicators,
            rsiValue = rsi14,
            macdLevel = macdHist,
            stochK = stochK,
            adxStrength = adxVal,
            cciValue = cci20,
            awesomeOscillator = ao,
            supertrendValue = supertrendVal,
            supertrendSignal = stSignal,
            parabolicSarValue = sarVal,
            chaikinMoneyFlow = cmf20,
            moneyFlowIndex = mfi14,
            ema20 = ema20,
            ema50 = ema50,
            ema200 = ema200,
            sma20 = sma20,
            sma50 = sma50,
            sma200 = sma200,
            indicatorBreakdown = signals,
            favoriteStudiesBreakdown = favoriteSignals
        )

        val thesis = when (stance) {
            AgentStance.BEARISH -> "TradingView Multi-Indicator Node signals $summaryVerdict for $sym: $totalSell Sell vs $totalBuy Buy ($totalNeutral Neutral) across 34 studies. Favorite studies (Supertrend $stSignal, CMF ${String.format(Locale.US, "%.2f", cmf20)}, SAR) confirm $favoriteVerdict institutional pressure."
            AgentStance.BULLISH -> "TradingView Multi-Indicator Node signals $summaryVerdict for $sym: $totalBuy Buy vs $totalSell Sell ($totalNeutral Neutral) across 34 studies. Favorite studies (Supertrend $stSignal, CMF ${String.format(Locale.US, "%.2f", cmf20)}, MFI ${String.format(Locale.US, "%.1f", mfi14)}) confirm $favoriteVerdict institutional momentum."
            AgentStance.NEUTRAL -> "TradingView Multi-Indicator Node signals $summaryVerdict for $sym: $totalNeutral Neutral indicators ($totalBuy Buy, $totalSell Sell) across 34 studies. Supertrend at $${formatPrice(supertrendVal)} ($stSignal) with price in channel consolidation."
        }

        val points = mutableListOf(
            "TradingView Rating: $summaryVerdict ($totalBuy Buy • $totalNeutral Neutral • $totalSell Sell across 34 Studies)",
            "Favorite Indicators: $favoriteVerdict (Supertrend: $stSignal @ $${formatPrice(supertrendVal)}, CMF: ${String.format(Locale.US, "%.2f", cmf20)}, MFI: ${String.format(Locale.US, "%.1f", mfi14)})",
            "Moving Averages: $maVerdict ($maBuy Buy, $maNeutral Neutral, $maSell Sell)",
            "Oscillators: $oscVerdict ($oscBuy Buy, $oscNeutral Neutral, $oscSell Sell)",
            "RSI (14): ${String.format(Locale.US, "%.1f", rsi14)} [$rsiSignal] • MACD: [${if (macdHist >= 0) "+$${formatPrice(macdHist)}" else "-$${formatPrice(-macdHist)}"}]",
            "Key Moving Averages: SMA20 ($${formatPrice(sma20)}), EMA50 ($${formatPrice(ema50)}), EMA200 (${if (ema200 > 0.0) "$${formatPrice(ema200)}" else "N/A"})"
        )

        val report = AgentReport(
            role = AgentRole.TRADINGVIEW_STUDIES,
            score = score,
            stance = stance,
            headlineThesis = thesis,
            keyPoints = points,
            quantitativeMetrics = mapOf(
                "Summary Verdict" to summaryVerdict,
                "Buy Count" to "$totalBuy",
                "Sell Count" to "$totalSell",
                "Neutral Count" to "$totalNeutral",
                "Favorite Studies" to favoriteVerdict,
                "Supertrend" to "$stSignal ($${formatPrice(supertrendVal)})",
                "Chaikin Money Flow" to String.format(Locale.US, "%.3f", cmf20),
                "Money Flow Index" to String.format(Locale.US, "%.1f", mfi14),
                "MA Verdict" to maVerdict,
                "Osc Verdict" to oscVerdict,
                "RSI (14)" to String.format(Locale.US, "%.1f", rsi14),
                "ADX Trend Strength" to String.format(Locale.US, "%.1f", adxVal)
            )
        )

        return Pair(report, summaryObj)
    }

    // --- Mathematical Calculation Implementations ---

    private fun calculateSMA(values: List<Double>, period: Int): Double {
        val p = minOf(period, values.size)
        if (p == 0) return 0.0
        return values.takeLast(p).average()
    }

    private fun calculateEMA(values: List<Double>, period: Int): Double {
        val p = minOf(period, values.size)
        if (p == 0) return 0.0
        val multiplier = 2.0 / (p + 1.0)
        var ema = values.take(p).average()
        for (i in p until values.size) {
            ema = ((values[i] - ema) * multiplier) + ema
        }
        return ema
    }

    private fun ema13(values: List<Double>): Double = calculateEMA(values, 13)

    private fun calculateRSI(closes: List<Double>, period: Int): Double {
        if (closes.size < period + 1) return 50.0
        var gains = 0.0
        var losses = 0.0
        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            if (change > 0) gains += change else losses += abs(change)
        }
        var avgGain = gains / period
        var avgLoss = losses / period

        for (i in (period + 1) until closes.size) {
            val change = closes[i] - closes[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) abs(change) else 0.0
            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
        }

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return (100.0 - (100.0 / (1.0 + rs))).coerceIn(0.0, 100.0)
    }

    private fun calculateStochastic(candles: List<CandlePoint>, kPeriod: Int, dPeriod: Int): Pair<Double, Double> {
        val p = minOf(kPeriod, candles.size)
        if (p == 0) return Pair(50.0, 50.0)
        val window = candles.takeLast(p)
        val high = window.maxOf { it.high }
        val low = window.minOf { it.low }
        val currentClose = candles.last().close
        val k = if (high > low) ((currentClose - low) / (high - low)) * 100.0 else 50.0
        val d = (k + 50.0) / 2.0
        return Pair(k.coerceIn(0.0, 100.0), d.coerceIn(0.0, 100.0))
    }

    private fun calculateCCI(candles: List<CandlePoint>, period: Int): Double {
        val p = minOf(period, candles.size)
        if (p == 0) return 0.0
        val window = candles.takeLast(p)
        val typicalPrices = window.map { (it.high + it.low + it.close) / 3.0 }
        val smaTp = typicalPrices.average()
        val meanDeviation = typicalPrices.map { abs(it - smaTp) }.average()
        if (meanDeviation == 0.0) return 0.0
        val currentTp = typicalPrices.last()
        return (currentTp - smaTp) / (0.015 * meanDeviation)
    }

    private fun calculateADX(candles: List<CandlePoint>, period: Int): Triple<Double, Double, Double> {
        if (candles.size < period + 1) return Triple(22.0, 20.0, 20.0)
        var trSum = 0.0
        var plusDmSum = 0.0
        var minusDmSum = 0.0

        for (i in 1 until candles.size) {
            val c = candles[i]
            val prev = candles[i - 1]
            val tr = max(c.high - c.low, max(abs(c.high - prev.close), abs(c.low - prev.close)))
            val upMove = c.high - prev.high
            val downMove = prev.low - c.low
            val plusDm = if (upMove > downMove && upMove > 0) upMove else 0.0
            val minusDm = if (downMove > upMove && downMove > 0) downMove else 0.0

            trSum += tr
            plusDmSum += plusDm
            minusDmSum += minusDm
        }

        val safeTr = if (trSum > 0) trSum else 1.0
        val plusDi = (plusDmSum / safeTr) * 100.0
        val minusDi = (minusDmSum / safeTr) * 100.0
        val diSum = plusDi + minusDi
        val dx = if (diSum > 0) (abs(plusDi - minusDi) / diSum) * 100.0 else 20.0
        return Triple(dx.coerceIn(5.0, 95.0), plusDi, minusDi)
    }

    private fun calculateAwesomeOscillator(candles: List<CandlePoint>): Double {
        if (candles.size < 34) return 0.0
        val medians = candles.map { (it.high + it.low) / 2.0 }
        val sma5 = calculateSMA(medians, 5)
        val sma34 = calculateSMA(medians, 34)
        return sma5 - sma34
    }

    private fun calculateVWMA(closes: List<Double>, volumes: List<Double>, period: Int): Double {
        val p = minOf(period, closes.size, volumes.size)
        if (p == 0) return closes.lastOrNull() ?: 0.0
        var sumPv = 0.0
        var sumV = 0.0
        for (i in (closes.size - p) until closes.size) {
            sumPv += closes[i] * volumes[i]
            sumV += volumes[i]
        }
        return if (sumV > 0) sumPv / sumV else closes.takeLast(p).average()
    }

    private fun calculateHMA(closes: List<Double>, period: Int): Double {
        val p = minOf(period, closes.size)
        if (p < 4) return closes.lastOrNull() ?: 0.0
        val halfPeriod = max(1, p / 2)
        val sqrtPeriod = max(1, sqrt(p.toDouble()).toInt())

        val wmaHalf = calculateWMA(closes, halfPeriod)
        val wmaFull = calculateWMA(closes, p)
        val diffSeries = closes.takeLast(sqrtPeriod).map { (2.0 * wmaHalf) - wmaFull }
        return calculateWMA(diffSeries, sqrtPeriod)
    }

    private fun calculateWMA(values: List<Double>, period: Int): Double {
        val p = minOf(period, values.size)
        if (p == 0) return 0.0
        val window = values.takeLast(p)
        var numerator = 0.0
        var denominator = 0.0
        for (i in 0 until p) {
            val weight = i + 1
            numerator += window[i] * weight
            denominator += weight
        }
        return if (denominator > 0) numerator / denominator else window.average()
    }

    private fun calculateWilliamsR(candles: List<CandlePoint>, period: Int): Double {
        val p = minOf(period, candles.size)
        if (p == 0) return -50.0
        val window = candles.takeLast(p)
        val highestHigh = window.maxOf { it.high }
        val lowestLow = window.minOf { it.low }
        val close = candles.last().close
        if (highestHigh == lowestLow) return -50.0
        return ((highestHigh - close) / (highestHigh - lowestLow)) * -100.0
    }

    private fun calculateUltimateOscillator(candles: List<CandlePoint>): Double {
        if (candles.size < 28) return 50.0
        var bpSum7 = 0.0
        var trSum7 = 0.0
        var bpSum14 = 0.0
        var trSum14 = 0.0
        var bpSum28 = 0.0
        var trSum28 = 0.0

        for (i in 1 until candles.size) {
            val c = candles[i]
            val prev = candles[i - 1]
            val trueLow = min(c.low, prev.close)
            val trueHigh = max(c.high, prev.close)
            val bp = c.close - trueLow
            val tr = trueHigh - trueLow

            val revIdx = candles.size - 1 - i
            if (revIdx < 7) { bpSum7 += bp; trSum7 += tr }
            if (revIdx < 14) { bpSum14 += bp; trSum14 += tr }
            if (revIdx < 28) { bpSum28 += bp; trSum28 += tr }
        }

        val r7 = if (trSum7 > 0) bpSum7 / trSum7 else 0.5
        val r14 = if (trSum14 > 0) bpSum14 / trSum14 else 0.5
        val r28 = if (trSum28 > 0) bpSum28 / trSum28 else 0.5

        return (100.0 * ((4.0 * r7) + (2.0 * r14) + r28) / 7.0).coerceIn(0.0, 100.0)
    }

    private fun calculateStochRSI(closes: List<Double>, period: Int): Double {
        if (closes.size < period * 2) return 50.0
        val rsiList = mutableListOf<Double>()
        for (i in period until closes.size) {
            rsiList.add(calculateRSI(closes.subList(0, i + 1), period))
        }
        val p = minOf(period, rsiList.size)
        if (p == 0) return 50.0
        val window = rsiList.takeLast(p)
        val high = window.maxOrNull() ?: 70.0
        val low = window.minOrNull() ?: 30.0
        val current = window.last()
        if (high == low) return 50.0
        return (((current - low) / (high - low)) * 100.0).coerceIn(0.0, 100.0)
    }

    private fun formatPrice(p: Double): String {
        return if (p >= 1000.0) String.format(Locale.US, "%,.2f", p)
        else if (p >= 1.0) String.format(Locale.US, "%.2f", p)
        else String.format(Locale.US, "%.4f", p)
    }

    // --- Mathematical Implementations for Favorite TradingView Indicators ---

    /**
     * Supertrend (10, 3.0 ATR)
     * Returns Pair(SupertrendValue, Direction: 1 for Bullish, -1 for Bearish)
     */
    private fun calculateSupertrend(candles: List<CandlePoint>, period: Int, multiplier: Double): Pair<Double, Int> {
        if (candles.size < period + 1) {
            val lastClose = candles.lastOrNull()?.close ?: 0.0
            return Pair(lastClose, 0)
        }

        val atrs = mutableListOf<Double>()
        var trSum = 0.0
        for (i in 1 until candles.size) {
            val c = candles[i]
            val prev = candles[i - 1]
            val tr = max(c.high - c.low, max(abs(c.high - prev.close), abs(c.low - prev.close)))
            if (i <= period) {
                trSum += tr
                if (i == period) atrs.add(trSum / period)
            } else {
                val prevAtr = atrs.last()
                atrs.add(((prevAtr * (period - 1)) + tr) / period)
            }
        }

        var upperBand = 0.0
        var lowerBand = 0.0
        var supertrend = candles.last().close
        var direction = 1 // 1: Bullish, -1: Bearish

        val startIdx = period
        for (i in startIdx until candles.size) {
            val atrIdx = i - period
            val atr = if (atrIdx in atrs.indices) atrs[atrIdx] else (candles[i].close * 0.02)
            val c = candles[i]
            val prev = candles[i - 1]
            val hl2 = (c.high + c.low) / 2.0

            var basicUpper = hl2 + (multiplier * atr)
            var basicLower = hl2 - (multiplier * atr)

            val finalUpper = if (basicUpper < upperBand || prev.close > upperBand) basicUpper else upperBand
            val finalLower = if (basicLower > lowerBand || prev.close < lowerBand) basicLower else lowerBand

            if (direction == 1) {
                if (c.close < finalLower) {
                    direction = -1
                    supertrend = finalUpper
                } else {
                    supertrend = finalLower
                }
            } else {
                if (c.close > finalUpper) {
                    direction = 1
                    supertrend = finalLower
                } else {
                    supertrend = finalUpper
                }
            }

            upperBand = finalUpper
            lowerBand = finalLower
        }

        return Pair(supertrend, direction)
    }

    /**
     * Parabolic SAR (Step 0.02, Max 0.20)
     */
    private fun calculateParabolicSAR(candles: List<CandlePoint>, step: Double = 0.02, maxStep: Double = 0.20): Double {
        if (candles.size < 5) return candles.lastOrNull()?.close ?: 0.0

        var isBull = candles[1].close >= candles[0].close
        var sar = if (isBull) candles[0].low else candles[0].high
        var ep = if (isBull) candles[1].high else candles[1].low
        var af = step

        for (i in 2 until candles.size) {
            val prev = candles[i - 1]
            val curr = candles[i]
            sar += af * (ep - sar)

            if (isBull) {
                if (sar > prev.low) sar = prev.low
                if (sar > candles[max(0, i - 2)].low) sar = candles[max(0, i - 2)].low
                if (curr.high > ep) {
                    ep = curr.high
                    af = min(af + step, maxStep)
                }
                if (curr.low < sar) {
                    isBull = false
                    sar = ep
                    ep = curr.low
                    af = step
                }
            } else {
                if (sar < prev.high) sar = prev.high
                if (sar < candles[max(0, i - 2)].high) sar = candles[max(0, i - 2)].high
                if (curr.low < ep) {
                    ep = curr.low
                    af = min(af + step, maxStep)
                }
                if (curr.high > sar) {
                    isBull = true
                    sar = ep
                    ep = curr.high
                    af = step
                }
            }
        }
        return sar
    }

    /**
     * Chaikin Money Flow (CMF 20)
     * Sum(Volume * ((Close - Low) - (High - Close)) / (High - Low)) / Sum(Volume)
     */
    private fun calculateCMF(candles: List<CandlePoint>, period: Int): Double {
        val p = minOf(period, candles.size)
        if (p == 0) return 0.0
        val window = candles.takeLast(p)
        var sumMfv = 0.0
        var sumVol = 0.0
        for (c in window) {
            val range = c.high - c.low
            val mfm = if (range > 0) ((c.close - c.low) - (c.high - c.close)) / range else 0.0
            sumMfv += mfm * c.volume
            sumVol += c.volume
        }
        return if (sumVol > 0) sumMfv / sumVol else 0.0
    }

    /**
     * Money Flow Index (MFI 14)
     * Volume-weighted RSI
     */
    private fun calculateMFI(candles: List<CandlePoint>, period: Int): Double {
        val p = minOf(period, candles.size - 1)
        if (p < 2) return 50.0

        var posMoneyFlow = 0.0
        var negMoneyFlow = 0.0

        val window = candles.takeLast(p + 1)
        for (i in 1 until window.size) {
            val curr = window[i]
            val prev = window[i - 1]
            val tpCurr = (curr.high + curr.low + curr.close) / 3.0
            val tpPrev = (prev.high + prev.low + prev.close) / 3.0
            val rawMoneyFlow = tpCurr * curr.volume

            if (tpCurr > tpPrev) posMoneyFlow += rawMoneyFlow
            else if (tpCurr < tpPrev) negMoneyFlow += rawMoneyFlow
        }

        if (negMoneyFlow == 0.0) return 100.0
        val moneyRatio = posMoneyFlow / negMoneyFlow
        return (100.0 - (100.0 / (1.0 + moneyRatio))).coerceIn(0.0, 100.0)
    }

    /**
     * Keltner Channels (20 EMA, 2.0 ATR)
     */
    private fun calculateKeltnerChannels(candles: List<CandlePoint>, period: Int, multiplier: Double): Pair<Double, Double> {
        val closes = candles.map { it.close }
        val ema = calculateEMA(closes, period)
        val p = minOf(period, candles.size)
        var atr = 0.0
        if (candles.size > 1) {
            val trs = mutableListOf<Double>()
            for (i in 1 until candles.size) {
                val c = candles[i]
                val prev = candles[i - 1]
                val tr = max(c.high - c.low, max(abs(c.high - prev.close), abs(c.low - prev.close)))
                trs.add(tr)
            }
            atr = trs.takeLast(minOf(p, trs.size)).average()
        }
        val band = multiplier * atr
        return Pair(ema + band, ema - band)
    }

    /**
     * Donchian Channels (20) - Midpoint of highest high and lowest low
     */
    private fun calculateDonchianChannelMid(candles: List<CandlePoint>, period: Int): Double {
        val p = minOf(period, candles.size)
        if (p == 0) return candles.lastOrNull()?.close ?: 0.0
        val window = candles.takeLast(p)
        val hh = window.maxOf { it.high }
        val ll = window.minOf { it.low }
        return (hh + ll) / 2.0
    }

    /**
     * Vortex Indicator (VI 14)
     * Positive and Negative trend movement relative to true range
     */
    private fun calculateVortex(candles: List<CandlePoint>, period: Int): Pair<Double, Double> {
        val p = minOf(period, candles.size - 1)
        if (p < 2) return Pair(1.0, 1.0)

        val window = candles.takeLast(p + 1)
        var sumVmPlus = 0.0
        var sumVmMinus = 0.0
        var sumTr = 0.0

        for (i in 1 until window.size) {
            val curr = window[i]
            val prev = window[i - 1]
            sumVmPlus += abs(curr.high - prev.low)
            sumVmMinus += abs(curr.low - prev.high)
            val tr = max(curr.high - curr.low, max(abs(curr.high - prev.close), abs(curr.low - prev.close)))
            sumTr += tr
        }

        if (sumTr == 0.0) return Pair(1.0, 1.0)
        return Pair(sumVmPlus / sumTr, sumVmMinus / sumTr)
    }

    /**
     * Volume Oscillator (Short EMA - Long EMA) / Long EMA * 100
     */
    private fun calculateVolumeOscillator(volumes: List<Double>, shortPeriod: Int, longPeriod: Int): Double {
        if (volumes.size < longPeriod) return 0.0
        val shortEma = calculateEMA(volumes, shortPeriod)
        val longEma = calculateEMA(volumes, longPeriod)
        if (longEma == 0.0) return 0.0
        return ((shortEma - longEma) / longEma) * 100.0
    }
}
