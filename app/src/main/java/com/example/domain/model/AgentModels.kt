package com.example.domain.model

enum class AgentRole(val title: String, val badgeColorHex: Long) {
    FUNDAMENTAL("Fundamental Analyst", 0xFF10B981),
    TECHNICAL("Technical Analyst", 0xFF38BDF8),
    TRADINGVIEW_STUDIES("TradingView Technicals Agent", 0xFF2962FF),
    ORDER_FLOW("Order Flow & Book Analyst", 0xFF06B6D4),
    SENTIMENT("Sentiment & News Analyst", 0xFFF59E0B),
    NEWS_SCANNER("News Scanner Agent", 0xFFF97316),
    RISK_MANAGEMENT("Risk Manager", 0xFFEF4444),
    MARKET_MANIPULATION("Manipulation & Sweep Guard", 0xFFA855F7),
    CHIEF_STRATEGIST("Chief Investment Strategist", 0xFF818CF8)
}

enum class AgentStance {
    BULLISH,
    NEUTRAL,
    BEARISH
}

enum class TradingAction(val label: String, val isPositive: Boolean) {
    STRONG_BUY("STRONG BUY", true),
    BUY("BUY", true),
    HOLD("HOLD", false),
    SELL("SELL", false),
    STRONG_SELL("STRONG SELL", false)
}

data class NewsScannerResult(
    val query: String,
    val headlines: List<String> = emptyList(),
    val newsItems: List<NewsItem> = emptyList(),
    val sentimentPolarity: SentimentPolarity = SentimentPolarity.NEUTRAL,
    val sentimentScore: Double = 0.0, // -1.0 to +1.0
    val confidenceScore: Int = 75, // 0 - 100%
    val priceImpactPercent: Double = 0.0,
    val primaryCatalyst: String = "",
    val catalystSummary: String = "",
    val modelsScanned: List<String> = emptyList(),
    val institutionalStance: AgentStance = AgentStance.NEUTRAL,
    val timestamp: Long = System.currentTimeMillis(),
    val isLlmGenerated: Boolean = false
)

data class AgentReport(
    val role: AgentRole,
    val score: Int, // 1 to 10
    val stance: AgentStance,
    val headlineThesis: String,
    val keyPoints: List<String>,
    val quantitativeMetrics: Map<String, String>,
    val sourceBadge: String = "Rule-based",
    val modelName: String? = null
)

data class DebateMessage(
    val role: AgentRole,
    val agentName: String,
    val argument: String,
    val round: Int,
    val stance: AgentStance
)

data class MarketManipulationAnalysis(
    val manipulationRiskScore: Int, // 0 - 100%
    val manipulationLevel: String, // "Low", "Moderate", "Elevated (Stop Hunt Risk)", "High (Squeeze Alert)"
    val stopHuntDetected: Boolean,
    val sweepStatus: String, // e.g. "SSL Sweep Confirmed (Retail Stops Cleared)", "BSL Sweep Confirmed", "No Sweep (Caution on Breakout)"
    val sweptPriceLevel: Double? = null,
    val fakeoutWarning: String = "Normal order flow without structural traps",
    val fundingSqueezeRisk: String = "Funding rate balanced; normal liquidation risk",
    val structureStatus: String = "Valid Trend Expansion", // "Valid Order Flow", "Post-Sweep Reclaim", "Failed Structure: Breaker Block Pivot"
    val isStructureFailed: Boolean = false,
    val invalidationLevel: Double = 0.0,
    val ifStructureFailsContingency: String = "If invalidation level breaks, bias flips to Breaker Block retest."
)

// --- Four Ingestion Streams according to Architecture Schema ---
data class MarketStreamData(
    val sourceName: String = "Yahoo! Finance",
    val realTimePrice: Double,
    val dayChangePercent: Double,
    val volumeUsd: Double,
    val dayHigh: Double,
    val dayLow: Double,
    val technicalOscillatorsVerdict: String, // e.g. "Bullish MACD Cross & RSI Neutral (54.2)"
    val chartPatternDetected: String, // e.g. "Bullish Engulfing with Liquidity Sweep"
    val movingAverageAlignment: String // e.g. "Price above EMA 20 & 50, testing 200 EMA"
)

data class SocialMediaStreamData(
    val xTwitterSentiment: Double, // -1.0 to +1.0
    val xTwitterVolume24h: String, // e.g. "42.8K Tweets"
    val xCashtagVelocity: String, // e.g. "+312% Cashtag Spikes"
    val redditSentiment: Double, // -1.0 to +1.0
    val redditTopSubreddits: List<String>, // ["r/wallstreetbets", "r/cryptocurrency", "r/stocks"]
    val redditCommentMentions: Int,
    val eodhdSocialScore: Double, // 0 - 100
    val eodhdSocialBuzz: String // e.g. "High Retail Attention (88/100)"
)

data class NewsStreamData(
    val bloombergHeadlines: List<String>,
    val bloombergSentimentScore: Double,
    val redditBreakingNews: List<String>,
    val finnhubCatalysts: List<String>,
    val reutersWireUpdates: List<String>,
    val overallNewsSentiment: AgentStance
)

data class FundamentalsStreamData(
    val companyProfile: String,
    val sectorIndustry: String,
    val marketCapFormatted: String,
    val peRatio: String,
    val revenueGrowthYoY: String,
    val freeCashFlow: String,
    val balanceSheetHealth: String, // e.g. "Net Cash Positive • Low Leverage (D/E 0.28)"
    val insiderTransactionsSummary: String, // e.g. "Net Insider Accumulation (Form 4: +$1.4M Buys, 0 Sells)"
    val institutionalWhaleHoldings: String // e.g. "64.2% Institutional Ownership (BlackRock, Vanguard)"
)

// --- Researcher Team (Bullish vs Bearish Discussion -> Buy & Sell Evidence) ---
data class ResearcherDossier(
    val discussionSummary: String,
    val bullishResearcherThesis: String,
    val bearishResearcherThesis: String,
    val buyEvidence: List<String>,
    val sellEvidence: List<String>,
    val netEvidenceScore: Double // -10.0 to +10.0
)

// --- Trader Proposal (Powered by OpenAI o1 Deep Thinking) ---
data class TraderProposal(
    val modelBadge: String = "Rule-based Quant Engine",
    val reasoningSteps: List<String>,
    val proposedAction: TradingAction,
    val proposedStrategy: String, // "Spot Accumulation", "Perpetual Futures Long", etc.
    val plannedEntryZone: String,
    val recommendedLimitEntry: Double,
    val proposedStopLoss: Double,
    val proposedTakeProfit1: Double,
    val proposedTakeProfit2: Double,
    val proposedTakeProfit3: Double,
    val recommendedPositionSizePercent: Double,
    val recommendedLeverage: Int,
    val riskRewardRatio: String
)

// --- Risk Management Team (Aggressive, Neutral, Conservative) ---
data class RiskProfileEvaluation(
    val profileName: String, // "Aggressive", "Neutral", "Conservative"
    val riskRating: String, // "High Risk / Max Profit", "Balanced R:R", "Capital Preservation"
    val maxPositionSizePercent: Double,
    val suggestedLeverage: Int,
    val suggestedStopLoss: Double,
    val targetMultiplier: Double,
    val evaluationVerdict: String
)

data class RiskTeamEvaluation(
    val aggressiveProfile: RiskProfileEvaluation,
    val neutralProfile: RiskProfileEvaluation,
    val conservativeProfile: RiskProfileEvaluation,
    val consensusRiskLevel: String // "Low", "Moderate", "Elevated", "Extreme"
)

// --- Manager Decision & Execution ---
data class ManagerDecision(
    val status: String, // "AUTHORIZED", "MODIFIED", "CONDITIONAL_APPROVAL"
    val authorizedAction: TradingAction,
    val executiveRationale: String,
    val executionVenue: String, // "Binance / Bybit Execution Desk"
    val orderType: String, // "Pending Limit Post-Only"
    val executionReadiness: String // "Armed & Ready for Trigger"
)

data class MultiAgentSynthesis(
    val action: TradingAction,
    val confidenceScore: Int, // 0 - 100%
    val recommendedBuyPrice: Double = 0.0,
    val optimalBuyZoneLow: Double = 0.0,
    val optimalBuyZoneHigh: Double = 0.0,
    val entryStrategy: String = "Limit Order on Retracement",
    val targetPrice: Double,
    val stopLossPrice: Double,
    val takeProfitPrice: Double,
    val takeProfit2Price: Double = 0.0,
    val takeProfit3Price: Double = 0.0,
    val riskRewardRatio: String,
    val positionSizeRecommendation: String,
    val riskLevel: String, // Low, Moderate, High, Extreme
    val executiveSummary: String,
    val bullArguments: List<String>,
    val bearArguments: List<String>,
    val agentReports: List<AgentReport>,
    val debateTranscript: List<DebateMessage>,
    val bloombergHeadlinesScanned: List<String> = emptyList(),
    val bloombergPriceImpactPercent: Double = 0.0,
    val bloombergCatalystSummary: String = "",
    val predictedPriceChangePercent: Double = 0.0,
    val predictedPriceChangeUsd: Double = 0.0,
    val newsScannerResult: NewsScannerResult? = null,
    val nodesScanned: List<String> = emptyList(),
    val unifiedConsensusSummary: String = "",
    val unifiedConfluences: List<String> = emptyList(),
    val manipulationAnalysis: MarketManipulationAnalysis? = null,
    val tradingViewSummary: TradingViewTechnicalSummary? = null,
    val marketStream: MarketStreamData? = null,
    val socialMediaStream: SocialMediaStreamData? = null,
    val newsStream: NewsStreamData? = null,
    val fundamentalsStream: FundamentalsStreamData? = null,
    val researcherDossier: ResearcherDossier? = null,
    val traderProposal: TraderProposal? = null,
    val riskTeamEvaluation: RiskTeamEvaluation? = null,
    val managerDecision: ManagerDecision? = null,
    val tradeSetupPlan: TradeSetupPlan? = null,
    val signalEngineOutput: SignalEngineOutput? = null,
    val verdictState: SignalVerdictState = SignalVerdictState.TRADE,
    val canonicalVerdictState: VerdictState? = null,
    val aiShiftPoints: Int = 0,
    val aiShiftModelName: String? = null,
    val generatedAt: Long = System.currentTimeMillis()
)

data class TradingViewTechnicalSummary(
    val summaryVerdict: String, // "STRONG SELL", "SELL", "NEUTRAL", "BUY", "STRONG BUY"
    val movingAveragesVerdict: String, // "STRONG SELL", "SELL", "NEUTRAL", "BUY", "STRONG BUY"
    val oscillatorsVerdict: String, // "SELL", "NEUTRAL", "BUY"
    val favoriteStudiesVerdict: String = "NEUTRAL", // "STRONG SELL", "SELL", "NEUTRAL", "BUY", "STRONG BUY"
    val buyCount: Int,
    val neutralCount: Int,
    val sellCount: Int,
    val totalIndicators: Int = 34,
    val rsiValue: Double = 50.0,
    val macdLevel: Double = 0.0,
    val stochK: Double = 50.0,
    val adxStrength: Double = 25.0,
    val cciValue: Double = 0.0,
    val awesomeOscillator: Double = 0.0,
    val supertrendValue: Double = 0.0,
    val supertrendSignal: String = "NEUTRAL",
    val parabolicSarValue: Double = 0.0,
    val chaikinMoneyFlow: Double = 0.0,
    val moneyFlowIndex: Double = 50.0,
    val ema20: Double = 0.0,
    val ema50: Double = 0.0,
    val ema200: Double = 0.0,
    val sma20: Double = 0.0,
    val sma50: Double = 0.0,
    val sma200: Double = 0.0,
    val indicatorBreakdown: List<TradingViewIndicatorSignal> = emptyList(),
    val favoriteStudiesBreakdown: List<TradingViewIndicatorSignal> = emptyList()
)

data class TradingViewIndicatorSignal(
    val name: String,
    val value: String,
    val signal: String // "BUY", "SELL", "NEUTRAL"
)

data class ApiSourceInfo(
    val id: String,
    val name: String,
    val description: String,
    val endpointUrl: String,
    val isFree: Boolean,
    val requiresApiKey: Boolean,
    var isEnabled: Boolean = true,
    var status: String = "Active", // "Active", "Testing", "Offline"
    var latencyMs: Long = 0,
    var dataPointsProvided: String = ""
)
