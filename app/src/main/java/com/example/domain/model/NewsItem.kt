package com.example.domain.model

enum class SentimentPolarity {
    BULLISH,
    NEUTRAL,
    BEARISH
}

data class NewsItem(
    val title: String,
    val source: String,
    val pubDate: String,
    val link: String,
    val sentiment: SentimentPolarity,
    val sentimentScore: Double, // -1.0 to +1.0
    val symbolTags: List<String> = emptyList(),
    val assetClassTag: String = "GENERAL",
    val isMacro: Boolean = false,
    val isStructuredShockSource: Boolean = false
)
