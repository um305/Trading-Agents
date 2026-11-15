package com.example.domain.model

enum class AssetType {
    STOCK,
    CRYPTO
}

enum class CryptoExchange(
    val id: String,
    val displayName: String,
    val takerFeePercent: Double,
    val colorHex: Long,
    val defaultSpreadBps: Double
) {
    BINANCE("binance", "Binance", 0.04, 0xFFF0B90B, 0.0),
    BYBIT("bybit", "Bybit", 0.055, 0xFFFF9800, 0.03),
    MEXC("mexc", "MEXC Global", 0.02, 0xFF2EB774, -0.02),
    OKX("okx", "OKX", 0.05, 0xFF3D72DE, 0.01),
    COINBASE("coinbase", "Coinbase Pro", 0.60, 0xFF0052FF, 0.05)
}

data class ExchangePriceQuote(
    val exchange: CryptoExchange,
    val price: Double,
    val change24h: Double,
    val volume24hUsd: Double,
    val spreadPercent: Double,
    val fundingRate: Double? = null,
    val latencyMs: Long = 28,
    val bidPrice: Double? = null,
    val askPrice: Double? = null,
    val fetchedAtMs: Long = System.currentTimeMillis(),
    val isStale: Boolean = false,
    val unavailableReason: String? = null
)

enum class PositionSide(val label: String) {
    LONG("LONG / BUY"),
    SHORT("SHORT / SELL")
}

enum class EntryOrderType(val label: String) {
    LIMIT("LIMIT"),
    MARKET_NOW("MARKET NOW"),
    WAIT_NO_TRADE("WAIT / NO TRADE")
}

enum class TradePlanLifecycleStatus(val label: String) {
    ANALYZING("ANALYZING AGENT DATA..."),
    PENDING_ENTRY("PENDING LIMIT ENTRY"),
    IN_ENTRY_ZONE("IN ENTRY ZONE (FILLABLE)"),
    ACTIVE_IN_TRADE("TRADE ACTIVE (WORKING TO TP1)"),
    TP1_REACHED("TP1 HIT — SECURE PROFITS & TRAIL SL"),
    TP2_REACHED("TP2 HIT — RUNNER ACTIVE TO TP3"),
    TP3_REACHED("ALL TARGETS HIT"),
    STOPPED_OUT("STOP LOSS HIT"),
    EXPIRED("PLAN EXPIRED"),
    INVALIDATED("STRUCTURE INVALIDATED")
}

data class TradeSetupPlan(
    val side: PositionSide,
    val pairSymbol: String,
    val currentPrice: Double,
    val entryZoneStart: Double,
    val entryZoneEnd: Double,
    val recommendedEntry: Double,
    val recommendedLeverage: Int,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double = 0.0,
    val stopLoss: Double,
    val estimatedLiquidationPrice: Double = 0.0,
    val riskRewardRatio: String = "1:2.0",
    val maxLossPercent: Double = 2.0,
    val expectedGainPercent: Double = 4.0,
    val reasoning: String = "",
    val invalidationPrice: Double = 0.0,
    val structureState: String = "Valid Trend Expansion",
    val ifStructureFailsPlan: String = "If invalidation level breaks, setup pivots to Breaker Block retest.",
    val liquiditySweepStatus: String = "Sweep Evaluation Active",
    val manipulationRiskRating: String = "Low (22%)",
    val lifecycleStatus: TradePlanLifecycleStatus = TradePlanLifecycleStatus.PENDING_ENTRY,
    val isLocked: Boolean = true,
    val timeframe: Timeframe = Timeframe.FIFTEEN_MIN,
    val isAvailable: Boolean = true,
    val unavailableReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val createdCandleTimestamp: Long = 0L,
    val atrUsed: Double = 0.0,
    val candleInterval: Long = 0L,
    val isAgainstTrend: Boolean = false,
    val trendWarning: String? = null,
    val entryType: EntryOrderType = EntryOrderType.LIMIT,
    val confluenceScore: Int = 0,
    val contributingNodes: List<String> = emptyList(),
    val whyThisEntry: String = "",
    val expiryTimestamp: Long = 0L,
    val isSpotRestricted: Boolean = false,
    val routedViaFuturesShort: Boolean = false,
    val marketType: TradingMarketType = TradingMarketType.SPOT,
    val entryFilled: Boolean = false,
    val signalOutput: SignalEngineOutput? = null,
    val entryModelName: String? = null,
    val verdictState: SignalVerdictState = SignalVerdictState.TRADE,
    val sessionStatus: MarketSessionStatus = MarketSessionStatus.OPEN,
    val netRiskRewardReal: Double = 2.0
) {
    fun evaluateStatus(livePrice: Double, candleLow: Double? = null, candleHigh: Double? = null): TradePlanLifecycleStatus {
        if (!isAvailable) return lifecycleStatus
        if (lifecycleStatus == TradePlanLifecycleStatus.ANALYZING ||
            lifecycleStatus == TradePlanLifecycleStatus.EXPIRED ||
            lifecycleStatus == TradePlanLifecycleStatus.INVALIDATED ||
            lifecycleStatus == TradePlanLifecycleStatus.STOPPED_OUT ||
            lifecycleStatus == TradePlanLifecycleStatus.TP3_REACHED) {
            return lifecycleStatus
        }
        if (expiryTimestamp > 0L && System.currentTimeMillis() > expiryTimestamp) {
            return TradePlanLifecycleStatus.EXPIRED
        }

        val zMin = minOf(entryZoneStart, entryZoneEnd)
        val zMax = maxOf(entryZoneStart, entryZoneEnd)
        val orderedTp1 = takeProfit1
        val orderedTp2 = if (takeProfit2 > 0) takeProfit2 else orderedTp1
        val orderedTp3 = if (takeProfit3 > 0) takeProfit3 else orderedTp2

        val isCurrentlyFilled = entryFilled || entryType == EntryOrderType.MARKET_NOW

        // 1. Check Stop Loss and Invalidation first
        if (stopLoss > 0.0) {
            if (side == PositionSide.LONG && livePrice <= stopLoss) return TradePlanLifecycleStatus.STOPPED_OUT
            if (side == PositionSide.SHORT && livePrice >= stopLoss) return TradePlanLifecycleStatus.STOPPED_OUT
        }

        if (invalidationPrice > 0.0) {
            if (side == PositionSide.LONG && livePrice <= invalidationPrice) return TradePlanLifecycleStatus.INVALIDATED
            if (side == PositionSide.SHORT && livePrice >= invalidationPrice) return TradePlanLifecycleStatus.INVALIDATED
        }

        if (!isCurrentlyFilled) {
            // Pending Limit Order: Check if post-creation tick fills entry
            val fillsEntry = if (side == PositionSide.LONG) {
                (livePrice in zMin..zMax) || (livePrice <= recommendedEntry)
            } else {
                (livePrice in zMin..zMax) || (livePrice >= recommendedEntry)
            }

            if (!fillsEntry) {
                // If price moved straight to TP1 without touching limit, plan missed/expired
                val missedTarget = if (side == PositionSide.LONG) livePrice >= orderedTp1 else livePrice <= orderedTp1
                return if (missedTarget) TradePlanLifecycleStatus.EXPIRED else TradePlanLifecycleStatus.PENDING_ENTRY
            }
        }

        return if (side == PositionSide.LONG) {
            when {
                orderedTp3 > 0 && livePrice >= orderedTp3 -> TradePlanLifecycleStatus.TP3_REACHED
                orderedTp2 > 0 && livePrice >= orderedTp2 -> TradePlanLifecycleStatus.TP2_REACHED
                orderedTp1 > 0 && livePrice >= orderedTp1 -> TradePlanLifecycleStatus.TP1_REACHED
                livePrice in zMin..zMax -> TradePlanLifecycleStatus.IN_ENTRY_ZONE
                else -> TradePlanLifecycleStatus.ACTIVE_IN_TRADE
            }
        } else {
            when {
                orderedTp3 > 0 && livePrice <= orderedTp3 -> TradePlanLifecycleStatus.TP3_REACHED
                orderedTp2 > 0 && livePrice <= orderedTp2 -> TradePlanLifecycleStatus.TP2_REACHED
                orderedTp1 > 0 && livePrice <= orderedTp1 -> TradePlanLifecycleStatus.TP1_REACHED
                livePrice in zMin..zMax -> TradePlanLifecycleStatus.IN_ENTRY_ZONE
                else -> TradePlanLifecycleStatus.ACTIVE_IN_TRADE
            }
        }
    }

    fun evaluateWithCandles(livePrice: Double, candles: List<CandlePoint>): TradePlanLifecycleStatus {
        if (!isAvailable) return lifecycleStatus
        if (lifecycleStatus == TradePlanLifecycleStatus.ANALYZING ||
            lifecycleStatus == TradePlanLifecycleStatus.EXPIRED ||
            lifecycleStatus == TradePlanLifecycleStatus.INVALIDATED ||
            lifecycleStatus == TradePlanLifecycleStatus.STOPPED_OUT ||
            lifecycleStatus == TradePlanLifecycleStatus.TP3_REACHED) {
            return lifecycleStatus
        }
        if (expiryTimestamp > 0L && System.currentTimeMillis() > expiryTimestamp) {
            return TradePlanLifecycleStatus.EXPIRED
        }
        if (candles.isEmpty()) return evaluateStatus(livePrice)

        val series = if (candles.size > 60) candles.takeLast(60) else candles

        // Strictly isolate post-creation candles so older historical bars from before the setup cannot trigger fills or TP/SL
        val minTs = if (createdCandleTimestamp > 0L) createdCandleTimestamp else (createdAt - 5000L)
        val matchingPostCreation = series.filter { it.timestamp > minTs }

        val activeHigh = if (matchingPostCreation.isNotEmpty()) maxOf(matchingPostCreation.maxOf { it.high }, livePrice) else livePrice
        val activeLow = if (matchingPostCreation.isNotEmpty()) minOf(matchingPostCreation.minOf { it.low }, livePrice) else livePrice

        val zMin = minOf(entryZoneStart, entryZoneEnd)
        val zMax = maxOf(entryZoneStart, entryZoneEnd)
        val orderedTp1 = takeProfit1
        val orderedTp2 = if (takeProfit2 > 0) takeProfit2 else orderedTp1
        val orderedTp3 = if (takeProfit3 > 0) takeProfit3 else orderedTp2

        val isCurrentlyFilled = entryFilled ||
                entryType == EntryOrderType.MARKET_NOW ||
                lifecycleStatus == TradePlanLifecycleStatus.ACTIVE_IN_TRADE ||
                lifecycleStatus == TradePlanLifecycleStatus.TP1_REACHED ||
                lifecycleStatus == TradePlanLifecycleStatus.TP2_REACHED

        // 1. Check Stop Loss on post-creation prices first
        if (stopLoss > 0.0) {
            if (side == PositionSide.LONG && activeLow <= stopLoss) return TradePlanLifecycleStatus.STOPPED_OUT
            if (side == PositionSide.SHORT && activeHigh >= stopLoss) return TradePlanLifecycleStatus.STOPPED_OUT
        }

        // 2. Check Structural Invalidation on post-creation prices
        if (invalidationPrice > 0.0) {
            if (side == PositionSide.LONG && activeLow <= invalidationPrice) return TradePlanLifecycleStatus.INVALIDATED
            if (side == PositionSide.SHORT && activeHigh >= invalidationPrice) return TradePlanLifecycleStatus.INVALIDATED
        }

        if (!isCurrentlyFilled) {
            // Pending Limit Order: requires subsequent price to touch limit or entry zone
            val filledByPostBar = if (side == PositionSide.LONG) {
                (livePrice in zMin..zMax) || (livePrice <= recommendedEntry) ||
                        matchingPostCreation.any { it.low <= zMax }
            } else {
                (livePrice in zMin..zMax) || (livePrice >= recommendedEntry) ||
                        matchingPostCreation.any { it.high >= zMin }
            }

            if (!filledByPostBar) {
                // If price moved away to TP1 without touching limit, plan missed/expired
                val missedTarget = if (side == PositionSide.LONG) activeHigh >= orderedTp1 && livePrice > zMax
                else activeLow <= orderedTp1 && livePrice < zMin
                return if (missedTarget) TradePlanLifecycleStatus.EXPIRED else TradePlanLifecycleStatus.PENDING_ENTRY
            }
        }

        // Check Take Profit Targets
        return if (side == PositionSide.LONG) {
            when {
                orderedTp3 > 0 && activeHigh >= orderedTp3 -> TradePlanLifecycleStatus.TP3_REACHED
                orderedTp2 > 0 && activeHigh >= orderedTp2 -> TradePlanLifecycleStatus.TP2_REACHED
                orderedTp1 > 0 && activeHigh >= orderedTp1 -> TradePlanLifecycleStatus.TP1_REACHED
                livePrice in zMin..zMax -> TradePlanLifecycleStatus.IN_ENTRY_ZONE
                else -> TradePlanLifecycleStatus.ACTIVE_IN_TRADE
            }
        } else {
            when {
                orderedTp3 > 0 && activeLow <= orderedTp3 -> TradePlanLifecycleStatus.TP3_REACHED
                orderedTp2 > 0 && activeLow <= orderedTp2 -> TradePlanLifecycleStatus.TP2_REACHED
                orderedTp1 > 0 && activeLow <= orderedTp1 -> TradePlanLifecycleStatus.TP1_REACHED
                livePrice in zMin..zMax -> TradePlanLifecycleStatus.IN_ENTRY_ZONE
                else -> TradePlanLifecycleStatus.ACTIVE_IN_TRADE
            }
        }
    }

    val isStoppedOutOrInvalidated: Boolean
        get() = lifecycleStatus == TradePlanLifecycleStatus.STOPPED_OUT ||
                lifecycleStatus == TradePlanLifecycleStatus.INVALIDATED

    val isStillActive: Boolean
        get() {
            if (!isAvailable) return false
            if (lifecycleStatus == TradePlanLifecycleStatus.TP3_REACHED ||
                lifecycleStatus == TradePlanLifecycleStatus.STOPPED_OUT ||
                lifecycleStatus == TradePlanLifecycleStatus.EXPIRED ||
                lifecycleStatus == TradePlanLifecycleStatus.INVALIDATED) {
                return false
            }
            if (expiryTimestamp > 0L && System.currentTimeMillis() > expiryTimestamp) {
                return false
            }
            // Invalidate stale setups where market price has drifted >18% away from planned entry
            if (recommendedEntry > 0.0 && currentPrice > 0.0) {
                val drift = kotlin.math.abs(currentPrice - recommendedEntry) / recommendedEntry
                if (drift > 0.18) {
                    return false
                }
            }
            return true
        }

    companion object {
        fun createAnalyzing(
            symbol: String,
            timeframe: Timeframe,
            side: PositionSide = PositionSide.LONG,
            currentPrice: Double = 0.0
        ): TradeSetupPlan {
            return TradeSetupPlan(
                side = side,
                pairSymbol = symbol,
                currentPrice = currentPrice,
                entryZoneStart = currentPrice,
                entryZoneEnd = currentPrice,
                recommendedEntry = currentPrice,
                recommendedLeverage = 10,
                takeProfit1 = 0.0,
                takeProfit2 = 0.0,
                takeProfit3 = 0.0,
                stopLoss = 0.0,
                estimatedLiquidationPrice = 0.0,
                riskRewardRatio = "N/A",
                maxLossPercent = 0.0,
                expectedGainPercent = 0.0,
                reasoning = "Analyzing full multi-agent consensus, TradingView technicals & order flow...",
                invalidationPrice = 0.0,
                timeframe = timeframe,
                isAvailable = false,
                unavailableReason = "Analyzing...",
                lifecycleStatus = TradePlanLifecycleStatus.ANALYZING,
                entryType = EntryOrderType.WAIT_NO_TRADE
            )
        }
    }
}

enum class TradingMarketType(val label: String) {

    SPOT("Spot Trading"),
    FUTURES("Perpetual Futures")
}

data class CandlePoint(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val isClosed: Boolean = true,
    val closeTimestamp: Long = 0L
) {
    fun effectiveCloseTimestamp(stepMillis: Long): Long =
        if (closeTimestamp > 0L) closeTimestamp else timestamp + stepMillis
}

data class CryptoFuturesData(
    val contractSymbol: String,
    val markPrice: Double,
    val indexPrice: Double,
    val fundingRatePercent: Double,
    val nextFundingCountdown: String,
    val openInterestUsd: Double,
    val longRatioPercent: Double,
    val shortRatioPercent: Double,
    val defaultLeverage: Int = 10,
    val liquidationLong: Double = 0.0,
    val liquidationShort: Double = 0.0
)

data class MarketQuote(
    val symbol: String,
    val name: String,
    val assetType: AssetType,
    val marketType: TradingMarketType = TradingMarketType.SPOT,
    val currentPrice: Double,
    val change: Double,
    val changePercent: Double,
    val open: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val volume: Double,
    val marketCap: Double = 0.0,
    val peRatio: Double? = null,
    val fiftyTwoWeekHigh: Double = 0.0,
    val fiftyTwoWeekLow: Double = 0.0,
    val candles: List<CandlePoint> = emptyList(),
    val primarySource: String = "Yahoo Finance API",
    val futuresData: CryptoFuturesData? = null,
    val timeframe: Timeframe = Timeframe.FIFTEEN_MIN,
    val isSynthetic: Boolean = false,
    val barsLoadedCount: Int = candles.size,
    val ticker24hOpen: Double = 0.0,
    val candleChangePercent: Double? = null,
    val lastClosedBarTimestamp: Long = candles.lastOrNull { it.isClosed }?.timestamp ?: 0L,
    val candleAgeMillis: Long = 0L,
    val isStale: Boolean = false,
    val staleReason: String? = null
)

data class CryptoScanItem(
    val symbol: String,
    val name: String,
    val marketType: TradingMarketType,
    val price: Double,
    val change24h: Double,
    val volume24hUsd: Double,
    val fundingRate: Double? = null,
    val openInterestUsd: Double? = null,
    val rsi: Double,
    val trendSignal: String, // "Strong Bullish", "Bearish Divergence", "Oversold Bounce", etc.
    val agentVerdict: TradingAction,
    val recommendedBuyPrice: Double = 0.0,
    val targetSellPrice: Double = 0.0,
    val stopLossPrice: Double = 0.0
)

enum class AiProvider(val displayName: String, val defaultModel: String, val defaultEndpoint: String) {
    GEMINI("Google Gemini", "gemini-2.5-flash", "https://generativelanguage.googleapis.com"),
    OPENAI("OpenAI", "gpt-4o-mini", "https://api.openai.com/v1"),
    ANTHROPIC("Anthropic Claude", "claude-3-5-sonnet-latest", "https://api.anthropic.com/v1"),
    DEEPSEEK("DeepSeek", "deepseek-chat", "https://api.deepseek.com/v1"),
    GROQ("Groq Llama-3", "llama-3.3-70b-versatile", "https://api.groq.com/openai/v1"),
    MISTRAL("Mistral AI", "mistral-large-latest", "https://api.mistral.ai/v1"),
    COHERE("Cohere Command", "command-r-plus", "https://api.cohere.com/v2"),
    PERPLEXITY("Perplexity AI", "sonar-pro", "https://api.perplexity.ai"),
    OLLAMA("Local Ollama / Self-Hosted", "llama3.2", "http://localhost:11434/v1"),
    OPENROUTER("OpenRouter", "openrouter/auto", "https://openrouter.ai/api/v1"),
    CUSTOM_REST("Custom OpenAI-Compatible API", "custom-model", "https://api.custom-ai.com/v1"),
    CUSTOM_ENDPOINT("Custom Direct REST Webhook", "webhook-agent", "https://your-server.com/api/predict"),
    SIMULATION("Deterministic Quant Fallback", "tauric-quant-local", "local://quant-engine")
}

data class AiModelConfig(
    val id: String,
    val provider: AiProvider,
    val modelName: String,
    val apiKey: String,
    val assignedRole: AgentRole?, // null means pool/round-robin
    val isEnabled: Boolean = true,
    var requestsRouted: Int = 0,
    var lastLatencyMs: Long = 0,
    var lastStatus: String = "Ready",
    var totalTokensUsed: Long = 0L,
    val customEndpoint: String? = null,
    val customDisplayName: String? = null,
    val customAuthHeader: String? = null
)

enum class ModelAvailability(val label: String, val colorHex: Long) {
    HEALTHY_AVAILABLE("Available", 0xFF10B981),
    DEGRADED_HIGH_LATENCY("High Latency", 0xFFF59E0B),
    RATE_LIMITED_429("Rate Limited (Cooling)", 0xFFEF4444),
    CIRCUIT_BROKEN("Unavailable", 0xFF6B7280)
}

data class ModelWorkloadMetrics(
    val modelId: String,
    val provider: AiProvider,
    val modelName: String,
    val estimatedTokensConsumed: Long = 0,
    val tokenQuotaPerHour: Long = 100_000,
    val remainingTokens: Long = 100_000,
    val inFlightRequests: Int = 0,
    val availability: ModelAvailability = ModelAvailability.HEALTHY_AVAILABLE,
    val consecutiveErrors: Int = 0,
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val avgLatencyMs: Long = 120,
    val cooldownRemainingSec: Long = 0
)

enum class LoadBalancingMode(val label: String, val description: String) {
    TOKEN_AVAILABILITY_BALANCED(
        "Token & Availability Intelligent Balancer",
        "Dynamically evaluates token quotas, in-flight capacity, and provider health to route workloads to the most available model with automatic failover."
    ),
    AUTO_CASCADE_FREE_TIER(
        "Smart Free-Tier Downgrade Cascade",
        "Prioritizes top AI models and automatically steps down to lower free versions or alternative free models when tokens/rate limits exhaust, dividing the workload seamlessly."
    ),
    SPECIALIZED_ROLE(
        "Role-Based Multi-AI Division",
        "Each specialized agent (Fundamental, Technical, Sentiment, Risk, Chief) runs on a distinct AI model and key."
    ),
    ROUND_ROBIN_PARALLEL(
        "Round-Robin Multi-Key Pool",
        "Distributes prompts evenly across all configured API keys to prevent rate limits and maximize throughput."
    )
}

enum class Timeframe(
    val label: String,
    val rangeParam: String,
    val intervalParam: String,
    val binanceInterval: String,
    val stepMillis: Long,
    val defaultCandleCount: Int
) {
    ONE_MIN("1m", "7d", "1m", "1m", 60_000L, 500),
    FIVE_MIN("5m", "60d", "5m", "5m", 300_000L, 500),
    FIFTEEN_MIN("15m", "60d", "15m", "15m", 900_000L, 500),
    THIRTY_MIN("30m", "60d", "30m", "30m", 1_800_000L, 500),
    ONE_HOUR("1h", "730d", "1h", "1h", 3_600_000L, 500),
    FOUR_HOURS("4h", "730d", "1h", "4h", 14_400_000L, 500),
    ONE_DAY("1D", "2y", "1d", "1d", 86_400_000L, 500),
    FIVE_DAYS("5D", "5y", "1d", "1d", 432_000_000L, 250),
    ONE_MONTH("1M", "5y", "1wk", "1w", 604_800_000L, 250),
    THREE_MONTHS("3M", "10y", "1mo", "1M", 2_592_000_000L, 120),
    ONE_YEAR("1Y", "10y", "1mo", "1M", 31_536_000_000L, 120)
}

data class TimeframeRiskConfig(
    val multiplier: Double,
    val maxTp1Pct: Double,
    val maxTp3Pct: Double,
    val maxRangeFactor: Double
)

val TIMEFRAME_RISK_CONFIGS: Map<Timeframe, TimeframeRiskConfig> = mapOf(
    Timeframe.ONE_MIN to TimeframeRiskConfig(multiplier = 0.35, maxTp1Pct = 0.008, maxTp3Pct = 0.022, maxRangeFactor = 0.40),
    Timeframe.FIVE_MIN to TimeframeRiskConfig(multiplier = 0.55, maxTp1Pct = 0.012, maxTp3Pct = 0.035, maxRangeFactor = 0.65),
    Timeframe.FIFTEEN_MIN to TimeframeRiskConfig(multiplier = 0.85, maxTp1Pct = 0.020, maxTp3Pct = 0.055, maxRangeFactor = 1.00),
    Timeframe.THIRTY_MIN to TimeframeRiskConfig(multiplier = 1.15, maxTp1Pct = 0.028, maxTp3Pct = 0.075, maxRangeFactor = 1.30),
    Timeframe.ONE_HOUR to TimeframeRiskConfig(multiplier = 1.50, maxTp1Pct = 0.040, maxTp3Pct = 0.100, maxRangeFactor = 1.60),
    Timeframe.FOUR_HOURS to TimeframeRiskConfig(multiplier = 2.20, maxTp1Pct = 0.080, maxTp3Pct = 0.180, maxRangeFactor = 2.50),
    Timeframe.ONE_DAY to TimeframeRiskConfig(multiplier = 3.50, maxTp1Pct = 0.140, maxTp3Pct = 0.300, maxRangeFactor = 4.00),
    Timeframe.FIVE_DAYS to TimeframeRiskConfig(multiplier = 5.00, maxTp1Pct = 0.220, maxTp3Pct = 0.450, maxRangeFactor = 6.00),
    Timeframe.ONE_MONTH to TimeframeRiskConfig(multiplier = 7.00, maxTp1Pct = 0.350, maxTp3Pct = 0.700, maxRangeFactor = 8.00),
    Timeframe.THREE_MONTHS to TimeframeRiskConfig(multiplier = 9.00, maxTp1Pct = 0.500, maxTp3Pct = 0.900, maxRangeFactor = 10.00),
    Timeframe.ONE_YEAR to TimeframeRiskConfig(multiplier = 12.00, maxTp1Pct = 0.750, maxTp3Pct = 1.500, maxRangeFactor = 15.00)
)

