package com.example.domain.daytrader.news

import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.model.MarketProfileType
import com.example.domain.model.NewsItem
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object DayTraderNewsEventEngine {

    /**
     * Tags a raw financial news headline into one of the 9 day-trader categories.
     */
    fun classifyHeadlineTag(title: String): HeadlineTag {
        val lower = title.lowercase(Locale.US)
        return when {
            lower.contains("earning") || lower.contains("eps") || lower.contains("q1") || lower.contains("q2") ||
                    lower.contains("q3") || lower.contains("q4") || lower.contains("quarterly report") -> HeadlineTag.EARNINGS

            lower.contains("guidance") || lower.contains("outlook") || lower.contains("forecast") ||
                    lower.contains("raises target") || lower.contains("lowers guidance") -> HeadlineTag.GUIDANCE

            lower.contains("acquire") || lower.contains("merger") || lower.contains("acquisition") ||
                    lower.contains("buyout") || lower.contains("takeover") || lower.contains("deal") -> HeadlineTag.DEAL_MA

            lower.contains("upgrade") || lower.contains("downgrade") || lower.contains("analyst") ||
                    lower.contains("price target") || lower.contains("initiates") || lower.contains("overweight") -> HeadlineTag.ANALYST_RATING

            lower.contains("8-k") || lower.contains("10-k") || lower.contains("10-q") ||
                    lower.contains("sec filing") || lower.contains("form 4") -> HeadlineTag.SEC_FILING

            lower.contains("lawsuit") || lower.contains("investigation") || lower.contains("sec") ||
                    lower.contains("doj") || lower.contains("subpoena") || lower.contains("court") ||
                    lower.contains("sanction") || lower.contains("regulatory") -> HeadlineTag.LEGAL_REGULATORY

            lower.contains("launch") || lower.contains("release") || lower.contains("chip") ||
                    lower.contains("patent") || lower.contains("fda approval") || lower.contains("product") -> HeadlineTag.PRODUCT

            lower.contains("cpi") || lower.contains("fed") || lower.contains("fomc") ||
                    lower.contains("interest rate") || lower.contains("inflation") || lower.contains("powell") ||
                    lower.contains("nonfarm") || lower.contains("treasury") -> HeadlineTag.MACRO

            lower.contains("list") || lower.contains("delist") || lower.contains("binance will") ||
                    lower.contains("launchpool") || lower.contains("airdrop") -> HeadlineTag.CRYPTO_LISTING

            else -> HeadlineTag.GENERAL
        }
    }

    /**
     * Formats raw news items with category tags, symbol tags, macro flag, and structured shock metadata.
     * Optionally filters strictly to [symbol] + macro headlines when [symbol] is non-blank.
     */
    fun processHeadlines(
        rawNews: List<NewsItem>,
        profile: MarketProfile,
        dayChangePercent: Double,
        nowTimestamp: Long = System.currentTimeMillis(),
        targetSymbol: String = "",
        assetType: com.example.domain.model.AssetType? = null
    ): List<TaggedHeadlineItem> {
        val df = SimpleDateFormat("HH:mm:ss z", Locale.US).apply { timeZone = profile.timeZone }
        val cleanSym = targetSymbol.trim().uppercase(Locale.US)

        val scopedNews = if (cleanSym.isNotBlank()) {
            rawNews.map { SymbolNewsMatcher.tagNewsItem(it) }
                .filter { SymbolNewsMatcher.matchesSymbol(it, cleanSym, assetType) }
        } else {
            rawNews.map { SymbolNewsMatcher.tagNewsItem(it) }
        }

        return scopedNews.map { item ->
            val tag = if (item.isMacro) HeadlineTag.MACRO else classifyHeadlineTag(item.title)
            val itemTs = try {
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(item.pubDate)?.time ?: nowTimestamp
            } catch (_: Exception) {
                nowTimestamp
            }
            val within2h = (nowTimestamp - itemTs) <= 2 * 3600 * 1000L
            val isStructured = item.isStructuredShockSource ||
                SymbolNewsMatcher.isStructuredShockSource(item.title, item.source)

            TaggedHeadlineItem(
                headline = item.title,
                tag = tag,
                timestamp = itemTs,
                timeFormatted = item.pubDate.ifBlank { df.format(itemTs) },
                source = item.source,
                movedTodayPercent = dayChangePercent,
                isShockEvent = within2h && isStructured,
                url = item.link,
                symbolTags = item.symbolTags,
                assetClassTag = item.assetClassTag,
                isMacro = item.isMacro,
                isStructuredShockSource = isStructured
            )
        }
    }

    /**
     * Computes trailing 7-day baseline headlines per 2h window for the symbol.
     * Minimum floor is 1.0 headlines/2h so quiet assets require >3 headlines/2h to surge.
     */
    fun computeTrailing7dRatePer2h(
        taggedHeadlines: List<TaggedHeadlineItem>,
        cleanSymbol: String,
        nowTimestamp: Long = System.currentTimeMillis()
    ): Double {
        val sevenDaysMs = 7L * 24L * 3600_000L
        val twoHoursMs = 2L * 3600_000L
        val historical = taggedHeadlines.filter {
            !it.isMacro &&
                it.timestamp in (nowTimestamp - sevenDaysMs) until (nowTimestamp - twoHoursMs) &&
                (it.symbolTags.isEmpty() || it.symbolTags.any { t -> t.equals(cleanSymbol, ignoreCase = true) })
        }
        // Observed baseline rate over the 7-day window (83 two-hour buckets before the current 2h window)
        val windows = 83.0
        val rawRate = historical.size.toDouble() / windows
        // Routine major crypto/megacap RSS feeds naturally deliver ~1-2 articles per 2h window; use >= 1.5 floor so normal tape doesn't false-trigger "8-Source Surge"
        val baselineFloor = 1.5
        return maxOf(baselineFloor, rawRate)
    }

    /**
     * Evaluates event risk for day trading:
     * - Symbol-scoped: unrelated-ticker headlines/filings (e.g. NVDA on a DOGE chart) are ignored.
     * - News shock requires a structured source (SEC 8-K, exchange halt/announcement for THIS symbol) OR
     *   headline rate > 3x trailing 7-day average across >= 3 distinct domains with consistent sentiment polarity.
     * - News shock emits CAUTION (size x0.5, buffer x1.5), never a hard NO TRADE veto.
     */
    fun assessEventRisk(
        symbol: String,
        profile: MarketProfile,
        taggedHeadlines: List<TaggedHeadlineItem>,
        recent8KFilings: List<Sec8KFiling> = emptyList(),
        earningsDateDaysAhead: Int? = null,
        nowTimestamp: Long = System.currentTimeMillis()
    ): EventRiskAssessment {
        val cleanSymbol = symbol.trim().uppercase(Locale.US)
            .removeSuffix("USDT")
            .removeSuffix("USD")
        val twoHoursAgo = nowTimestamp - 2 * 3600 * 1000L

        // Scope 8-K filings strictly to this symbol
        val recent8K = recent8KFilings.any {
            it.symbol.equals(cleanSymbol, ignoreCase = true) && it.timestamp >= twoHoursAgo
        }

        // Scope headlines strictly to this symbol (or macro)
        val scopedHeadlines = taggedHeadlines.filter {
            SymbolNewsMatcher.matchesTaggedItem(it, cleanSymbol)
        }
        val recentSymbolHeadlines = scopedHeadlines.filter {
            !it.isMacro && it.timestamp >= twoHoursAgo &&
                (it.symbolTags.isEmpty() || it.symbolTags.any { t -> t.equals(cleanSymbol, ignoreCase = true) })
        }

        val hasStructuredShockHeadline = recentSymbolHeadlines.any {
            (it.isStructuredShockSource || SymbolNewsMatcher.isStructuredShockSource(it.headline, it.source)) &&
                (it.symbolTags.any { t -> t.equals(cleanSymbol, ignoreCase = true) } ||
                    SymbolNewsMatcher.extractSymbolTags(it.headline, null).any { t -> t.equals(cleanSymbol, ignoreCase = true) })
        }
        val distinctDomainsIn2h = recentSymbolHeadlines
            .map { extractDomainOrSourceKey(it.url.orEmpty(), it.source) }
            .filter { it.isNotBlank() }
            .distinct()
            .size

        val trailingBaselineRatePer2h = computeTrailing7dRatePer2h(scopedHeadlines, cleanSymbol, nowTimestamp)
        val rateExceedsBaseline3x = recentSymbolHeadlines.size.toDouble() > (3.0 * trailingBaselineRatePer2h)
        val hasConsistentPolarity = checkConsistentHeadlinePolarity(recentSymbolHeadlines)

        val hasMultiSourceSurge = distinctDomainsIn2h >= 3 && rateExceedsBaseline3x && hasConsistentPolarity
        val hasNewsShock = recent8K || hasStructuredShockHeadline || hasMultiSourceSurge

        val shockSourceType = when {
            recent8K -> "SEC 8-K"
            hasStructuredShockHeadline -> "Exchange Halt / Structured Filing"
            hasMultiSourceSurge -> "$distinctDomainsIn2h-Domain Aligned Surge (>3x baseline)"
            else -> null
        }

        // Check earnings proximity (within 5 trading days)
        val isEarningsClose = profile.type == MarketProfileType.US_EQUITY &&
            earningsDateDaysAhead != null && earningsDateDaysAhead in 0..5

        val sizeMult = if (hasNewsShock) 0.5 else 1.0
        val bufferMult = if (hasNewsShock) 1.5 else 1.0

        val summary = when {
            hasNewsShock && isEarningsClose ->
                "CAUTION — NEWS SHOCK ($shockSourceType) & EARNINGS: $cleanSymbol event within 2h (Size x0.5, Buffer x1.5). Earnings in $earningsDateDaysAhead days."
            hasNewsShock ->
                "CAUTION — NEWS SHOCK ($shockSourceType): $cleanSymbol shock within 2h (Size x0.5, Buffer x1.5)."
            scopedHeadlines.any { it.isMacro } && isEarningsClose ->
                "MACRO EVENT & EARNINGS ($earningsDateDaysAhead d): Monitor macro release; flat before close."
            isEarningsClose ->
                "UPCOMING EARNINGS ($earningsDateDaysAhead days ahead): Day trade allowed only if flat before close."
            scopedHeadlines.any { it.isMacro } ->
                "EVENT RISK NORMAL (Macro tape tagged macro • No $cleanSymbol shock)."
            else ->
                "EVENT RISK NORMAL: Baseline news velocity for $cleanSymbol (rate ${recentSymbolHeadlines.size}/2h vs ${String.format(Locale.US, "%.1f", trailingBaselineRatePer2h * 3.0)} threshold)."
        }

        return EventRiskAssessment(
            hasNewsShock = hasNewsShock,
            isEarningsWithin5Days = isEarningsClose,
            requiresFlatBeforeClose = isEarningsClose,
            positionSizeMultiplier = sizeMult,
            bufferMultiplier = bufferMult,
            vetoReason = null,
            summaryText = summary,
            symbol = cleanSymbol,
            shockSourceType = shockSourceType
        )
    }

    private fun extractDomainOrSourceKey(url: String, source: String): String {
        val cleanUrl = url.trim().lowercase(Locale.US)
        if (cleanUrl.startsWith("http")) {
            val host = cleanUrl.substringAfter("://").substringBefore("/").removePrefix("www.")
            if (host.isNotBlank() && !host.contains("news.google.com")) return host
        }
        return source.trim().lowercase(Locale.US)
    }

    private fun checkConsistentHeadlinePolarity(headlines: List<TaggedHeadlineItem>): Boolean {
        if (headlines.size < 3) return false
        var posCount = 0
        var negCount = 0
        for (item in headlines) {
            val lower = item.headline.lowercase(Locale.US)
            val isNeg = lower.contains("halt") || lower.contains("hack") || lower.contains("exploit") ||
                lower.contains("lawsuit") || lower.contains("sec") || lower.contains("plunge") ||
                lower.contains("crash") || lower.contains("drop") || lower.contains("ban") ||
                lower.contains("delist") || lower.contains("miss") || lower.contains("cut") ||
                lower.contains("downgrade") || lower.contains("fraud") || lower.contains("default")
            val isPos = lower.contains("approve") || lower.contains("etf") || lower.contains("surge") ||
                lower.contains("beat") || lower.contains("raise") || lower.contains("upgrade") ||
                lower.contains("partnership") || lower.contains("acquire") || lower.contains("launch") ||
                lower.contains("breakout") || lower.contains("rally")
            if (isNeg && !isPos) negCount++
            else if (isPos && !isNeg) posCount++
        }
        val polarized = posCount + negCount
        if (polarized < 2) return false
        val majority = maxOf(posCount, negCount)
        return majority.toDouble() / polarized.toDouble() >= 0.70
    }
}
