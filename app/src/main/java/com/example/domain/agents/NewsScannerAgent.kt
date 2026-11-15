package com.example.domain.agents

import android.util.Log
import com.example.data.api.MarketDataService
import com.example.domain.ai.MultiAiDispatcher
import com.example.domain.model.AgentStance
import com.example.domain.model.AiModelConfig
import com.example.domain.model.AssetType
import com.example.domain.model.NewsItem
import com.example.domain.model.NewsScannerResult
import com.example.domain.model.SentimentPolarity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * NewsScannerAgent
 * Uses web scraping and news RSS integrations to fetch real-time financial headlines
 * for any stock or crypto query, and passes them into the Gemini model context (or
 * broadcasts through all available AI fleet models simultaneously) for comprehensive
 * sentiment analysis, price catalyst projection, and institutional impact calibration.
 */
class NewsScannerAgent(
    private val apiService: MarketDataService = MarketDataService()
) {

    /**
     * Scans financial headlines for a stock or crypto, passes them into Gemini / fleet models,
     * and produces a structured sentiment and price decision verdict.
     *
     * @param query Ticker symbol or query (e.g. "NVDA", "BTC", "SOL", "AAPL")
     * @param assetType AssetType.STOCK or AssetType.CRYPTO
     * @param models Currently configured and enabled AI models
     * @param dispatcher MultiAiDispatcher for routing into Gemini or all models
     * @param broadcastAllModels If true, fans out prompt to every available model simultaneously
     */
    suspend fun scanHeadlinesAndAnalyze(
        query: String,
        assetType: AssetType = AssetType.STOCK,
        models: List<AiModelConfig> = emptyList(),
        dispatcher: MultiAiDispatcher? = null,
        broadcastAllModels: Boolean = true
    ): NewsScannerResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().uppercase(Locale.US)

        // 1. Fetch & scrape real-time financial headlines (Bloomberg Wire + Google/Yahoo Financial RSS)
        val newsItems = fetchHeadlines(cleanQuery, assetType)
        val headlines = newsItems.map { it.title }

        // 2. Perform algorithmic keyword sentiment calibration
        val (keywordScore, keywordStance, keywordImpact) = evaluateKeywords(headlines)

        // 3. Prepare prompt with headlines context for Gemini / AI models
        val prompt = buildAnalysisPrompt(cleanQuery, assetType, newsItems)

        val scannedModelNames = mutableListOf<String>()
        var aiSummary = ""
        var aiScore = keywordScore
        var aiImpact = keywordImpact

        var isLlmGenerated = false

        if (dispatcher != null && models.isNotEmpty()) {
            try {
                if (broadcastAllModels) {
                    val results = dispatcher.broadcastPromptToAllModels(
                        prompt = prompt,
                        models = models,
                        roleName = "NewsScannerAgent"
                    )

                    val successfulCalls = results.filter { it.success }
                    scannedModelNames.addAll(successfulCalls.map { "AI (${it.providerName} ${it.modelName})" })

                    if (successfulCalls.isNotEmpty()) {
                        isLlmGenerated = true
                        val firstValid = successfulCalls.first()
                        aiSummary = firstValid.response
                        val (parsedScore, parsedImpact) = parseAiSentimentResponse(firstValid.response)
                        if (parsedScore != null) aiScore = parsedScore
                        if (parsedImpact != null) aiImpact = parsedImpact
                    }
                } else {
                    val geminiResult = dispatcher.executeGeminiPrompt(prompt, models)
                    if (geminiResult.success) {
                        isLlmGenerated = true
                        scannedModelNames.add("AI (${geminiResult.providerName} ${geminiResult.modelName})")
                        aiSummary = geminiResult.response
                        val (parsedScore, parsedImpact) = parseAiSentimentResponse(geminiResult.response)
                        if (parsedScore != null) aiScore = parsedScore
                        if (parsedImpact != null) aiImpact = parsedImpact
                    }
                }
            } catch (e: Exception) {
                Log.w("NewsScannerAgent", "AI model dispatch exception: ${e.message}")
            }
        }

        if (scannedModelNames.isEmpty()) {
            scannedModelNames.add("Engine (lexicon)")
        }

        // 4. Combine score and determine final polarity & stance
        val finalScore = if (headlines.isEmpty() && !isLlmGenerated) {
            0.0
        } else if (isLlmGenerated) {
            ((keywordScore * 0.4) + (aiScore * 0.6)).coerceIn(-1.0, 1.0)
        } else {
            keywordScore.coerceIn(-1.0, 1.0)
        }

        val polarity = when {
            finalScore > 0.15 -> SentimentPolarity.BULLISH
            finalScore < -0.15 -> SentimentPolarity.BEARISH
            else -> SentimentPolarity.NEUTRAL
        }

        val stance = when (polarity) {
            SentimentPolarity.BULLISH -> AgentStance.BULLISH
            SentimentPolarity.BEARISH -> AgentStance.BEARISH
            SentimentPolarity.NEUTRAL -> AgentStance.NEUTRAL
        }

        val finalPriceImpact = if (headlines.isEmpty() && !isLlmGenerated) {
            0.0
        } else if (isLlmGenerated && aiImpact != 0.0) {
            (keywordImpact * 0.35) + (aiImpact * 0.65)
        } else {
            keywordImpact
        }

        val leadHeadline = headlines.firstOrNull() ?: "No relevant news for $cleanQuery"
        val fallbackSummary = if (headlines.isEmpty()) {
            "No relevant news headlines matched $cleanQuery ($assetType). Sentiment: N/A."
        } else {
            when (stance) {
                AgentStance.BULLISH -> "Engine (lexicon): Headline keyword balance skewed positive (+${String.format(Locale.US, "%.1f", finalPriceImpact)}% est.) on $cleanQuery."
                AgentStance.BEARISH -> "Engine (lexicon): Negative press headlines flagged (${String.format(Locale.US, "%.1f", finalPriceImpact)}% est.) on $cleanQuery."
                AgentStance.NEUTRAL -> "Engine (lexicon): Balanced headline flow on $cleanQuery without strong directional bias."
            }
        }

        val cleanSummary = if (isLlmGenerated && aiSummary.isNotBlank() && !aiSummary.contains("Fallback")) {
            aiSummary
        } else {
            fallbackSummary
        }

        val confidence = if (headlines.isEmpty() && !isLlmGenerated) {
            0
        } else {
            ((Math.abs(finalScore) * 50.0) + 50.0).toInt().coerceIn(50, 95)
        }

        NewsScannerResult(
            query = cleanQuery,
            headlines = headlines,
            newsItems = newsItems,
            sentimentPolarity = polarity,
            sentimentScore = finalScore,
            confidenceScore = confidence,
            priceImpactPercent = finalPriceImpact,
            primaryCatalyst = leadHeadline,
            catalystSummary = cleanSummary,
            modelsScanned = scannedModelNames,
            institutionalStance = stance,
            isLlmGenerated = isLlmGenerated
        )
    }

    /**
     * Fetches financial news strictly scoped by symbol and aliases; returns empty list when no matching news exists.
     */
    suspend fun fetchHeadlines(symbol: String, assetType: AssetType): List<NewsItem> = withContext(Dispatchers.IO) {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        val list = mutableListOf<NewsItem>()

        try {
            val generalNews = apiService.fetchFinancialNews(cleanSym)
            list.addAll(generalNews)
        } catch (e: Exception) {
            Log.w("NewsScannerAgent", "General news fetch error: ${e.message}")
        }

        try {
            val bloombergNews = apiService.fetchBloombergNews(cleanSym)
            list.addAll(bloombergNews)
        } catch (e: Exception) {
            Log.w("NewsScannerAgent", "Bloomberg news fetch error: ${e.message}")
        }

        list.map { com.example.domain.daytrader.news.SymbolNewsMatcher.tagNewsItem(it, cleanSym, assetType) }
            .filter { com.example.domain.daytrader.news.SymbolNewsMatcher.matchesSymbol(it, cleanSym, assetType) }
            .distinctBy { it.title }
            .take(10)
    }

    private fun evaluateKeywords(headlines: List<String>): Triple<Double, AgentStance, Double> {
        val bullKeywords = listOf("surge", "jump", "inflow", "record", "gain", "breakout", "etf", "accumulat", "liquidity", "rate cut", "boost", "rally", "approv", "upgrade", "outperform", "expand", "soar", "stabiliz", "beats", "revenue")
        val bearKeywords = listOf("drop", "fall", "outflow", "plunge", "decline", "crackdown", "sec", "lawsuit", "scrutiny", "liquidation", "deficit", "inflation", "tariff", "downgrade", "underperform", "slump", "bearish", "miss")

        var score = 0.0
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
            } else {
                score += 0.1
            }
        }

        val normalizedScore = (score / headlines.size.coerceAtLeast(1)).coerceIn(-1.0, 1.0)
        val stance = when {
            normalizedScore > 0.15 -> AgentStance.BULLISH
            normalizedScore < -0.15 -> AgentStance.BEARISH
            else -> AgentStance.NEUTRAL
        }

        val priceImpact = when {
            normalizedScore > 0.35 -> 2.5 + (normalizedScore * 3.5) // +3.7% to +6.0%
            normalizedScore > 0.05 -> 1.0 + (normalizedScore * 2.5) // +1.1% to +2.5%
            normalizedScore < -0.35 -> -2.5 + (normalizedScore * 3.5)
            normalizedScore < -0.05 -> -1.0 + (normalizedScore * 2.5)
            else -> 0.4
        }

        return Triple(normalizedScore, stance, priceImpact)
    }

    private fun buildAnalysisPrompt(query: String, assetType: AssetType, news: List<NewsItem>): String {
        val headlinesText = news.take(6).mapIndexed { idx, it ->
            "${idx + 1}. [${it.source}] ${it.title} (${it.pubDate})"
        }.joinToString("\n")

        return """
            You are the NewsScannerAgent in an institutional multi-agent financial trading framework.
            Query: $query ($assetType)
            
            Scanned Financial Headlines:
            $headlinesText
            
            Perform rigorous sentiment analysis and price catalyst projection:
            1. POLARITY: (BULLISH / BEARISH / NEUTRAL)
            2. SENTIMENT_SCORE: (-1.00 to +1.00)
            3. ESTIMATED_PRICE_IMPACT: (percentage, e.g. +4.2% or -3.1%)
            4. KEY_CATALYST: (Primary headline driving the decision)
            5. SUMMARY: (Concise 2-sentence institutional synthesis on headline momentum and order book effect)
        """.trimIndent()
    }

    private fun parseAiSentimentResponse(response: String): Pair<Double?, Double?> {
        var score: Double? = null
        var impact: Double? = null
        try {
            val lines = response.lines()
            for (line in lines) {
                val lower = line.lowercase(Locale.US)
                if (lower.contains("sentiment_score") || lower.contains("score:")) {
                    val num = extractDouble(line)
                    if (num != null && num in -1.0..1.0) {
                        score = num
                    }
                }
                if (lower.contains("price_impact") || lower.contains("impact:")) {
                    val num = extractDouble(line)
                    if (num != null && num in -25.0..25.0) {
                        impact = num
                    }
                }
            }
        } catch (_: Exception) {}
        return Pair(score, impact)
    }

    private fun extractDouble(text: String): Double? {
        val regex = Regex("[-+]?\\d*\\.?\\d+")
        val match = regex.find(text)
        return match?.value?.toDoubleOrNull()
    }
}
