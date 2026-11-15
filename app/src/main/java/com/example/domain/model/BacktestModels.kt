package com.example.domain.model

enum class AgentConsensusMode(val label: String, val description: String) {
    FULL_COMMITTEE(
        "Full Multi-Agent Committee",
        "All 4 agents (Fundamental, Technical, Sentiment, Risk) vote. Requires committee convergence."
    ),
    TECHNICAL_RISK_MOMENTUM(
        "Technical + Risk Officer",
        "Technical studies detect momentum, filtered strictly by Risk Manager stop-loss boundaries."
    ),
    SENTIMENT_CATALYST_BREAKOUT(
        "Sentiment Catalyst + Tape",
        "NewsScanner headlines and institutional sentiment trigger entries with technical support invalidation."
    )
}

data class BacktestStrategyConfig(
    val initialCapital: Double = 10_000.0,
    val positionSizePercent: Double = 25.0, // 25% of equity per trade
    val leverage: Int = 1,
    val minConfidenceThreshold: Int = 65, // 65% minimum agent confidence to execute
    val enableStopLoss: Boolean = true,
    val stopLossAtrMultiplier: Double = 1.5,
    val takeProfitAtrMultiplier: Double = 3.0,
    val consensusMode: AgentConsensusMode = AgentConsensusMode.FULL_COMMITTEE
)

data class BacktestTrade(
    val id: String,
    val entryTimestamp: Long,
    val exitTimestamp: Long,
    val entryPrice: Double,
    val exitPrice: Double,
    val side: PositionSide,
    val returnPercent: Double,
    val returnUsd: Double,
    val isWin: Boolean,
    val exitReason: String, // "Take Profit Hit", "Stop Loss Invalidation", "Agent Committee Reversal", "Horizon Close"
    val agentVerdictAtEntry: TradingAction,
    val agentConfidenceAtEntry: Int,
    val consensusThesis: String
)

data class EquityPoint(
    val timestamp: Long,
    val equity: Double,
    val benchmarkEquity: Double,
    val drawdownPercent: Double
)

data class BacktestResult(
    val symbol: String,
    val timeframe: Timeframe,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRatePercent: Double,
    val totalReturnPercent: Double,
    val totalReturnUsd: Double,
    val benchmarkReturnPercent: Double,
    val alphaPercent: Double,
    val profitFactor: Double,
    val maxDrawdownPercent: Double,
    val sharpeRatio: Double,
    val averageTradeReturnPercent: Double,
    val trades: List<BacktestTrade>,
    val equityCurve: List<EquityPoint>,
    val agentContributionStats: Map<AgentRole, Double>,
    val executionTimeMs: Long
)
