package com.example.domain.engine

import com.example.domain.model.AgentRole
import com.example.domain.model.AgentStance
import com.example.domain.model.CandlePoint
import com.example.domain.model.CryptoFuturesData
import com.example.domain.model.FamilyVote
import com.example.domain.model.MarketQuote
import com.example.domain.model.MarketRegime
import com.example.domain.model.NewsItem
import com.example.domain.model.NewsScannerResult
import com.example.domain.model.OrderFlowReport
import com.example.domain.model.PositionSide
import com.example.domain.model.SignalFamily
import com.example.domain.model.TechnicalIndicators
import com.example.domain.util.PriceFormatter
import kotlin.math.abs

/**
 * Signal Engine v2: Signal Family Scorer & Red Team Conflict Evaluator
 * Max ONE vote per family. Correlated indicators merge into a single family vote.
 */
object SignalFamilyScorer {

    data class FamilyScoringResult(
        val votes: List<FamilyVote>,
        val alignedCount: Int,
        val totalCount: Int = 7,
        val alignedFamilies: List<SignalFamily>,
        val conflicts: List<String>,
        val redTeamConflictScore: Double,
        val redTeamCounterEvidence: List<String>,
        val structureHasFreshBreak: Boolean,
        val structureBreakDirection: PositionSide?,
        val trendMovingAverageDirection: PositionSide?,
        val isMixedStructure: Boolean = false
    )

    fun evaluateFamilies(
        candidateSide: PositionSide,
        quote: MarketQuote,
        candles: List<CandlePoint>,
        indicators: TechnicalIndicators,
        htfIndicators: TechnicalIndicators?,
        orderFlow: OrderFlowReport?,
        futuresData: CryptoFuturesData?,
        news: List<NewsItem>,
        scannerResult: NewsScannerResult?,
        regime: MarketRegime
    ): FamilyScoringResult {
        val currentPrice = quote.currentPrice
        val votes = mutableListOf<FamilyVote>()
        val conflicts = mutableListOf<String>()
        val counterEvidence = mutableListOf<String>()

        val isLong = candidateSide == PositionSide.LONG

        // -------------------------------------------------------------
        // 1. TREND FAMILY (HTF / Setup TF, bias only)
        // Merges EMA 20/50/200, SMA 20/50, ADX/DMI, HH/HL, Bollinger midline
        // -------------------------------------------------------------
        val p = currentPrice
        val ema50 = indicators.ema50.takeIf { it > 0 } ?: indicators.sma50
        val hasValidEma200 = indicators.ema200 > 0.0 && candles.size >= 250
        val ema200 = if (hasValidEma200) indicators.ema200 else 0.0
        val adx = indicators.adx
        val plusDi = indicators.plusDi
        val minusDi = indicators.minusDi

        val isBullishTrend = if (hasValidEma200) {
            (p > ema50 && ema50 > ema200 && plusDi > minusDi && adx >= 20.0)
        } else {
            (p > ema50 && plusDi > minusDi && adx >= 20.0)
        }
        val isBearishTrend = if (hasValidEma200) {
            (p < ema50 && ema50 < ema200 && minusDi > plusDi && adx >= 20.0)
        } else {
            (p < ema50 && minusDi > plusDi && adx >= 20.0)
        }

        val trendStance = when {
            isBullishTrend -> AgentStance.BULLISH
            isBearishTrend -> AgentStance.BEARISH
            p > ema50 -> AgentStance.BULLISH
            p < ema50 -> AgentStance.BEARISH
            else -> AgentStance.NEUTRAL
        }

        val trendStrength = when (trendStance) {
            AgentStance.BULLISH -> if (isBullishTrend) 0.85 else 0.45
            AgentStance.BEARISH -> if (isBearishTrend) -0.85 else -0.45
            AgentStance.NEUTRAL -> 0.0
        }

        val trendReasons = mutableListOf<String>()
        if (p > ema50) trendReasons.add("Price (${PriceFormatter.formatCurrency(p)}) > EMA50 (${PriceFormatter.formatCurrency(ema50)})")
        else trendReasons.add("Price (${PriceFormatter.formatCurrency(p)}) < EMA50 (${PriceFormatter.formatCurrency(ema50)})")

        if (hasValidEma200) {
            if (ema50 > ema200) trendReasons.add("EMA50 > EMA200 (Golden alignment)")
            else trendReasons.add("EMA50 < EMA200 (Death alignment)")
        } else {
            trendReasons.add("EMA200: N/A (need 250 bars)")
        }

        if (adx >= 20.0) trendReasons.add("ADX at ${String.format(java.util.Locale.US, "%.1f", adx)} (Trend active)")
        else if (adx < 15.0) trendReasons.add("ADX at ${String.format(java.util.Locale.US, "%.1f", adx)} (No trend / range)")
        else trendReasons.add("ADX at ${String.format(java.util.Locale.US, "%.1f", adx)} (Weak trend / range)")

        val trendVote = FamilyVote(
            family = SignalFamily.TREND,
            stance = trendStance,
            signedStrength = trendStrength,
            freshness = 1.0,
            weight = 1.0,
            reasons = trendReasons,
            rawValues = mapOf(
                "EMA50" to PriceFormatter.formatPrice(ema50),
                "EMA200" to if (hasValidEma200) PriceFormatter.formatPrice(ema200) else "N/A",
                "ADX" to String.format(java.util.Locale.US, "%.1f", adx)
            ),
            isAlignedWithCandidate = (isLong && trendStance == AgentStance.BULLISH) || (!isLong && trendStance == AgentStance.BEARISH)
        )
        votes.add(trendVote)

        val maTrendDirection = if (isBullishTrend || p > ema50) PositionSide.LONG else PositionSide.SHORT

        // -------------------------------------------------------------
        // 2. STRUCTURE / LOCATION (SMC: BOS/CHoCH, OB, FVG, Swings, Displacements)
        // -------------------------------------------------------------
        val smc = indicators.smc
        val lastBos = smc?.lastBOS
        val lastChoch = smc?.lastCHoCH
        val unmitigatedObs = smc?.activeOrderBlocks ?: emptyList()
        val unmitigatedFvgs = smc?.activeFVGs ?: emptyList()
        val pdPercent = smc?.premiumDiscountPercent ?: 50.0

        val hasBullishStructure = (lastBos != null && isLong) ||
                smc?.builtInSmcSignal?.contains("BULLISH") == true ||
                smc?.recentLiquiditySweep?.type == com.example.domain.model.LiquidityType.SELL_SIDE_LIQUIDITY_SSL ||
                smc?.smcInternalStructure?.contains("Bullish") == true

        val hasBearishStructure = (lastChoch != null && !isLong) ||
                smc?.builtInSmcSignal?.contains("BEARISH") == true ||
                smc?.recentLiquiditySweep?.type == com.example.domain.model.LiquidityType.BUY_SIDE_LIQUIDITY_BSL ||
                smc?.smcInternalStructure?.contains("Bearish") == true

        val swingIsBearish = smc?.smcSwingStructure?.contains("Bearish", ignoreCase = true) == true
        val swingIsBullish = smc?.smcSwingStructure?.contains("Bullish", ignoreCase = true) == true
        val internalIsBearish = smc?.smcInternalStructure?.contains("Bearish", ignoreCase = true) == true
        val internalIsBullish = smc?.smcInternalStructure?.contains("Bullish", ignoreCase = true) == true

        val isMixedStructure = (swingIsBearish && internalIsBullish) || (swingIsBullish && internalIsBearish)

        val structureStance = when {
            isMixedStructure -> AgentStance.NEUTRAL
            hasBullishStructure && !hasBearishStructure -> AgentStance.BULLISH
            hasBearishStructure && !hasBullishStructure -> AgentStance.BEARISH
            pdPercent < 45.0 -> AgentStance.BULLISH // Discount
            pdPercent > 55.0 -> AgentStance.BEARISH // Premium
            else -> AgentStance.NEUTRAL
        }

        val structureStrength = when {
            isMixedStructure -> 0.0
            structureStance == AgentStance.BULLISH -> 0.80
            structureStance == AgentStance.BEARISH -> -0.80
            else -> 0.0
        }

        val structureReasons = mutableListOf<String>()
        var mixedDescription: String? = null
        smc?.let {
            if (isMixedStructure) {
                val swingDesc = if (swingIsBearish) "bearish" else "bullish"
                val intDesc = if (internalIsBullish) "bullish" else "bearish"
                mixedDescription = "MIXED structure (swing $swingDesc / internal $intDesc)"
                structureReasons.add("Structure: $mixedDescription")
            } else {
                structureReasons.add("Structure: ${it.smcInternalStructure} / ${it.smcSwingStructure}")
            }
            structureReasons.add("Built-in SMC Signal: ${it.builtInSmcSignal}")
            structureReasons.add("P/D Zone: ${it.premiumDiscountZone} (${String.format(java.util.Locale.US, "%.1f", pdPercent)}%)")
            if (unmitigatedObs.isNotEmpty()) {
                val ob = unmitigatedObs.last()
                structureReasons.add("Fresh OB: ${ob.type.label} at ${PriceFormatter.formatZoneRange(ob.bottom, ob.top)}")
            }
            if (unmitigatedFvgs.isNotEmpty()) {
                val fvg = unmitigatedFvgs.last()
                structureReasons.add("Fresh FVG: ${fvg.type.label} at ${PriceFormatter.formatCurrency(fvg.midPrice)}")
            }
        }

        val isAligned = !isMixedStructure && ((isLong && structureStance == AgentStance.BULLISH) || (!isLong && structureStance == AgentStance.BEARISH))

        val structureVote = FamilyVote(
            family = SignalFamily.STRUCTURE,
            stance = structureStance,
            signedStrength = structureStrength,
            freshness = 1.0,
            weight = 1.0,
            reasons = structureReasons,
            rawValues = mapOf(
                "PD_Percent" to String.format(java.util.Locale.US, "%.1f", pdPercent),
                "Unmitigated_OB_Count" to unmitigatedObs.size.toString(),
                "Unmitigated_FVG_Count" to unmitigatedFvgs.size.toString(),
                "IsMixed" to isMixedStructure.toString()
            ),
            isAlignedWithCandidate = isAligned
        )
        votes.add(structureVote)

        // Fresh structure break tracking
        val structureHasFreshBreak = lastBos != null || lastChoch != null || smc?.recentLiquiditySweep != null
        val structureBreakDirection: PositionSide? = when {
            hasBullishStructure -> PositionSide.LONG
            hasBearishStructure -> PositionSide.SHORT
            else -> null
        }

        // -------------------------------------------------------------
        // 3. VALUE & VOLUME PROFILE (VWAP, POC, VAH/VAL, RVOL)
        // -------------------------------------------------------------
        val vwap = indicators.sessionVwap.takeIf { it > 0 } ?: currentPrice
        val volProfile = indicators.volumeProfile
        val poc = volProfile?.poc ?: vwap
        val rvol = indicators.rvol

        val valueStance = when {
            isLong && p in (poc * 0.985)..(poc * 1.015) -> AgentStance.BULLISH // At value node
            !isLong && p in (poc * 0.985)..(poc * 1.015) -> AgentStance.BEARISH
            p > vwap && rvol >= 1.0 -> AgentStance.BULLISH
            p < vwap && rvol >= 1.0 -> AgentStance.BEARISH
            else -> AgentStance.NEUTRAL
        }

        val valueVote = FamilyVote(
            family = SignalFamily.VALUE_VOLUME,
            stance = valueStance,
            signedStrength = if (valueStance == AgentStance.BULLISH) 0.60 else if (valueStance == AgentStance.BEARISH) -0.60 else 0.0,
            freshness = 1.0,
            weight = 1.0,
            reasons = listOf(
                "Session VWAP at ${PriceFormatter.formatCurrency(vwap)} (Price is ${if (p >= vwap) "above" else "below"})",
                "Volume Profile POC at ${PriceFormatter.formatCurrency(poc)}",
                "RVOL at ${String.format(java.util.Locale.US, "%.2f", rvol)}x 14d average"
            ),
            rawValues = mapOf("VWAP" to PriceFormatter.formatPrice(vwap), "POC" to PriceFormatter.formatPrice(poc), "RVOL" to String.format(java.util.Locale.US, "%.2f", rvol)),
            isAlignedWithCandidate = (isLong && valueStance == AgentStance.BULLISH) || (!isLong && valueStance == AgentStance.BEARISH)
        )
        votes.add(valueVote)

        // -------------------------------------------------------------
        // 4. VOLATILITY (ATR, Bollinger Bandwidth, Efficiency Ratio)
        // -------------------------------------------------------------
        val atr = indicators.averageTrueRange
        val er = indicators.efficiencyRatio
        val bwPct = indicators.bandwidthPercentile

        val volStance = when {
            regime == MarketRegime.EXPANSION -> if (isLong) AgentStance.BULLISH else AgentStance.BEARISH
            regime == MarketRegime.TREND -> if (isLong) AgentStance.BULLISH else AgentStance.BEARISH
            regime == MarketRegime.DEAD -> AgentStance.NEUTRAL
            else -> AgentStance.NEUTRAL
        }

        val volVote = FamilyVote(
            family = SignalFamily.VOLATILITY,
            stance = volStance,
            signedStrength = if (regime == MarketRegime.TREND || regime == MarketRegime.EXPANSION) 0.65 else 0.0,
            freshness = 1.0,
            weight = 1.0,
            reasons = listOf(
                "Regime: ${regime.label}",
                "ATR(14): ${PriceFormatter.formatCurrency(atr)} (pct ${String.format(java.util.Locale.US, "%.0f", indicators.atrPercentile)}%)",
                "Efficiency Ratio: ${String.format(java.util.Locale.US, "%.2f", er)}",
                "Bollinger Bandwidth Percentile: ${String.format(java.util.Locale.US, "%.1f", bwPct)}%"
            ),
            rawValues = mapOf("ATR" to PriceFormatter.formatPrice(atr), "ER" to String.format(java.util.Locale.US, "%.2f", er)),
            isAlignedWithCandidate = (isLong && volStance == AgentStance.BULLISH) || (!isLong && volStance == AgentStance.BEARISH)
        )
        votes.add(volVote)

        // -------------------------------------------------------------
        // 5. MOMENTUM / TIMING (RSI context, MACD histogram, divergences)
        // -------------------------------------------------------------
        val rsi = indicators.rsi14
        val macdHist = indicators.macd.histogram

        val momentumStance = when {
            isLong && (rsi in 40.0..65.0 || macdHist > 0) -> AgentStance.BULLISH
            !isLong && (rsi in 35.0..60.0 || macdHist < 0) -> AgentStance.BEARISH
            rsi > 70.0 -> AgentStance.BEARISH // Overbought caution
            rsi < 30.0 -> AgentStance.BULLISH // Oversold caution
            else -> AgentStance.NEUTRAL
        }

        val momVote = FamilyVote(
            family = SignalFamily.MOMENTUM_TIMING,
            stance = momentumStance,
            signedStrength = if (momentumStance == AgentStance.BULLISH) 0.50 else if (momentumStance == AgentStance.BEARISH) -0.50 else 0.0,
            freshness = 1.0,
            weight = 1.0,
            reasons = listOf(
                "RSI(14) at ${String.format(java.util.Locale.US, "%.1f", rsi)}",
                "MACD Histogram: ${String.format(java.util.Locale.US, "%.3f", macdHist)}"
            ),
            rawValues = mapOf("RSI" to String.format(java.util.Locale.US, "%.1f", rsi)),
            isAlignedWithCandidate = (isLong && momentumStance == AgentStance.BULLISH) || (!isLong && momentumStance == AgentStance.BEARISH)
        )
        votes.add(momVote)

        // -------------------------------------------------------------
        // 6. FLOW / POSITIONING (Order Book, CVD, Open Interest, Funding)
        // -------------------------------------------------------------
        val flowBias = orderFlow?.biasScore ?: 0.0
        val funding = (futuresData?.fundingRatePercent ?: 0.0) / 100.0
        val oiChange = 0.0

        val flowStance = when {
            flowBias > 15.0 || (funding < -0.005 && isLong) -> AgentStance.BULLISH
            flowBias < -15.0 || (funding > 0.015 && !isLong) -> AgentStance.BEARISH
            else -> AgentStance.NEUTRAL
        }

        val flowVote = FamilyVote(
            family = SignalFamily.FLOW_POSITIONING,
            stance = flowStance,
            signedStrength = (flowBias / 100.0).coerceIn(-1.0, 1.0),
            freshness = 1.0,
            weight = 1.0,
            reasons = listOf(
                "Order Flow Bias: ${String.format(java.util.Locale.US, "%.1f", flowBias)}/100",
                "Perp Funding Rate: ${String.format(java.util.Locale.US, "%.4f", funding * 100)}%",
                "OI Change: ${String.format(java.util.Locale.US, "%.2f", oiChange)}%"
            ),
            rawValues = mapOf("FlowBias" to String.format(java.util.Locale.US, "%.1f", flowBias)),
            isAlignedWithCandidate = (isLong && flowStance == AgentStance.BULLISH) || (!isLong && flowStance == AgentStance.BEARISH)
        )
        votes.add(flowVote)

        // -------------------------------------------------------------
        // 7. CONTEXT (News Polarity, Sentiment Catalyst, Macro)
        // -------------------------------------------------------------
        val hasRelevantNews = news.isNotEmpty() || (scannerResult?.headlines?.isNotEmpty() == true)
        val scannerScore = if (hasRelevantNews) (scannerResult?.sentimentScore ?: 0.0) else 0.0
        val contextStance = when {
            !hasRelevantNews -> AgentStance.NEUTRAL
            scannerScore > 0.15 -> AgentStance.BULLISH
            scannerScore < -0.15 -> AgentStance.BEARISH
            else -> AgentStance.NEUTRAL
        }

        val contextVote = FamilyVote(
            family = SignalFamily.CONTEXT,
            stance = contextStance,
            signedStrength = scannerScore.coerceIn(-1.0, 1.0),
            freshness = 1.0,
            weight = 1.0,
            reasons = listOf(
                if (hasRelevantNews) "News Polarity: ${scannerResult?.sentimentPolarity?.name ?: "NEUTRAL"}" else "Sentiment: N/A",
                if (hasRelevantNews) "Catalyst: ${scannerResult?.primaryCatalyst?.takeIf { it.isNotBlank() } ?: "Headline scan active"}" else "No relevant news for ${quote.symbol}"
            ),
            rawValues = mapOf("SentimentScore" to if (hasRelevantNews) String.format(java.util.Locale.US, "%.2f", scannerScore) else "-"),
            isAlignedWithCandidate = (isLong && contextStance == AgentStance.BULLISH) || (!isLong && contextStance == AgentStance.BEARISH)
        )
        votes.add(contextVote)

        // -------------------------------------------------------------
        // RED TEAM EVALUATION & CONFLICT DETECTION
        // -------------------------------------------------------------
        val opposingVotes = votes.filter {
            (isLong && it.stance == AgentStance.BEARISH) || (!isLong && it.stance == AgentStance.BULLISH)
        }

        // 0. Explicit Mixed SMC Structure conflict (swing vs internal disagree)
        if (isMixedStructure && mixedDescription != null) {
            val mixedMsg = "STRUCTURAL CONFLICT: $mixedDescription — not aligned."
            conflicts.add(mixedMsg)
            counterEvidence.add(mixedMsg)
        }

        // 1. Structure Break vs Lagging Moving Average Conflict
        if (structureHasFreshBreak && structureBreakDirection != null && maTrendDirection != structureBreakDirection) {
            val conflictMsg = "CRITICAL CONFLICT: Fresh structure break (${structureBreakDirection.name} BOS/CHoCH) disagrees with lagging moving averages (${maTrendDirection.name})."
            conflicts.add(conflictMsg)
            counterEvidence.add(conflictMsg)
        }

        opposingVotes.forEach { opp ->
            val msg = "${opp.family.displayName} opposes setup (${opp.stance.name}): ${opp.reasons.firstOrNull() ?: ""}"
            conflicts.add(msg)
            counterEvidence.add(msg)
        }

        // Calculate Red Team conflict score (0.0 to 1.0)
        var conflictScore = (opposingVotes.size.toDouble() / 7.0)
        if (isMixedStructure) {
            conflictScore = maxOf(conflictScore, 0.25)
        }
        if (structureHasFreshBreak && structureBreakDirection != null && maTrendDirection != structureBreakDirection) {
            conflictScore = maxOf(conflictScore, 0.40) // Immediately exceeds 0.35 threshold!
        }

        val alignedVotes = votes.filter { it.isAlignedWithCandidate }

        return FamilyScoringResult(
            votes = votes,
            alignedCount = alignedVotes.size,
            alignedFamilies = alignedVotes.map { it.family },
            conflicts = conflicts,
            redTeamConflictScore = conflictScore,
            redTeamCounterEvidence = counterEvidence,
            structureHasFreshBreak = structureHasFreshBreak,
            structureBreakDirection = structureBreakDirection,
            trendMovingAverageDirection = maTrendDirection,
            isMixedStructure = isMixedStructure
        )
    }
}
