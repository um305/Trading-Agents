package com.example.domain.daytrader.news

enum class HeadlineTag(val label: String, val badgeColorHex: Long) {
    EARNINGS("Earnings", 0xFF10B981),
    GUIDANCE("Guidance", 0xFF06B6D4),
    DEAL_MA("Deal / M&A", 0xFF8B5CF6),
    ANALYST_RATING("Analyst Rating", 0xFF3B82F6),
    SEC_FILING("SEC Filing", 0xFFF59E0B),
    LEGAL_REGULATORY("Legal / Regulatory", 0xFFEF4444),
    PRODUCT("Product", 0xFF14B8A6),
    MACRO("Macro", 0xFFEC4899),
    CRYPTO_LISTING("Listing / Delisting", 0xFFF97316),
    GENERAL("General Market", 0xFF6B7280)
}

data class TaggedHeadlineItem(
    val headline: String,
    val tag: HeadlineTag,
    val timestamp: Long,
    val timeFormatted: String,
    val source: String,
    val movedTodayPercent: Double,
    val isShockEvent: Boolean = false,
    val url: String? = null,
    val symbolTags: List<String> = emptyList(),
    val assetClassTag: String = "GENERAL",
    val isMacro: Boolean = false,
    val isStructuredShockSource: Boolean = false
)

data class Sec8KFiling(
    val symbol: String,
    val filingDate: String,
    val timestamp: Long,
    val items: List<String>,
    val description: String,
    val isWithinTwoHours: Boolean
)

data class EventRiskAssessment(
    val hasNewsShock: Boolean, // structured 8-K/halt OR >=3 tagged headlines within 2h from distinct sources
    val isEarningsWithin5Days: Boolean,
    val requiresFlatBeforeClose: Boolean,
    val positionSizeMultiplier: Double = 1.0, // 0.5 if news shock
    val bufferMultiplier: Double = 1.0,       // 1.5 if news shock
    val vetoReason: String? = null,
    val summaryText: String,
    val symbol: String = "",
    val shockSourceType: String? = null
)
