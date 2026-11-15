package com.example.domain.daytrader.news

import com.example.domain.model.AssetType
import com.example.domain.model.NewsItem
import java.util.Locale

/**
 * Deterministic symbol/asset-class tagger and matcher for headlines & event alerts.
 * Prevents cross-symbol leakage (e.g., an NVDA 8-K or halt headline never leaks onto DOGE).
 */
object SymbolNewsMatcher {

    private val SYMBOL_ALIASES: Map<String, List<String>> = mapOf(
        "DOGE" to listOf("doge", "dogecoin"),
        "SOL" to listOf("sol", "solana"),
        "BTC" to listOf("btc", "bitcoin"),
        "ETH" to listOf("eth", "ethereum", "ether"),
        "XRP" to listOf("xrp", "ripple"),
        "ADA" to listOf("ada", "cardano"),
        "BNB" to listOf("bnb", "binance coin"),
        "AVAX" to listOf("avax", "avalanche"),
        "LINK" to listOf("link", "chainlink"),
        "SUI" to listOf("sui"),
        "SHIB" to listOf("shib", "shiba inu"),
        "PEPE" to listOf("pepe"),
        "NVDA" to listOf("nvda", "nvidia"),
        "SNDK" to listOf("sndk", "sandisk", "western digital"),
        "TSLA" to listOf("tsla", "tesla"),
        "AAPL" to listOf("aapl", "apple"),
        "MSFT" to listOf("msft", "microsoft"),
        "AMZN" to listOf("amzn", "amazon"),
        "GOOGL" to listOf("googl", "goog", "alphabet", "google"),
        "META" to listOf("meta"),
        "AMD" to listOf("amd", "advanced micro devices"),
        "SPY" to listOf("spy", "s&p 500", "s&p500"),
        "QQQ" to listOf("qqq", "nasdaq 100", "nasdaq-100")
    )

    private val MACRO_KEYWORDS = listOf(
        "fomc", "federal reserve", "powell", "cpi", "inflation", "pce",
        "nonfarm", "payroll", "interest rate", "rate cut", "rate hike",
        "treasury yield", "gdp report", "central bank", "ecb rate", "boj rate"
    )

    private val STRUCTURED_SHOCK_KEYWORDS = listOf(
        "8-k", "form 8-k", "sec filing", "trading halt", "halted", "circuit breaker",
        "luld halt", "exchange halt", "exchange announcement", "delisting notice"
    )

    fun extractSymbolTags(text: String, hintSymbol: String? = null): List<String> {
        val lower = text.lowercase(Locale.US)
        val tags = mutableSetOf<String>()

        SYMBOL_ALIASES.forEach { (sym, aliases) ->
            val matched = aliases.any { alias ->
                val regex = Regex("\\b${Regex.escape(alias)}\\b")
                regex.containsMatchIn(lower)
            }
            if (matched) tags.add(sym)
        }

        // Also match explicit uppercase $TICKER or NASDAQ:TICKER patterns
        val cashtagRegex = Regex("\\$([A-Z]{2,6})\\b")
        cashtagRegex.findAll(text).forEach { match ->
            tags.add(match.groupValues[1].uppercase(Locale.US))
        }

        if (hintSymbol != null && hintSymbol.isNotBlank()) {
            val cleanHint = hintSymbol.uppercase(Locale.US)
            val hintRegex = Regex("\\b${Regex.escape(cleanHint.lowercase(Locale.US))}\\b")
            if (hintRegex.containsMatchIn(lower)) {
                tags.add(cleanHint)
            }
        }

        return tags.toList()
    }

    fun isMacroHeadline(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        return MACRO_KEYWORDS.any { kw -> lower.contains(kw) }
    }

    fun isStructuredShockSource(title: String, source: String): Boolean {
        val combined = "$title $source".lowercase(Locale.US)
        val hasKeyword = STRUCTURED_SHOCK_KEYWORDS.any { kw -> combined.contains(kw) }
        val isOfficialWire = source.contains("SEC", ignoreCase = true) ||
            source.contains("EDGAR", ignoreCase = true) ||
            source.contains("NYSE", ignoreCase = true) ||
            source.contains("NASDAQ", ignoreCase = true) ||
            source.contains("Exchange", ignoreCase = true)
        return hasKeyword || (isOfficialWire && (combined.contains("halt") || combined.contains("8-k")))
    }

    private val STOCK_CONTEXT_KEYWORDS = listOf(
        "sec filing", "8-k", "10-k", "10-q", "nasdaq", "nyse", "wall street",
        "earnings call", "quarterly earnings", "eps ", "dividend", "guidance"
    )

    private val CRYPTO_CONTEXT_KEYWORDS = listOf(
        "blockchain", "mainnet", "defi", "token", "altcoin", "binance",
        "coinbase", "perpetual", "funding rate", "on-chain", "crypto"
    )

    fun tagNewsItem(item: NewsItem, hintSymbol: String? = null, assetType: AssetType? = null): NewsItem {
        val extracted = extractSymbolTags(item.title, hintSymbol)
        val mergedTags = (item.symbolTags + extracted).map { it.uppercase(Locale.US) }.distinct()
        val macro = item.isMacro || (isMacroHeadline(item.title) && mergedTags.isEmpty())
        val structured = item.isStructuredShockSource || isStructuredShockSource(item.title, item.source)
        val lowerCombined = "${item.title} ${item.source}".lowercase(Locale.US)
        val inferredAssetClass = when {
            macro -> "MACRO"
            item.assetClassTag == "STOCK" || item.assetClassTag == "CRYPTO" -> item.assetClassTag
            STOCK_CONTEXT_KEYWORDS.any { lowerCombined.contains(it) } -> "STOCK"
            CRYPTO_CONTEXT_KEYWORDS.any { lowerCombined.contains(it) } -> "CRYPTO"
            assetType != null -> assetType.name
            else -> "GENERAL"
        }
        return item.copy(
            symbolTags = mergedTags,
            assetClassTag = inferredAssetClass,
            isMacro = macro,
            isStructuredShockSource = structured
        )
    }

    fun matchesSymbol(item: NewsItem, targetSymbol: String, targetAssetType: AssetType? = null): Boolean {
        val cleanTarget = targetSymbol.trim().uppercase(Locale.US)
            .removeSuffix("USDT")
            .removeSuffix("USD")
            .removeSuffix("-PERP")
            .removeSuffix("PERP")
        val tagged = tagNewsItem(item)
        if (tagged.isMacro) return true

        // Enforce strict asset-class separation when not macro
        if (targetAssetType != null) {
            if (targetAssetType == AssetType.CRYPTO && tagged.assetClassTag == "STOCK") return false
            if (targetAssetType == AssetType.STOCK && tagged.assetClassTag == "CRYPTO") return false
        }

        if (tagged.symbolTags.isNotEmpty()) {
            return tagged.symbolTags.any { it.equals(cleanTarget, ignoreCase = true) }
        }
        // Strict whole-word alias match (never substring match like "SOL" inside "Solutions")
        val aliases = SYMBOL_ALIASES[cleanTarget] ?: listOf(cleanTarget.lowercase(Locale.US))
        val lower = item.title.lowercase(Locale.US)
        return aliases.any { alias ->
            Regex("\\b${Regex.escape(alias)}\\b").containsMatchIn(lower)
        }
    }

    fun matchesTaggedItem(item: TaggedHeadlineItem, targetSymbol: String): Boolean {
        val cleanTarget = targetSymbol.trim().uppercase(Locale.US)
            .removeSuffix("USDT")
            .removeSuffix("USD")
        if (item.isMacro) return true
        if (item.symbolTags.isNotEmpty()) {
            return item.symbolTags.any { it.equals(cleanTarget, ignoreCase = true) }
        }
        val extracted = extractSymbolTags(item.headline, cleanTarget)
        if (extracted.isNotEmpty()) {
            return extracted.any { it.equals(cleanTarget, ignoreCase = true) }
        }
        val aliases = SYMBOL_ALIASES[cleanTarget] ?: listOf(cleanTarget.lowercase(Locale.US))
        val lower = item.headline.lowercase(Locale.US)
        return aliases.any { alias ->
            Regex("\\b${Regex.escape(alias)}\\b").containsMatchIn(lower)
        }
    }
}
