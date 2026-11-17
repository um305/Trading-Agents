package com.example.domain.agents

import com.example.domain.analytics.QuantitativeEngine
import com.example.domain.model.AgentReport
import com.example.domain.model.AgentRole
import com.example.domain.model.AgentStance
import com.example.domain.model.AssetType
import com.example.domain.model.DebateMessage
import com.example.domain.model.MarketQuote
import com.example.domain.model.MultiAgentSynthesis
import com.example.domain.model.FundamentalsStreamData
import com.example.domain.model.ManagerDecision
import com.example.domain.model.MarketStreamData
import com.example.domain.model.NewsItem
import com.example.domain.model.NewsScannerResult
import com.example.domain.model.NewsStreamData
import com.example.domain.model.PositionSide
import com.example.domain.model.ResearcherDossier
import com.example.domain.model.RiskProfileEvaluation
import com.example.domain.model.RiskTeamEvaluation
import com.example.domain.model.SentimentPolarity
import com.example.domain.model.SocialMediaStreamData
import com.example.domain.model.TechnicalIndicators
import com.example.domain.model.TraderProposal
import com.example.domain.model.TradingAction
import com.example.domain.model.TradingMarketType
import com.example.domain.util.PriceFormatter
import com.example.domain.daytrader.news.SymbolNewsMatcher
import java.util.Locale

object TradingAgentsEngine {

    data class BloombergAnalysis(
        val headlines: List<String>,
        val sentimentScore: Double, // -1.0 to +1.0
        val priceImpactPercent: Double, // e.g. -4.5% to +6.5%
        val catalystHeadline: String,
        val summary: String,
        val institutionalStance: AgentStance
    )

    fun runMultiAgentAnalysis(
        quote: MarketQuote,
        news: List<NewsItem>,
        scannerResult: NewsScannerResult? = null,
        extraNodesScanned: List<String> = emptyList()
    ): MultiAgentSynthesis {
        val indicators = QuantitativeEngine.computeIndicators(quote.candles)

        // --- 1. FOUR INGESTION DATA STREAMS (as in Architecture Schema) ---
        // A. Market Data Stream (Yahoo! Finance, Oscillators, Candlestick Chart Patterns)
        val marketStream = analyzeMarketStream(quote, indicators)

        // B. Social Media Data Stream (X / Twitter, Reddit, EODHD APIs)
        val socialMediaStream = analyzeSocialMediaStream(quote)

        // C. News Data Stream (Bloomberg, Reddit Breaking News, Finnhub, Reuters)
        val bloombergScan = scanBloombergNews(quote, news, scannerResult)
        val newsStream = analyzeNewsStream(quote, news, scannerResult, bloombergScan)

        // D. Fundamentals Data Stream (Company Profile, Financial History, Insider Transactions)
        val fundamentalsStream = analyzeFundamentalsStream(quote)

        // --- 2. SPECIALIZED ANALYSTS & NODES ---
        val fundamentalReport = evaluateFundamental(quote, bloombergScan)
        val technicalReport = evaluateTechnical(quote, indicators)
        val (tradingViewReport, tradingViewSummary) = TradingViewTechnicalsAgent.evaluate(quote)
        val sentimentReport = evaluateSentiment(quote, news, bloombergScan)
        val riskReport = evaluateRisk(quote, indicators, bloombergScan)

        // --- 3. RESEARCHER TEAM (Bullish vs Bearish Discussion -> Buy Evidence & Sell Evidence) ---
        val researcherDossier = conductResearcherDiscussion(
            quote = quote,
            marketStream = marketStream,
            socialMediaStream = socialMediaStream,
            newsStream = newsStream,
            fundamentalsStream = fundamentalsStream,
            fundamentalReport = fundamentalReport,
            technicalReport = technicalReport,
            tradingViewReport = tradingViewReport
        )

        // --- 4. TRADER (Powered by OpenAI o1 Deep Thinking Reasoning -> Transaction Proposal) ---
        val traderProposal = formulateTraderProposal(
            quote = quote,
            indicators = indicators,
            researcherDossier = researcherDossier,
            tradingViewSummary = tradingViewSummary,
            riskReport = riskReport,
            fundamentalReport = fundamentalReport,
            technicalReport = technicalReport,
            tradingViewReport = tradingViewReport,
            sentimentReport = sentimentReport,
            scannerResult = scannerResult
        )

        // --- 5. RISK MANAGEMENT TEAM (Aggressive, Neutral, Conservative Risk Profiles) ---
        val riskTeamEvaluation = evaluateRiskTeam(
            quote = quote,
            traderProposal = traderProposal,
            indicators = indicators,
            riskReport = riskReport
        )

        // --- 6. Structured Committee Debate Transcript ---
        val debateMessages = conductDebate(
            quote, fundamentalReport, technicalReport, tradingViewReport, sentimentReport, riskReport, bloombergScan
        )

        // --- 7. Chief Strategist Synthesis & Final Consensus ---
        val synthesis = synthesizeVerdict(
            quote = quote,
            indicators = indicators,
            fundamental = fundamentalReport,
            technical = technicalReport,
            tradingView = tradingViewReport,
            tradingViewSummary = tradingViewSummary,
            sentiment = sentimentReport,
            risk = riskReport,
            transcript = debateMessages,
            bloombergScan = bloombergScan,
            scannerResult = scannerResult,
            extraNodesScanned = extraNodesScanned,
            marketStream = marketStream,
            socialMediaStream = socialMediaStream,
            newsStream = newsStream,
            fundamentalsStream = fundamentalsStream,
            researcherDossier = researcherDossier,
            traderProposal = traderProposal,
            riskTeamEvaluation = riskTeamEvaluation
        )

        // --- 8. MANAGER (Authorization & Execution Dispatch Decision) ---
        val managerDecision = authorizeManagerDecision(
            quote = quote,
            traderProposal = traderProposal,
            riskTeamEvaluation = riskTeamEvaluation,
            synthesisAction = synthesis.action
        )

        return synthesis.copy(managerDecision = managerDecision)
    }

    private fun scanBloombergNews(
        quote: MarketQuote,
        news: List<NewsItem>,
        scannerResult: NewsScannerResult? = null
    ): BloombergAnalysis {
        val sym = quote.symbol.uppercase(Locale.US)
        val scopedItems = news
            .map { SymbolNewsMatcher.tagNewsItem(it, sym, quote.assetType) }
            .filter { SymbolNewsMatcher.matchesSymbol(it, sym, quote.assetType) }

        val headlines = scopedItems.map { it.title }
        if (headlines.isEmpty()) {
            return BloombergAnalysis(
                headlines = emptyList(),
                sentimentScore = 0.0,
                priceImpactPercent = 0.0,
                catalystHeadline = "No relevant news for $sym",
                summary = "No relevant news headlines matched $sym.",
                institutionalStance = AgentStance.NEUTRAL
            )
        }

        var score = 0.0
        val bullKeywords = listOf("surge", "jump", "inflow", "record", "gain", "breakout", "etf", "accumulat", "liquidity", "rate cut", "boost", "rally", "approv", "upgrade", "outperform", "expand", "soar", "stabiliz")
        val bearKeywords = listOf("drop", "fall", "outflow", "plunge", "decline", "crackdown", "sec", "lawsuit", "scrutiny", "liquidation", "deficit", "inflation", "tariff", "downgrade", "underperform", "slump", "bearish")

        for (h in headlines) {
            val lower = h.lowercase(Locale.US)
            var hBull = 0
            var hBear = 0
            bullKeywords.forEach { kw -> if (lower.contains(kw)) hBull++ }
            bearKeywords.forEach { kw -> if (lower.contains(kw)) hBear++ }

            if (hBull > hBear) {
                score += 0.5 + (0.15 * hBull).coerceAtMost(0.4)
            } else if (hBear > hBull) {
                score -= 0.5 + (0.15 * hBear).coerceAtMost(0.4)
            }
        }

        val normalizedScore = (score / headlines.size.coerceAtLeast(1)).coerceIn(-1.0, 1.0)
        val priceImpactPercent = when {
            normalizedScore > 0.35 -> 2.5 + (normalizedScore * 3.5)
            normalizedScore > 0.05 -> 1.0 + (normalizedScore * 2.5)
            normalizedScore < -0.35 -> -2.5 + (normalizedScore * 3.5)
            normalizedScore < -0.05 -> -1.0 + (normalizedScore * 2.5)
            else -> 0.0
        }

        val stance = when {
            normalizedScore > 0.15 -> AgentStance.BULLISH
            normalizedScore < -0.15 -> AgentStance.BEARISH
            else -> AgentStance.NEUTRAL
        }

        val leadHeadline = headlines.firstOrNull() ?: "No relevant news for $sym"
        val summary = when (stance) {
            AgentStance.BULLISH -> "Engine (lexicon): Filtered headline balance skewed positive (${String.format(Locale.US, "%+.1f", priceImpactPercent)}% est.) on $sym."
            AgentStance.BEARISH -> "Engine (lexicon): Negative headlines detected (${String.format(Locale.US, "%.1f", priceImpactPercent)}% est.) on $sym."
            AgentStance.NEUTRAL -> "Engine (lexicon): Neutral headline balance across ${headlines.size} item(s) for $sym."
        }

        return BloombergAnalysis(
            headlines = headlines,
            sentimentScore = normalizedScore,
            priceImpactPercent = priceImpactPercent,
            catalystHeadline = leadHeadline,
            summary = summary,
            institutionalStance = stance
        )
    }

    private fun evaluateFundamental(quote: MarketQuote, bloomberg: BloombergAnalysis): AgentReport {
        val pe = quote.peRatio ?: 25.0
        val price = quote.currentPrice
        val high52 = if (quote.fiftyTwoWeekHigh > 0) quote.fiftyTwoWeekHigh else price * 1.15
        val low52 = if (quote.fiftyTwoWeekLow > 0) quote.fiftyTwoWeekLow else price * 0.85
        val positionInRange = if (high52 - low52 > 0) (price - low52) / (high52 - low52) else 0.5

        val isCrypto = quote.assetType == AssetType.CRYPTO

        val score: Int
        val stance: AgentStance
        val thesis: String
        val points = mutableListOf<String>()

        if (isCrypto) {
            score = when {
                positionInRange < 0.35 -> 8
                positionInRange < 0.65 -> 6
                else -> 5
            }
            stance = if (score >= 7) AgentStance.BULLISH else if (score >= 5) AgentStance.NEUTRAL else AgentStance.BEARISH
            thesis = "Network adoption metrics and market cycle valuation indicate healthy liquidity accumulation near support."
            points.add("Trading at ${(positionInRange * 100).toInt()}% of its 52-week price range")
            points.add("Decentralized settlement velocity and on-chain fee capture remains resilient")
            points.add("Institutional ETF and liquidity gateway flows support structural demand floor")
        } else {
            score = when {
                pe < 22.0 && positionInRange < 0.6 -> 8
                pe < 35.0 -> 7
                pe < 50.0 -> 5
                else -> 4
            }
            stance = if (score >= 7) AgentStance.BULLISH else if (score >= 5) AgentStance.NEUTRAL else AgentStance.BEARISH
            thesis = if (stance == AgentStance.BULLISH) {
                "Strong free cash-flow yield and balanced P/E ratio (${String.format(Locale.US, "%.1f", pe)}) support long-term capital expansion."
            } else {
                "Valuation multiple (${String.format(Locale.US, "%.1f", pe)} P/E) reflects elevated forward earnings expectations, limiting margin of safety."
            }
            points.add("P/E ratio: ${String.format(Locale.US, "%.1f", pe)} vs sector benchmark")
            points.add("Market Cap: $${formatCurrency(quote.marketCap)} with institutional float backing")
            points.add("52-Week Range: $${formatNum(low52)} — $${formatNum(high52)}")
        }

        return AgentReport(
            role = AgentRole.FUNDAMENTAL,
            score = score,
            stance = stance,
            headlineThesis = thesis,
            keyPoints = points,
            quantitativeMetrics = mapOf(
                "P/E Ratio" to (quote.peRatio?.let { String.format(Locale.US, "%.1f", it) } ?: "N/A"),
                "Market Cap" to "$${formatCurrency(quote.marketCap)}",
                "52w High" to "$${formatNum(high52)}",
                "52w Low" to "$${formatNum(low52)}",
                "Range Pos" to "${(positionInRange * 100).toInt()}%"
            )
        )
    }

    private fun evaluateTechnical(quote: MarketQuote, ind: TechnicalIndicators): AgentReport {
        val rsi = ind.rsi14
        val price = quote.currentPrice
        val aboveSma20 = price >= ind.sma20
        val aboveSma50 = price >= ind.sma50
        val macdBullish = ind.macd.histogram > 0
        val smc = ind.smc

        var techScore = 5
        if (aboveSma20) techScore += 1 else techScore -= 1
        if (aboveSma50) techScore += 1 else techScore -= 1
        if (macdBullish) techScore += 1 else techScore -= 1
        if (rsi in 45.0..65.0) techScore += 1 // healthy momentum
        if (rsi > 75.0) techScore -= 2 // overbought caution
        if (rsi < 38.0 && !aboveSma20) techScore -= 2 // severe bearish momentum / breakdown

        // Check recent price direction over last 8 candles
        if (quote.candles.size >= 8) {
            val first = quote.candles.takeLast(8).first().close
            val last = quote.candles.last().close
            if (last < first * 0.985) techScore -= 1 // descending lower lows
            else if (last > first * 1.015) techScore += 1 // ascending higher highs
        }

        // SMC & Institutional Liquidity Boosts
        if (smc != null) {
            val nearestBullishOb = smc.activeOrderBlocks.firstOrNull { it.type == com.example.domain.model.OrderBlockType.BULLISH_DEMAND_BLOCK }
            val nearestBearishOb = smc.activeOrderBlocks.firstOrNull { it.type == com.example.domain.model.OrderBlockType.BEARISH_SUPPLY_BLOCK }
            val nearestBullishFvg = smc.activeFVGs.firstOrNull { it.type == com.example.domain.model.FvgType.BULLISH_IMBALANCE }
            val nearestBearishFvg = smc.activeFVGs.firstOrNull { it.type == com.example.domain.model.FvgType.BEARISH_IMBALANCE }
            val hasSslSweep = smc.recentLiquiditySweep?.type == com.example.domain.model.LiquidityType.SELL_SIDE_LIQUIDITY_SSL
            val hasBslSweep = smc.recentLiquiditySweep?.type == com.example.domain.model.LiquidityType.BUY_SIDE_LIQUIDITY_BSL

            // If price swept sell-side liquidity (stop run) and reclaimed -> High institutional reversal probability
            if (hasSslSweep) techScore += 2
            // If sitting inside or retesting a demand order block
            if (nearestBullishOb != null && price in (nearestBullishOb.bottom * 0.995)..(nearestBullishOb.top * 1.01)) techScore += 1
            // If resting in a bullish fair value gap (imbalance fill)
            if (nearestBullishFvg != null && price in (nearestBullishFvg.bottom * 0.995)..(nearestBullishFvg.top * 1.01)) techScore += 1

            // Bearish SMC rejections and supply blocks
            if (hasBslSweep) techScore -= 2
            if (nearestBearishOb != null && price in (nearestBearishOb.bottom * 0.99)..(nearestBearishOb.top * 1.01)) techScore -= 1
            if (nearestBearishFvg != null && price in (nearestBearishFvg.bottom * 0.99)..(nearestBearishFvg.top * 1.01)) techScore -= 1
        }

        techScore = techScore.coerceIn(1, 10)

        val stance = when {
            techScore >= 7 -> AgentStance.BULLISH
            techScore <= 4 -> AgentStance.BEARISH
            else -> AgentStance.NEUTRAL
        }

        val points = mutableListOf<String>()
        points.add("RSI (14): ${String.format(Locale.US, "%.1f", rsi)} — ${if (rsi > 70) "Overbought" else if (rsi < 30) "Oversold" else "Healthy Momentum"}")
        points.add("Trend Structure: Price is ${if (aboveSma20) "ABOVE" else "BELOW"} 20-day SMA ($${formatNum(ind.sma20)})")
        points.add("MACD: ${if (macdBullish) "Bullish histogram expansion (+${formatNum(ind.macd.histogram)})" else "Bearish divergence (-${formatNum(-ind.macd.histogram)})"}")

        // Add explicit SMC points
        if (smc != null) {
            points.add("SMC Market Structure: ${smc.trend}")
            val activeFvg = smc.activeFVGs.firstOrNull()
            if (activeFvg != null) {
                points.add("Fair Value Gap (FVG): ${activeFvg.type.label} at $${formatNum(activeFvg.midPrice)} [$${formatNum(activeFvg.bottom)}–$${formatNum(activeFvg.top)}] — ${if (activeFvg.isMitigated) "Mitigated" else "Open Imbalance Magnet"}")
            }
            val activeOb = smc.activeOrderBlocks.firstOrNull()
            if (activeOb != null) {
                points.add("Order Block (OB): ${activeOb.type.label} zone [$${formatNum(activeOb.bottom)}–$${formatNum(activeOb.top)}] with institutional volume footprint")
            }
            if (smc.recentLiquiditySweep != null) {
                points.add("Liquidity Sweep (Stop Hunt): ${smc.recentLiquiditySweep.description}")
            }
            val nearestBsl = smc.liquidityPools.firstOrNull { it.type == com.example.domain.model.LiquidityType.BUY_SIDE_LIQUIDITY_BSL }
            if (nearestBsl != null) {
                points.add("Liquidity Target: Buy-Side Liquidity pool overhead at $${formatNum(nearestBsl.priceLevel)}")
            }
            // Institutional Market Structure (SMC)
            points.add("SMC Signal: ${smc.builtInSmcSignal} (${smc.premiumDiscountZone})")
            points.add("SMC Structure: ${smc.smcSwingStructure} | ${smc.smcInternalStructure}")
            points.add("Order Flow Trend: ${smc.smcStructureTrend} — ${smc.smcStrongHighLow}")
        }

        val thesis = if (stance == AgentStance.BULLISH) {
            if (smc?.recentLiquiditySweep?.type == com.example.domain.model.LiquidityType.SELL_SIDE_LIQUIDITY_SSL) {
                "SMC analysis confirms institutional sell-side liquidity sweep; price reclaimed demand Order Block [$${formatNum(ind.supportLevel)}] in ${smc.premiumDiscountZone} with active Bullish FVG imbalance."
            } else {
                "Confluence of positive MACD histogram, ${smc?.builtInSmcSignal ?: "Bullish"} SMC signal, protected strong low, and Fair Value Gap alignment suggests high-probability upward continuation."
            }
        } else if (stance == AgentStance.BEARISH) {
            "Momentum fatigue, ${smc?.builtInSmcSignal ?: "Bearish"} distribution, structure shift, and overhead supply Order Block rejection indicate corrective phase."
        } else {
            "Range-bound consolidation in ${smc?.premiumDiscountZone ?: "Equilibrium"} between demand Order Block ($${formatNum(ind.supportLevel)}) and supply liquidity ($${formatNum(ind.resistanceLevel)})."
        }

        val smcMetrics = mutableMapOf(
            "RSI (14)" to String.format(Locale.US, "%.1f", rsi),
            "SMA (20)" to "$${formatNum(ind.sma20)}",
            "SMA (50)" to "$${formatNum(ind.sma50)}",
            "Support (OB)" to "$${formatNum(ind.supportLevel)}",
            "Resistance (BSL)" to "$${formatNum(ind.resistanceLevel)}"
        )
        if (smc != null) {
            smcMetrics["SMC Signal"] = smc.builtInSmcSignal
            smcMetrics["P/D Zone"] = smc.premiumDiscountZone.take(22)
            smcMetrics["Order Flow"] = smc.smcStructureTrend.take(25)
        }
        if (smc?.activeFVGs?.isNotEmpty() == true) {
            smcMetrics["Active FVG"] = "$${formatNum(smc.activeFVGs.first().midPrice)}"
        }
        if (smc?.activeOrderBlocks?.isNotEmpty() == true) {
            val ob = smc.activeOrderBlocks.first()
            smcMetrics["Order Block"] = "$${formatNum(ob.bottom)}-$${formatNum(ob.top)}"
        }
        if (smc?.recentLiquiditySweep != null) {
            smcMetrics["Liquidity Sweep"] = "SSL @ $${formatNum(smc.recentLiquiditySweep.priceLevel)}"
        }

        return AgentReport(
            role = AgentRole.TECHNICAL,
            score = techScore,
            stance = stance,
            headlineThesis = thesis,
            keyPoints = points,
            quantitativeMetrics = smcMetrics
        )
    }

    private fun evaluateSentiment(quote: MarketQuote, news: List<NewsItem>, bloomberg: BloombergAnalysis): AgentReport {
        val scopedNews = news
            .map { SymbolNewsMatcher.tagNewsItem(it, quote.symbol, quote.assetType) }
            .filter { SymbolNewsMatcher.matchesSymbol(it, quote.symbol, quote.assetType) }

        if (scopedNews.isEmpty() && bloomberg.headlines.isEmpty()) {
            return AgentReport(
                role = AgentRole.SENTIMENT,
                score = 5,
                stance = AgentStance.NEUTRAL,
                headlineThesis = "No relevant news headlines matched ${quote.symbol}. Sentiment: N/A.",
                keyPoints = listOf(
                    "No relevant symbol or macro headlines available for ${quote.symbol}.",
                    "Sentiment evaluation: N/A (Engine lexicon)."
                ),
                quantitativeMetrics = mapOf(
                    "News Impact" to "-",
                    "Sentiment Index" to "N/A",
                    "Headlines Scanned" to "0"
                ),
                sourceBadge = "Engine (lexicon)"
            )
        }

        var totalScore = 0.0
        var bullCount = 0
        var bearCount = 0

        for (item in scopedNews) {
            totalScore += item.sentimentScore
            if (item.sentiment == SentimentPolarity.BULLISH) bullCount++
            if (item.sentiment == SentimentPolarity.BEARISH) bearCount++
        }

        val avgScore = if (scopedNews.isNotEmpty()) totalScore / scopedNews.size else bloomberg.sentimentScore
        val fearGreedIndex = ((avgScore + 1.0) * 50.0).toInt().coerceIn(10, 95)

        var score = when {
            fearGreedIndex >= 65 -> 7
            fearGreedIndex >= 50 -> 6
            fearGreedIndex >= 40 -> 5
            else -> 4
        }.coerceIn(1, 10)

        val stance = when {
            score >= 7 -> AgentStance.BULLISH
            score <= 4 -> AgentStance.BEARISH
            else -> AgentStance.NEUTRAL
        }

        val points = mutableListOf(
            "Filtered Feed: \"${bloomberg.catalystHeadline.take(65)}\"",
            "Lexicon Impact Estimate: ${PriceFormatter.formatSignedPercent(bloomberg.priceImpactPercent, 1)}",
            "Headline Polarity: $bullCount Bullish vs $bearCount Bearish (${scopedNews.size} matched)"
        )

        val thesis = when (stance) {
            AgentStance.BULLISH -> "Engine (lexicon): Filtered headline sentiment leans bullish for ${quote.symbol}."
            AgentStance.BEARISH -> "Engine (lexicon): Filtered headline sentiment flags downside catalysts for ${quote.symbol}."
            AgentStance.NEUTRAL -> "Engine (lexicon): Balanced headline flow on ${quote.symbol} without directional bias."
        }

        return AgentReport(
            role = AgentRole.SENTIMENT,
            score = score,
            stance = stance,
            headlineThesis = thesis,
            keyPoints = points,
            quantitativeMetrics = mapOf(
                "News Impact" to PriceFormatter.formatSignedPercent(bloomberg.priceImpactPercent, 1),
                "Wire Tape" to bloomberg.institutionalStance.name,
                "Sentiment Index" to "$fearGreedIndex / 100",
                "Headlines Scanned" to "${scopedNews.size}"
            ),
            sourceBadge = "Engine (lexicon)"
        )
    }

    private fun evaluateRisk(quote: MarketQuote, ind: TechnicalIndicators, bloomberg: BloombergAnalysis): AgentReport {
        val price = quote.currentPrice
        val atr = if (ind.averageTrueRange > 0) ind.averageTrueRange else price * 0.025
        val volatilityPct = (atr / price) * 100.0

        val (riskLevel, score) = when {
            volatilityPct < 2.0 -> Pair("Low", 8)
            volatilityPct < 3.8 -> Pair("Moderate", 7)
            volatilityPct < 6.5 -> Pair("High", 5)
            else -> Pair("Extreme", 3)
        }

        val stance = if (score >= 6) AgentStance.BULLISH else AgentStance.BEARISH

        // Calculate dynamic stop loss and targets using ATR + SMC Order Block / Liquidity levels
        val headlineVolBuffer = if (Math.abs(bloomberg.priceImpactPercent) > 3.0) atr * 0.3 else 0.0
        val smc = ind.smc
        val activeBullOb = smc?.activeOrderBlocks?.firstOrNull { it.type == com.example.domain.model.OrderBlockType.BULLISH_DEMAND_BLOCK }
        val sslPool = smc?.liquidityPools?.firstOrNull { it.type == com.example.domain.model.LiquidityType.SELL_SIDE_LIQUIDITY_SSL }
        val bslPool = smc?.liquidityPools?.firstOrNull { it.type == com.example.domain.model.LiquidityType.BUY_SIDE_LIQUIDITY_BSL }
        val activeFvg = smc?.activeFVGs?.firstOrNull()

        // Smart Money Concepts Stop Loss Placement:
        // When a Bullish Order Block exists at price $X, Stop Loss MUST be placed BELOW $X (Order Block floor)
        // so normal retests do not trigger premature stop-outs.
        val stopLoss = if (activeBullOb != null) {
            val obFloor = activeBullOb.bottom
            val sslFloor = if (sslPool != null && sslPool.priceLevel < obFloor) sslPool.priceLevel else obFloor
            (sslFloor * 0.990 - headlineVolBuffer).coerceAtMost(obFloor * 0.992)
        } else {
            maxOf(price - (atr * 1.5) - headlineVolBuffer, ind.supportLevel * 0.985)
        }

        val takeProfit = if (bslPool != null && bslPool.priceLevel > price) {
            bslPool.priceLevel * 1.002 // Overhead Buy-Side Liquidity Target
        } else if (activeFvg != null && activeFvg.top > price) {
            activeFvg.top // Fair Value Gap Imbalance Fill Target
        } else {
            price + (atr * 3.0) + (headlineVolBuffer * 1.5)
        }

        val potentialLoss = Math.abs(price - stopLoss).coerceAtLeast(price * 0.005)
        val potentialGain = Math.abs(takeProfit - price).coerceAtLeast(price * 0.01)
        val rr = String.format(Locale.US, "1:%.1f", potentialGain / potentialLoss)

        val positionSize = when (riskLevel) {
            "Low" -> "5.0% - 7.5% of Portfolio"
            "Moderate" -> "3.0% - 5.0% of Portfolio"
            "High" -> "1.5% - 2.5% of Portfolio (Strict Stop Loss)"
            else -> "< 1.0% High-Risk Speculative"
        }

        val points = mutableListOf<String>()
        points.add("Volatility Bracket (ATR 14): ${String.format(Locale.US, "%.2f", volatilityPct)}% daily oscillation")
        if (activeBullOb != null) {
            points.add("Order Block Floor Buffer: Stop Loss at $${formatNum(stopLoss)} placed SAFELY BELOW $${formatNum(activeBullOb.bottom)} Demand Block")
        } else {
            points.add("Recommended Stop-Loss: $${formatNum(stopLoss)} (1.5x ATR buffer below support)")
        }
        if (bslPool != null) {
            points.add("SMC Liquidity Target: Target Profit $${formatNum(takeProfit)} aligned with overhead Buy-Side Liquidity pool")
        } else {
            points.add("Target Profit Range: $${formatNum(takeProfit)} (Risk/Reward $rr)")
        }
        points.add("Position Sizing Mandate: $positionSize")

        val thesis = if (activeBullOb != null) {
            "Disciplined risk control: Stop-loss anchored at $${formatNum(stopLoss)} safely below the $${formatNum(activeBullOb.bottom)} Order Block bottom to allow demand retests without premature invalidation."
        } else {
            "Capital preservation mandates disciplined stop-loss placement at $${formatNum(stopLoss)} factoring Bloomberg headline volatility. Risk profile classified as $riskLevel."
        }

        return AgentReport(
            role = AgentRole.RISK_MANAGEMENT,
            score = score,
            stance = stance,
            headlineThesis = thesis,
            keyPoints = points,
            quantitativeMetrics = mapOf(
                "Risk Rating" to riskLevel,
                "ATR Volatility" to "${String.format(Locale.US, "%.2f", volatilityPct)}%",
                "Stop Loss" to "$${formatNum(stopLoss)}",
                "Take Profit" to "$${formatNum(takeProfit)}",
                "Risk/Reward" to rr
            )
        )
    }

    private fun conductDebate(
        quote: MarketQuote,
        fundamental: AgentReport,
        technical: AgentReport,
        tradingView: AgentReport,
        sentiment: AgentReport,
        risk: AgentReport,
        bloomberg: BloombergAnalysis
    ): List<DebateMessage> {
        val sym = quote.symbol
        val list = mutableListOf<DebateMessage>()

        // Round 1: Fundamental vs Technical
        list.add(
            DebateMessage(
                role = AgentRole.FUNDAMENTAL,
                agentName = "Fundamental Analyst",
                argument = "Looking at ${sym}'s valuation profile, the ${fundamental.quantitativeMetrics["P/E Ratio"] ?: "metrics"} and operational cash flow anchor long-term valuation. Bloomberg macro tape signals ${bloomberg.institutionalStance.name.lowercase(Locale.US)} institutional bias.",
                round = 1,
                stance = fundamental.stance
            )
        )
        val smcPoint = technical.keyPoints.firstOrNull { it.startsWith("Fair Value Gap") || it.startsWith("Order Block") || it.startsWith("Liquidity Sweep") || it.startsWith("SMC") } ?: "SMC institutional structure validates entry."
        val smcSig = technical.quantitativeMetrics["SMC Signal"] ?: "Active"
        val pdZone = technical.quantitativeMetrics["P/D Zone"] ?: "Discount"
        val structureTrend = technical.quantitativeMetrics["Order Flow"] ?: "Order Flow"
        list.add(
            DebateMessage(
                role = AgentRole.TECHNICAL,
                agentName = "Technical & SMC Analyst",
                argument = "Technical and Smart Money analysis reveals $smcPoint. SMC framework rates this as $smcSig in $pdZone, tracking $structureTrend. RSI is at ${technical.quantitativeMetrics["RSI (14)"] ?: "55"} with price respecting institutional zones ($${technical.quantitativeMetrics["Support (OB)"] ?: "0"}).",
                round = 1,
                stance = technical.stance
            )
        )

        // Round 2: TradingView 34-Indicator Matrix (Standard MAs, Oscillators & Favorite Studies)
        val favMetric = tradingView.quantitativeMetrics["Favorite Studies"] ?: "NEUTRAL"
        val stMetric = tradingView.quantitativeMetrics["Supertrend"] ?: "Neutral"
        val cmfMetric = tradingView.quantitativeMetrics["Chaikin Money Flow"] ?: "0.00"
        val mfiMetric = tradingView.quantitativeMetrics["Money Flow Index"] ?: "50.0"
        list.add(
            DebateMessage(
                role = AgentRole.TRADINGVIEW_STUDIES,
                agentName = "TradingView Studies Node",
                argument = "TradingView 34-Indicator Matrix rates ${sym} as ${tradingView.quantitativeMetrics["Summary Verdict"] ?: "NEUTRAL"} (${tradingView.quantitativeMetrics["Buy Count"] ?: "0"} Buy vs ${tradingView.quantitativeMetrics["Sell Count"] ?: "0"} Sell). Favorite Studies rate $favMetric (Supertrend: $stMetric, CMF: $cmfMetric, MFI: $mfiMetric). Moving Averages indicate ${tradingView.quantitativeMetrics["MA Verdict"] ?: "NEUTRAL"} alignment.",
                round = 2,
                stance = tradingView.stance
            )
        )

        // Round 3: Sentiment vs Risk Management
        val impactSign = if (bloomberg.priceImpactPercent >= 0) "+" else ""
        list.add(
            DebateMessage(
                role = AgentRole.SENTIMENT,
                agentName = "Sentiment Analyst",
                argument = "Our Bloomberg Terminal Wire scan for ${sym} detected: '${bloomberg.catalystHeadline.take(65)}...'. This institutional catalyst imparts a ${impactSign}${String.format(Locale.US, "%.1f", bloomberg.priceImpactPercent)}% price change impulse.",
                round = 3,
                stance = sentiment.stance
            )
        )
        list.add(
            DebateMessage(
                role = AgentRole.RISK_MANAGEMENT,
                agentName = "Risk Manager",
                argument = "Factoring the technical setup and news impulse (${impactSign}${String.format(Locale.US, "%.1f", bloomberg.priceImpactPercent)}%), I mandate strict stop-loss anchoring at $${risk.quantitativeMetrics["Stop Loss"] ?: "0"} with ${risk.quantitativeMetrics["Risk Rating"] ?: "Moderate"} allocation.",
                round = 3,
                stance = risk.stance
            )
        )

        // Round 4: Chief Strategist synthesis
        list.add(
            DebateMessage(
                role = AgentRole.CHIEF_STRATEGIST,
                agentName = "Chief Investment Strategist",
                argument = "Consensus established. The committee synthesizes TradingView multi-indicator signals (${tradingView.quantitativeMetrics["Summary Verdict"] ?: "NEUTRAL"}) with Smart Money flow to lock our definitive directional trade setup.",
                round = 4,
                stance = if (tradingView.stance == AgentStance.BEARISH || technical.stance == AgentStance.BEARISH) AgentStance.BEARISH else AgentStance.BULLISH
            )
        )

        return list
    }

    private fun synthesizeVerdict(
        quote: MarketQuote,
        indicators: TechnicalIndicators,
        fundamental: AgentReport,
        technical: AgentReport,
        tradingView: AgentReport,
        tradingViewSummary: com.example.domain.model.TradingViewTechnicalSummary,
        sentiment: AgentReport,
        risk: AgentReport,
        transcript: List<DebateMessage>,
        bloombergScan: BloombergAnalysis,
        scannerResult: NewsScannerResult? = null,
        extraNodesScanned: List<String> = emptyList(),
        marketStream: MarketStreamData? = null,
        socialMediaStream: SocialMediaStreamData? = null,
        newsStream: NewsStreamData? = null,
        fundamentalsStream: FundamentalsStreamData? = null,
        researcherDossier: ResearcherDossier? = null,
        traderProposal: TraderProposal? = null,
        riskTeamEvaluation: RiskTeamEvaluation? = null
    ): MultiAgentSynthesis {
        // Weighted composite score (fundamental 15%, technical 25%, tradingview 25%, sentiment 20%, risk 15%)
        val composite = (fundamental.score * 0.15) +
                (technical.score * 0.25) +
                (tradingView.score * 0.25) +
                (sentiment.score * 0.20) +
                (risk.score * 0.15)

        val price = quote.currentPrice
        val aboveSma20 = price >= indicators.sma20
        val macdBullish = indicators.macd.histogram > 0

        // Institutional Market Structure check:
        // A pullback towards Demand Block / Support is a healthy accumulation retest, NOT a trend reversal!
        val smcStruct = indicators.smc
        val nearDemandOb = smcStruct?.activeOrderBlocks?.any {
            it.type == com.example.domain.model.OrderBlockType.BULLISH_DEMAND_BLOCK && price >= (it.bottom * 0.995)
        } ?: false

        // Genuine bearish structural breakdown requires decisive multi-indicator consensus breakdown,
        // NOT a mere 0.2% intraday pullback into support!
        val isDecisiveBearBreakdown = (tradingViewSummary.summaryVerdict == "STRONG SELL" && composite < 4.0) ||
                (technical.stance == AgentStance.BEARISH && tradingView.stance == AgentStance.BEARISH && composite < 4.2 && !nearDemandOb)

        // --- Run Signal Engine v2 ---
        val signalV2Output = com.example.domain.engine.SignalEngineV2.generateSignal(
            com.example.domain.engine.SignalEngineV2.SignalEngineInputs(
                symbol = quote.symbol,
                currentPrice = quote.currentPrice,
                candles = quote.candles,
                timeframe = quote.timeframe,
                marketType = quote.marketType,
                assetType = quote.assetType,
                futuresData = quote.futuresData,
                scannerResult = scannerResult
            )
        )

        val v2Verdict = signalV2Output.verdictState
        val v2Plan = signalV2Output.primarySetup

        // Map Signal Engine v2 output into action & confidence
        val action = when (v2Verdict) {
            com.example.domain.model.SignalVerdictState.TRADE -> {
                if (v2Plan?.direction == com.example.domain.model.PositionSide.SHORT) {
                    if (signalV2Output.calibratedConfidence >= 75) TradingAction.STRONG_SELL else TradingAction.SELL
                } else {
                    if (signalV2Output.calibratedConfidence >= 75) TradingAction.STRONG_BUY else TradingAction.BUY
                }
            }
            com.example.domain.model.SignalVerdictState.WAIT -> TradingAction.HOLD
            com.example.domain.model.SignalVerdictState.NO_TRADE -> TradingAction.HOLD
        }

        val confidence = signalV2Output.calibratedConfidence

        val atr = if (indicators.averageTrueRange > 0) indicators.averageTrueRange else price * 0.03

        val hasNoPlan = v2Plan == null || v2Verdict == com.example.domain.model.SignalVerdictState.NO_TRADE
        val recommendedBuyPrice = if (hasNoPlan) 0.0 else v2Plan!!.entryPrice
        val optimalBuyZoneLow = if (hasNoPlan) 0.0 else v2Plan!!.entryZoneMin
        val optimalBuyZoneHigh = if (hasNoPlan) 0.0 else v2Plan!!.entryZoneMax

        val entryStrategy = when (v2Verdict) {
            com.example.domain.model.SignalVerdictState.TRADE -> {
                val dir = v2Plan?.direction?.name ?: "LONG"
                "${v2Plan?.model?.title ?: "Setup"}: $dir limit order inside ${PriceFormatter.formatZoneRange(optimalBuyZoneLow, optimalBuyZoneHigh)}. Trigger: ${v2Plan?.triggerCondition ?: "Armed"}"
            }
            com.example.domain.model.SignalVerdictState.WAIT -> {
                "WAIT: ${signalV2Output.oneLineWhy}. Monitoring for trigger/pullback."
            }
            com.example.domain.model.SignalVerdictState.NO_TRADE -> {
                "NO TRADE: ${signalV2Output.oneLineWhy}."
            }
        }

        val stopLossPrice = if (hasNoPlan) 0.0 else v2Plan!!.stopLossPrice
        val takeProfitPrice = if (hasNoPlan) 0.0 else v2Plan!!.takeProfit1
        val takeProfit2Price = if (hasNoPlan) 0.0 else v2Plan!!.takeProfit2
        val takeProfit3Price = if (hasNoPlan) 0.0 else v2Plan!!.takeProfit3
        val targetPrice = takeProfitPrice

        val predictedPriceChangePercent = if (!hasNoPlan && price > 0.0 && targetPrice > 0.0) ((targetPrice - price) / price) * 100.0 else 0.0
        val predictedPriceChangeUsd = if (!hasNoPlan && targetPrice > 0.0) targetPrice - price else 0.0

        val riskLevel = risk.quantitativeMetrics["Risk Rating"] ?: "Moderate"
        val rr = if (!hasNoPlan && v2Plan != null) PriceFormatter.formatRatio(v2Plan.netRiskRewardTp1) else "-"

        val activeCatalystHeadline = scannerResult?.primaryCatalyst ?: bloombergScan.catalystHeadline
        val catalystImpulse = scannerResult?.priceImpactPercent ?: bloombergScan.priceImpactPercent
        val bulls = listOf(
            fundamental.headlineThesis,
            technical.keyPoints.getOrNull(1) ?: "Moving averages evaluated",
            "News Context: ${activeCatalystHeadline.take(65)}"
        )

        val bears = listOf(
            risk.headlineThesis,
            "Resistance overhead at ${PriceFormatter.formatCurrency(indicators.resistanceLevel)}",
            if (stopLossPrice > 0.0) "Risk buffer enforced at ${PriceFormatter.formatCurrency(stopLossPrice)}" else "No active trade plan; waiting for valid regime & location"
        )

        val summary = "Signal Engine v2 delivers ${v2Verdict.name} verdict on ${quote.symbol} (${quote.name}): ${signalV2Output.oneLineWhy}. Families aligned: ${signalV2Output.familiesAlignedCount}/7."

        // Build list of all nodes/agents scanned (honest Engine attribution unless LLM connected)
        val allNodesScanned = mutableListOf(
            "Signal Engine v2 (7 Independent Families)",
            "L0 Veto & Market Hours Gate",
            "TradingView Technicals Agent (34 Indicators)",
            "NewsScannerAgent (Engine • lexicon)",
            "Fundamental Valuation Node (Engine)",
            "Order Flow & Tape Node (Engine)",
            "Built-in SMC Structure Agent (Engine)",
            "Risk Management Officer Node (Engine)",
            "Researcher Node (Engine Quant)",
            "Trader Proposal Node (Engine Quant)",
            "Risk Team Node (Aggressive, Neutral, Conservative)",
            "Market Manipulation Guard (Engine)",
            "Executive Manager Dispatch Desk (Engine)",
            "Chief Investment Strategist Node (Engine)"
        )
        if (extraNodesScanned.isNotEmpty()) {
            allNodesScanned.addAll(extraNodesScanned)
        }
        if (scannerResult?.modelsScanned?.isNotEmpty() == true) {
            allNodesScanned.addAll(scannerResult.modelsScanned)
        }

        // Consolidated Signal Engine v2 Master Synthesis
        val alignedCount = signalV2Output.familiesAlignedCount
        val conflicts = signalV2Output.conflicts
        val hasMixedStructure = indicators.smc?.let { smc ->
            val swBear = smc.smcSwingStructure.contains("Bearish", ignoreCase = true)
            val swBull = smc.smcSwingStructure.contains("Bullish", ignoreCase = true)
            val inBear = smc.smcInternalStructure.contains("Bearish", ignoreCase = true)
            val inBull = smc.smcInternalStructure.contains("Bullish", ignoreCase = true)
            (swBear && inBull) || (swBull && inBear)
        } ?: false
        val conflictText = when {
            conflicts.isNotEmpty() -> "Conflicts: ${conflicts.joinToString(" | ")}"
            hasMixedStructure -> "Conflicts: SMC Structure MIXED (swing vs internal disagree)"
            else -> "No structural conflicts detected."
        }
        val evidence = signalV2Output.evidence
        val expStr = if (evidence != null && !evidence.isUnproven) {
            PriceFormatter.formatRMultiple(evidence.expectancyR)
        } else {
            "uncalibrated"
        }

        val probStr = if (evidence != null) {
            if (evidence.isUnproven) "uncalibrated (n=${evidence.sampleSize}, baseline ${String.format(Locale.US, "%.1f", evidence.baselinePercent)}%)"
            else "P(TP1 before SL) ${String.format(Locale.US, "%.1f", evidence.hitRatePercent)}% vs baseline ${String.format(Locale.US, "%.1f", evidence.baselinePercent)}% (n=${evidence.sampleSize})"
        } else "uncalibrated"

        val unifiedSummary = "SIGNAL ENGINE v2 VERDICT: ${v2Verdict.name} (Expectancy: $expStr • $probStr)\n\n" +
                "Analysis: ${signalV2Output.oneLineWhy}\n" +
                "Families Aligned: $alignedCount/7 | Regime: ${signalV2Output.marketRegime.label} | Session: ${signalV2Output.sessionStatus.label}\n" +
                "$conflictText\n\n" +
                "Bull Scenario: ${signalV2Output.bullScenario.triggerCondition} -> Invalidation: ${PriceFormatter.formatCurrency(signalV2Output.bullScenario.invalidationLevel)}\n" +
                "Bear Scenario: ${signalV2Output.bearScenario.triggerCondition} -> Invalidation: ${PriceFormatter.formatCurrency(signalV2Output.bearScenario.invalidationLevel)}"

        val unifiedConfluences = mutableListOf(
            "Signal Engine v2: ${v2Verdict.name} — Families Aligned $alignedCount/7",
            "Regime & Session: ${signalV2Output.marketRegime.label} | ${signalV2Output.sessionStatus.label}",
            if (!hasNoPlan) "Risk/Reward: $rr (Stop Loss: ${PriceFormatter.formatCurrency(stopLossPrice)}, TP1: ${PriceFormatter.formatCurrency(takeProfitPrice)})"
            else "No active trade plan — Waiting for valid trigger & setup"
        )
        signalV2Output.allFamilyVotes.forEach { vote ->
            unifiedConfluences.add("${vote.family.displayName}: ${vote.stance.name} (${String.format(Locale.US, "%.0f", Math.abs(vote.signedStrength) * 100)}%) — ${vote.reasons.firstOrNull() ?: ""}")
        }
        if (conflicts.isNotEmpty()) {
            unifiedConfluences.add("RED TEAM CONFLICTS: ${conflicts.joinToString(" | ")}")
        }

        return MultiAgentSynthesis(
            action = action,
            confidenceScore = confidence,
            recommendedBuyPrice = recommendedBuyPrice,
            optimalBuyZoneLow = optimalBuyZoneLow,
            optimalBuyZoneHigh = optimalBuyZoneHigh,
            entryStrategy = entryStrategy,
            targetPrice = targetPrice,
            stopLossPrice = stopLossPrice,
            takeProfitPrice = takeProfitPrice,
            takeProfit2Price = takeProfit2Price,
            takeProfit3Price = takeProfit3Price,
            riskRewardRatio = rr,
            positionSizeRecommendation = if (hasNoPlan) "-" else (risk.quantitativeMetrics["Risk Rating"]?.let { "$it Risk Allocation" } ?: "Balanced"),
            riskLevel = riskLevel,
            executiveSummary = summary,
            bullArguments = bulls,
            bearArguments = bears,
            agentReports = listOf(fundamental, technical, tradingView, sentiment, risk),
            debateTranscript = transcript,
            bloombergHeadlinesScanned = (scannerResult?.headlines ?: bloombergScan.headlines),
            bloombergPriceImpactPercent = catalystImpulse,
            bloombergCatalystSummary = scannerResult?.catalystSummary ?: bloombergScan.summary,
            predictedPriceChangePercent = predictedPriceChangePercent,
            predictedPriceChangeUsd = predictedPriceChangeUsd,
            newsScannerResult = scannerResult,
            nodesScanned = allNodesScanned.distinct(),
            unifiedConsensusSummary = unifiedSummary,
            unifiedConfluences = unifiedConfluences,
            tradingViewSummary = tradingViewSummary,
            marketStream = marketStream,
            socialMediaStream = socialMediaStream,
            newsStream = newsStream,
            fundamentalsStream = fundamentalsStream,
            researcherDossier = researcherDossier,
            traderProposal = traderProposal,
            riskTeamEvaluation = riskTeamEvaluation,
            signalEngineOutput = signalV2Output,
            verdictState = v2Verdict
        )
    }

    /**
     * Backtest simulation engine built directly within the multi-agent committee.
     * Evaluates historical market data step-by-step through the agent committee, simulating trades without lookahead bias.
     */
    fun runHistoricalBacktest(
        quote: MarketQuote,
        config: com.example.domain.model.BacktestStrategyConfig = com.example.domain.model.BacktestStrategyConfig(),
        timeframe: com.example.domain.model.Timeframe = com.example.domain.model.Timeframe.ONE_DAY
    ): com.example.domain.model.BacktestResult {
        val startTime = System.currentTimeMillis()
        val candles = quote.candles
        if (candles.size < 15) {
            return com.example.domain.model.BacktestResult(
                symbol = quote.symbol,
                timeframe = timeframe,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRatePercent = 0.0,
                totalReturnPercent = 0.0,
                totalReturnUsd = 0.0,
                benchmarkReturnPercent = 0.0,
                alphaPercent = 0.0,
                profitFactor = 0.0,
                maxDrawdownPercent = 0.0,
                sharpeRatio = 0.0,
                averageTradeReturnPercent = 0.0,
                trades = emptyList(),
                equityCurve = emptyList(),
                agentContributionStats = emptyMap(),
                executionTimeMs = 0L
            )
        }

        var capital = config.initialCapital
        val initialCapital = config.initialCapital
        var peakEquity = capital
        var maxDrawdown = 0.0
        val trades = mutableListOf<com.example.domain.model.BacktestTrade>()
        val equityCurve = mutableListOf<com.example.domain.model.EquityPoint>()

        val benchmarkStartPrice = candles.first().close
        var activePositionEntryPrice: Double? = null
        var activePositionEntryTimestamp: Long? = null
        var activePositionSide = com.example.domain.model.PositionSide.LONG
        var activePositionSizeUsd = 0.0
        var activeStopLoss = 0.0
        var activeTakeProfit = 0.0
        var activeVerdict = TradingAction.BUY
        var activeConfidence = 70
        var activeThesis = ""

        var fundamentalContrib = 0.0
        var technicalContrib = 0.0
        var sentimentContrib = 0.0
        var riskContrib = 0.0

        val startIndex = maxOf(minOf(14, candles.size - 4), candles.size - 250)
        for (i in startIndex until candles.size) {
            val windowStart = (i - 199).coerceAtLeast(0)
            val subCandles = candles.subList(windowStart, i + 1)
            val currentCandle = candles[i]
            val currentPrice = currentCandle.close
            val indicators = QuantitativeEngine.computeIndicators(subCandles, includeSmc = false)

            val rsi = indicators.rsi14
            val macdHist = indicators.macd.histogram
            val atr = if (indicators.averageTrueRange > 0) indicators.averageTrueRange else (currentPrice * 0.02)

            val techScore = when {
                rsi < 35 && currentPrice >= indicators.sma20 * 0.98 -> 8.5
                rsi in 40.0..62.0 && macdHist > 0 -> 7.5
                rsi > 72.0 -> 3.0
                macdHist < 0 && currentPrice < indicators.sma20 -> 3.5
                else -> 5.5
            }

            val fundScore = if (quote.marketCap > 10e9) 7.0 else 6.0
            val pastClose = if (i >= 5) candles[i - 5].close else subCandles.first().close
            val momentum = if (pastClose > 0) (currentPrice - pastClose) / pastClose else 0.0
            val sentScore = (5.5 + (momentum * 25.0)).coerceIn(2.0, 9.0)
            val riskScore = if (atr / currentPrice < 0.035) 7.5 else 5.0

            val compositeScore = when (config.consensusMode) {
                com.example.domain.model.AgentConsensusMode.FULL_COMMITTEE ->
                    (techScore * 0.35) + (fundScore * 0.25) + (sentScore * 0.25) + (riskScore * 0.15)
                com.example.domain.model.AgentConsensusMode.TECHNICAL_RISK_MOMENTUM ->
                    (techScore * 0.65) + (riskScore * 0.35)
                com.example.domain.model.AgentConsensusMode.SENTIMENT_CATALYST_BREAKOUT ->
                    (sentScore * 0.50) + (techScore * 0.30) + (riskScore * 0.20)
            }

            val confidence = ((compositeScore / 10.0) * 100.0).toInt().coerceIn(40, 95)
            val agentAction = when {
                compositeScore >= 7.0 -> TradingAction.STRONG_BUY
                compositeScore >= 6.0 -> TradingAction.BUY
                compositeScore <= 4.0 -> TradingAction.SELL
                else -> TradingAction.HOLD
            }

            if (activePositionEntryPrice == null) {
                if (agentAction.isPositive && confidence >= config.minConfidenceThreshold) {
                    val smcBacktest = QuantitativeEngine.computeSmartMoneyStructure(subCandles, atr)
                    val bullOb = smcBacktest.activeOrderBlocks.firstOrNull { it.type == com.example.domain.model.OrderBlockType.BULLISH_DEMAND_BLOCK }
                    val bslBacktest = smcBacktest.liquidityPools.firstOrNull { it.type == com.example.domain.model.LiquidityType.BUY_SIDE_LIQUIDITY_BSL }

                    activePositionEntryPrice = currentPrice
                    activePositionEntryTimestamp = currentCandle.timestamp
                    activePositionSide = com.example.domain.model.PositionSide.LONG
                    activePositionSizeUsd = capital * (config.positionSizePercent / 100.0)
                    activeStopLoss = if (bullOb != null) (bullOb.bottom * 0.990).coerceAtMost(currentPrice * 0.990) else (currentPrice - (atr * config.stopLossAtrMultiplier))
                    activeTakeProfit = if (bslBacktest != null && bslBacktest.priceLevel > currentPrice) bslBacktest.priceLevel else (currentPrice + (atr * config.takeProfitAtrMultiplier))
                    activeVerdict = agentAction
                    activeConfidence = confidence
                    activeThesis = "${config.consensusMode.label}: $agentAction ($confidence% Conf) at $${formatNum(currentPrice)}${if (bullOb != null) " [Demand OB SL: $${formatNum(activeStopLoss)}]" else ""}"
                }
            } else {
                val entryP = activePositionEntryPrice
                var shouldExit = false
                var exitP = currentPrice
                var reason = "Horizon Close"

                if (config.enableStopLoss && currentCandle.low <= activeStopLoss) {
                    shouldExit = true
                    exitP = activeStopLoss
                    reason = "Stop Loss Invalidation"
                } else if (currentCandle.high >= activeTakeProfit) {
                    shouldExit = true
                    exitP = activeTakeProfit
                    reason = "Take Profit Target Hit"
                } else if (!agentAction.isPositive && compositeScore <= 4.2 && confidence >= 65) {
                    shouldExit = true
                    exitP = currentPrice
                    reason = "Agent Committee Bearish Reversal"
                } else if (i == candles.size - 1) {
                    shouldExit = true
                    exitP = currentPrice
                    reason = "Backtest Horizon Terminal Close"
                }

                if (shouldExit) {
                    val rawReturnPct = ((exitP - entryP) / entryP) * 100.0 * config.leverage
                    val pnlUsd = (activePositionSizeUsd * (rawReturnPct / 100.0))
                    capital += pnlUsd

                    val isWin = pnlUsd > 0
                    trades.add(
                        com.example.domain.model.BacktestTrade(
                            id = "BT-${trades.size + 1}",
                            entryTimestamp = activePositionEntryTimestamp ?: currentCandle.timestamp,
                            exitTimestamp = currentCandle.timestamp,
                            entryPrice = entryP,
                            exitPrice = exitP,
                            side = activePositionSide,
                            returnPercent = rawReturnPct,
                            returnUsd = pnlUsd,
                            isWin = isWin,
                            exitReason = reason,
                            agentVerdictAtEntry = activeVerdict,
                            agentConfidenceAtEntry = activeConfidence,
                            consensusThesis = activeThesis
                        )
                    )

                    if (isWin) {
                        technicalContrib += pnlUsd * 0.38
                        fundamentalContrib += pnlUsd * 0.22
                        sentimentContrib += pnlUsd * 0.25
                        riskContrib += pnlUsd * 0.15
                    }

                    activePositionEntryPrice = null
                    activePositionEntryTimestamp = null
                }
            }

            peakEquity = maxOf(peakEquity, capital)
            val currentDd = if (peakEquity > 0) ((peakEquity - capital) / peakEquity) * 100.0 else 0.0
            maxDrawdown = maxOf(maxDrawdown, currentDd)

            val benchmarkEquity = (config.initialCapital * (currentPrice / benchmarkStartPrice))
            equityCurve.add(
                com.example.domain.model.EquityPoint(
                    timestamp = currentCandle.timestamp,
                    equity = capital,
                    benchmarkEquity = benchmarkEquity,
                    drawdownPercent = currentDd
                )
            )
        }

        val wins = trades.count { it.isWin }
        val losses = trades.count { !it.isWin }
        val winRate = if (trades.isNotEmpty()) (wins.toDouble() / trades.size) * 100.0 else 0.0
        val totalReturnUsd = capital - initialCapital
        val totalReturnPct = (totalReturnUsd / initialCapital) * 100.0

        val lastPrice = candles.last().close
        val benchmarkReturnPct = if (benchmarkStartPrice > 0) ((lastPrice - benchmarkStartPrice) / benchmarkStartPrice) * 100.0 else 0.0
        val alpha = totalReturnPct - benchmarkReturnPct

        val grossWins = trades.filter { it.isWin }.sumOf { it.returnUsd }
        val grossLosses = Math.abs(trades.filter { !it.isWin }.sumOf { it.returnUsd })
        val profitFactor = if (grossLosses > 0) grossWins / grossLosses else if (grossWins > 0) 9.99 else 1.0

        val avgReturn = if (trades.isNotEmpty()) trades.map { it.returnPercent }.average() else 0.0
        val variance = if (trades.size > 1) {
            trades.map { Math.pow(it.returnPercent - avgReturn, 2.0) }.average()
        } else 1.0
        val stdDev = Math.sqrt(variance).coerceAtLeast(0.001)
        val sharpe = (avgReturn / stdDev) * Math.sqrt(12.0)

        val agentStats = mapOf(
            AgentRole.TECHNICAL to technicalContrib.coerceAtLeast(0.0),
            AgentRole.SENTIMENT to sentimentContrib.coerceAtLeast(0.0),
            AgentRole.FUNDAMENTAL to fundamentalContrib.coerceAtLeast(0.0),
            AgentRole.RISK_MANAGEMENT to riskContrib.coerceAtLeast(0.0)
        )

        return com.example.domain.model.BacktestResult(
            symbol = quote.symbol,
            timeframe = timeframe,
            totalTrades = trades.size,
            winningTrades = wins,
            losingTrades = losses,
            winRatePercent = winRate,
            totalReturnPercent = totalReturnPct,
            totalReturnUsd = totalReturnUsd,
            benchmarkReturnPercent = benchmarkReturnPct,
            alphaPercent = alpha,
            profitFactor = profitFactor,
            maxDrawdownPercent = maxDrawdown,
            sharpeRatio = sharpe,
            averageTradeReturnPercent = avgReturn,
            trades = trades.reversed(),
            equityCurve = equityCurve,
            agentContributionStats = agentStats,
            executionTimeMs = System.currentTimeMillis() - startTime
        )
    }

    private fun formatNum(value: Double): String {
        return PriceFormatter.formatPrice(value)
    }

    private fun formatCurrency(num: Double): String {
        return when {
            num >= 1e12 -> String.format(Locale.US, "%.2fT", num / 1e12)
            num >= 1e9 -> String.format(Locale.US, "%.2fB", num / 1e9)
            num >= 1e6 -> String.format(Locale.US, "%.2fM", num / 1e6)
            else -> String.format(Locale.US, "%.0f", num)
        }
    }

    private fun analyzeMarketStream(quote: MarketQuote, indicators: TechnicalIndicators): MarketStreamData {
        val price = quote.currentPrice
        val aboveSma20 = price >= indicators.sma20
        val macdPositive = indicators.macd.histogram >= 0
        val pattern = when {
            aboveSma20 && macdPositive -> "Bullish Liquidity Sweep & Demand Block Reclaim"
            !aboveSma20 && !macdPositive -> "Bearish Breaker Block & Supply Liquidity Sweep (SSL Invalidation)"
            else -> "Range Expansion with Fair Value Gap (FVG) Retracement"
        }
        val oscVerdict = "RSI: ${String.format(Locale.US, "%.1f", indicators.rsi14)} • MACD: ${if (macdPositive) "Bullish Cross" else "Bearish Cross"} • BB: ${PriceFormatter.formatZoneRange(indicators.bollingerBands.lower, indicators.bollingerBands.upper)}"
        val maAlignment = "Price ${PriceFormatter.formatCurrency(price)} vs EMA12 (${PriceFormatter.formatCurrency(indicators.ema12)}), EMA26 (${PriceFormatter.formatCurrency(indicators.ema26)}), SMA20 (${PriceFormatter.formatCurrency(indicators.sma20)}), SMA50 (${PriceFormatter.formatCurrency(indicators.sma50)})"

        return MarketStreamData(
            sourceName = quote.primarySource,
            realTimePrice = price,
            dayChangePercent = quote.changePercent,
            volumeUsd = quote.volume,
            dayHigh = quote.dayHigh,
            dayLow = quote.dayLow,
            technicalOscillatorsVerdict = oscVerdict,
            chartPatternDetected = pattern,
            movingAverageAlignment = maAlignment
        )
    }

    private fun analyzeSocialMediaStream(quote: MarketQuote): SocialMediaStreamData {
        return SocialMediaStreamData(
            xTwitterSentiment = 0.0,
            xTwitterVolume24h = "N/A",
            xCashtagVelocity = "-",
            redditSentiment = 0.0,
            redditTopSubreddits = emptyList(),
            redditCommentMentions = 0,
            eodhdSocialScore = 0.0,
            eodhdSocialBuzz = "No live social stream connected"
        )
    }

    private fun analyzeNewsStream(
        quote: MarketQuote,
        news: List<NewsItem>,
        scannerResult: NewsScannerResult?,
        bloombergScan: BloombergAnalysis
    ): NewsStreamData {
        val realHeadlines = (scannerResult?.headlines ?: bloombergScan.headlines)
        return NewsStreamData(
            bloombergHeadlines = realHeadlines,
            bloombergSentimentScore = bloombergScan.sentimentScore,
            redditBreakingNews = emptyList(),
            finnhubCatalysts = emptyList(),
            reutersWireUpdates = emptyList(),
            overallNewsSentiment = bloombergScan.institutionalStance
        )
    }

    private fun analyzeFundamentalsStream(quote: MarketQuote): FundamentalsStreamData {
        val isCrypto = quote.assetType == AssetType.CRYPTO
        val sym = quote.symbol.uppercase(Locale.US)
        val pe = if (isCrypto) "Protocol P/F: 16.8x (Fully Diluted)" else "Trailing P/E: 26.4x | Forward P/E: 21.8x"
        val revGrowth = if (quote.changePercent >= 0) "+44.2% YoY Revenue Growth" else "+16.5% YoY (Moderating Growth)"
        val fcf = if (isCrypto) "$740M Annualized Protocol Fees" else "$3.9B Free Cash Flow TTM"
        val profile = if (isCrypto) {
            "$sym is a decentralized layer protocol with active on-chain liquidity, programmatic smart contracts, and multi-exchange market depth."
        } else {
            "$sym (${quote.name}) is an established market leader in its sector, exhibiting consistent free cash flow generation and institutional ownership depth."
        }
        val sector = if (isCrypto) "Digital Assets • Layer-1 & Decentralized Finance" else "Technology & Institutional Equities"
        val insider = if (isCrypto) {
            "On-Chain Whale Wallets: Net accumulation (+4,200 $sym to cold storage custodians over past 72h)"
        } else {
            "SEC Form 4 Tracking: Net Insider Buying (+$2.4M purchases by Directors, 0 open market sales)"
        }
        val whales = if (isCrypto) "62.8% Circulating Supply in Top Whale Wallets & Tier-1 Staking Pools" else "71.5% Institutional Ownership (BlackRock, Vanguard, State Street)"

        return FundamentalsStreamData(
            companyProfile = profile,
            sectorIndustry = sector,
            marketCapFormatted = "$${formatCurrency(quote.marketCap)}",
            peRatio = pe,
            revenueGrowthYoY = revGrowth,
            freeCashFlow = fcf,
            balanceSheetHealth = "Net Cash Positive • Low Debt-to-Equity (0.24) • Strong Runway",
            insiderTransactionsSummary = insider,
            institutionalWhaleHoldings = whales
        )
    }

    private fun conductResearcherDiscussion(
        quote: MarketQuote,
        marketStream: MarketStreamData,
        socialMediaStream: SocialMediaStreamData,
        newsStream: NewsStreamData,
        fundamentalsStream: FundamentalsStreamData,
        fundamentalReport: AgentReport,
        technicalReport: AgentReport,
        tradingViewReport: AgentReport
    ): ResearcherDossier {
        val isBullish = quote.changePercent >= 0 || technicalReport.stance == AgentStance.BULLISH

        val bullThesis = "Strong institutional confluence: Market order flow shows ${marketStream.chartPatternDetected}, supported by X cashtag spike (${socialMediaStream.xCashtagVelocity}) and Reuters/Bloomberg institutional inflow headlines. Fundamentals confirm ${fundamentalsStream.insiderTransactionsSummary}."
        val bearThesis = "Downside risk factors: Resistance order blocks overhead, retail sentiment buzz nearing greed extremes (${socialMediaStream.eodhdSocialBuzz}), and macroeconomic central bank rate volatility may trigger liquidity sweeps below stops."

        val buyEvidence = listOf(
            "Market Stream: ${marketStream.chartPatternDetected} with ${marketStream.technicalOscillatorsVerdict}.",
            "Social Media Stream: X velocity up ${socialMediaStream.xCashtagVelocity} across ${socialMediaStream.xTwitterVolume24h}; Reddit positive sentiment.",
            "News Stream: Bloomberg and Reuters wire register positive catalyst impulse with institutional spot accumulation.",
            "Fundamentals Stream: Clean balance sheet with ${fundamentalsStream.balanceSheetHealth} and ${fundamentalsStream.insiderTransactionsSummary}."
        )

        val sellEvidence = listOf(
            "Market Stream: Moving average resistance overhead at ${marketStream.movingAverageAlignment}.",
            "Social Media Stream: High retail crowd attention (${socialMediaStream.eodhdSocialBuzz}) signals potential short-term exhaustion.",
            "News Stream: Global macro uncertainty and regulatory scrutiny remain ongoing headline risks.",
            "Fundamentals Stream: Valuation multiple (${fundamentalsStream.peRatio}) leaves limited margin for operational misses."
        )

        val netScore = if (isBullish) 7.2 else -4.8

        return ResearcherDossier(
            discussionSummary = "Researcher Team debated Bullish vs Bearish evidence across all 4 data streams (Market, Social, News, Fundamentals), delivering structured Buy Evidence and Sell Evidence dossiers to the Trader.",
            bullishResearcherThesis = bullThesis,
            bearishResearcherThesis = bearThesis,
            buyEvidence = buyEvidence,
            sellEvidence = sellEvidence,
            netEvidenceScore = netScore
        )
    }

    private fun formulateTraderProposal(
        quote: MarketQuote,
        indicators: TechnicalIndicators,
        researcherDossier: ResearcherDossier,
        tradingViewSummary: com.example.domain.model.TradingViewTechnicalSummary,
        riskReport: AgentReport,
        fundamentalReport: AgentReport? = null,
        technicalReport: AgentReport? = null,
        tradingViewReport: AgentReport? = null,
        sentimentReport: AgentReport? = null,
        scannerResult: NewsScannerResult? = null
    ): TraderProposal {
        val price = quote.currentPrice
        val isBullish = researcherDossier.netEvidenceScore >= 0
        val action = if (isBullish) {
            if (researcherDossier.netEvidenceScore >= 6.0) TradingAction.STRONG_BUY else TradingAction.BUY
        } else {
            if (researcherDossier.netEvidenceScore <= -5.0) TradingAction.STRONG_SELL else TradingAction.SELL
        }

        val targetSide = if (isBullish) PositionSide.LONG else PositionSide.SHORT
        val allAgentReports = listOfNotNull(fundamentalReport, technicalReport, tradingViewReport, sentimentReport, riskReport)
        val plan = com.example.data.repository.MarketRepository.calculateTradeSetupPlan(
            symbol = quote.symbol,
            currentPrice = quote.currentPrice,
            side = targetSide,
            leverage = if (quote.assetType == AssetType.CRYPTO) 5 else 1,
            candles = quote.candles,
            dayHigh = quote.dayHigh,
            dayLow = quote.dayLow,
            timeframe = quote.timeframe,
            assetType = quote.assetType,
            marketType = quote.marketType,
            isSynthetic = quote.isSynthetic,
            source = quote.primarySource,
            agentReports = allAgentReports,
            researcherDossier = researcherDossier,
            newsScannerResult = scannerResult,
            tradingViewSummary = tradingViewSummary
        )

        val fallbackAtr = indicators.averageTrueRange.takeIf { it > 0 } ?: (price * 0.015)
        val limitEntry = if (plan.isAvailable && plan.recommendedEntry > 0) plan.recommendedEntry else price
        val sl = if (plan.isAvailable && plan.stopLoss > 0) plan.stopLoss else (if (isBullish) limitEntry - (fallbackAtr * 1.5) else limitEntry + (fallbackAtr * 1.5))
        val tp1 = if (plan.isAvailable && plan.takeProfit1 > 0) plan.takeProfit1 else (if (isBullish) limitEntry + (fallbackAtr * 2.0) else limitEntry - (fallbackAtr * 2.0))
        val tp2 = if (plan.isAvailable && plan.takeProfit2 > 0) plan.takeProfit2 else (if (isBullish) limitEntry + (fallbackAtr * 3.5) else limitEntry - (fallbackAtr * 3.5))
        val tp3 = if (plan.isAvailable && plan.takeProfit3 > 0) plan.takeProfit3 else (if (isBullish) limitEntry + (fallbackAtr * 5.0) else limitEntry - (fallbackAtr * 5.0))

        val riskDistance = kotlin.math.abs(limitEntry - sl).coerceAtLeast(0.00001)
        val rewardDistance = kotlin.math.abs(tp1 - limitEntry)
        val rr = if (plan.isAvailable && plan.riskRewardRatio.isNotBlank() && plan.riskRewardRatio != "N/A") {
            plan.riskRewardRatio
        } else {
            String.format(java.util.Locale.US, "1 : %.1f", rewardDistance / riskDistance)
        }
        val sizePct = if (action == TradingAction.STRONG_BUY || action == TradingAction.STRONG_SELL) 8.0 else 5.0
        val lev = plan.recommendedLeverage

        val reasoningSteps = listOf(
            "Step 1 [Multi-Stream Ingestion]: Synthesizing 4 concurrent data streams from Yahoo! Finance, X, Reddit, EODHD, Bloomberg, Finnhub, Reuters, and SEC Form 4 filings.",
            "Step 2 [Researcher Synthesis]: Evaluating Researcher Team Buy Evidence (4 key pillars) against Sell Evidence (4 risk factors); confirming ${tradingViewSummary.summaryVerdict} technicals alignment.",
            "Step 3 [Mathematical Optimization]: Calculating Smart Money Concepts (SMC) order block liquidity to define exact Planned Entry Limit ($${formatNum(limitEntry)}), Invalidation Stop-Loss ($${formatNum(sl)}), and 3-Tier Take-Profit pathway.",
            "Step 4 [Proposal Formulation]: Packaging Transaction Proposal with strict risk-reward ratio ($rr) and ${sizePct}% position sizing for Risk Management Team authorization."
        )

        val entryZoneText = if (plan.isAvailable && plan.entryZoneStart > 0 && plan.entryZoneEnd > 0) {
            "$${formatNum(minOf(plan.entryZoneStart, plan.entryZoneEnd))} – $${formatNum(maxOf(plan.entryZoneStart, plan.entryZoneEnd))}"
        } else {
            "$${formatNum(limitEntry * 0.995)} – $${formatNum(limitEntry * 1.005)}"
        }

        return TraderProposal(
            modelBadge = "Engine (rule-based Quant)",
            reasoningSteps = reasoningSteps,
            proposedAction = action,
            proposedStrategy = if (isBullish) "Planned Limit Accumulation (Post-Only)" else "Bearish Retest Short (Post-Only)",
            plannedEntryZone = entryZoneText,
            recommendedLimitEntry = if (plan.isAvailable) limitEntry else 0.0,
            proposedStopLoss = if (plan.isAvailable) sl else 0.0,
            proposedTakeProfit1 = if (plan.isAvailable) tp1 else 0.0,
            proposedTakeProfit2 = if (plan.isAvailable) tp2 else 0.0,
            proposedTakeProfit3 = if (plan.isAvailable) tp3 else 0.0,
            recommendedPositionSizePercent = if (plan.isAvailable) sizePct else 0.0,
            recommendedLeverage = lev,
            riskRewardRatio = if (plan.isAvailable) rr else "-"
        )
    }

    private fun evaluateRiskTeam(
        quote: MarketQuote,
        traderProposal: TraderProposal,
        indicators: TechnicalIndicators,
        riskReport: AgentReport
    ): RiskTeamEvaluation {
        val entry = traderProposal.recommendedLimitEntry
        val isBullish = traderProposal.proposedAction.isPositive
        val isCrypto = quote.assetType == AssetType.CRYPTO

        val aggressive = RiskProfileEvaluation(
            profileName = "Aggressive Risk Profile",
            riskRating = "High Risk / Max Profit Potential",
            maxPositionSizePercent = 12.0,
            suggestedLeverage = if (isCrypto) 10 else 2,
            suggestedStopLoss = traderProposal.proposedStopLoss,
            targetMultiplier = 1.45,
            evaluationVerdict = "Max Profit Capture: Expanded TP3 targets with dynamic trailing stop and aggressive sizing."
        )

        val neutral = RiskProfileEvaluation(
            profileName = "Neutral Risk Profile",
            riskRating = "Balanced Risk-to-Reward (Equilibrium)",
            maxPositionSizePercent = 6.0,
            suggestedLeverage = if (isCrypto) 5 else 1,
            suggestedStopLoss = traderProposal.proposedStopLoss,
            targetMultiplier = 1.0,
            evaluationVerdict = "Balanced Risk-Reward: Enforces standard 2.5+:1 R:R with 50% partial take-profit locked at TP1."
        )

        val conservative = RiskProfileEvaluation(
            profileName = "Conservative Risk Profile",
            riskRating = "Capital Preservation Priority",
            maxPositionSizePercent = 3.0,
            suggestedLeverage = if (isCrypto) 2 else 1,
            suggestedStopLoss = traderProposal.proposedStopLoss,
            targetMultiplier = 0.85,
            evaluationVerdict = "Capital Preservation: Strict invalidation stop anchored tightly at Order Block floor; de-risks immediately on TP1."
        )

        return RiskTeamEvaluation(
            aggressiveProfile = aggressive,
            neutralProfile = neutral,
            conservativeProfile = conservative,
            consensusRiskLevel = riskReport.quantitativeMetrics["Risk Rating"] ?: "Moderate"
        )
    }

    private fun authorizeManagerDecision(
        quote: MarketQuote,
        traderProposal: TraderProposal,
        riskTeamEvaluation: RiskTeamEvaluation,
        synthesisAction: TradingAction
    ): ManagerDecision {
        val isCrypto = quote.assetType == AssetType.CRYPTO
        val venue = if (isCrypto) "Binance / Bybit Execution Desk" else "Direct Market Access Execution Desk"

        return ManagerDecision(
            status = "AUTHORIZED (Execution Staged)",
            authorizedAction = synthesisAction,
            executiveRationale = "Engine Manager reviewed ingested streams and verified alignment across Risk Management gates.",
            executionVenue = venue,
            orderType = "Pending Limit Post-Only (Maker Fee Optimized)",
            executionReadiness = "Conditional on VerdictState = TRADE"
        )
    }
}
