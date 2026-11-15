package com.example.domain.model

data class BollingerBands(
    val upper: Double,
    val middle: Double,
    val lower: Double
)

data class MacdData(
    val macd: Double,
    val signal: Double,
    val histogram: Double
)

enum class FvgType(val label: String, val isBullish: Boolean) {
    BULLISH_IMBALANCE("Bullish FVG (BISI)", true),
    BEARISH_IMBALANCE("Bearish FVG (SIBI)", false)
}

data class FairValueGap(
    val type: FvgType,
    val top: Double,
    val bottom: Double,
    val midPrice: Double,
    val isMitigated: Boolean,
    val candleIndex: Int,
    val description: String
)

enum class OrderBlockType(val label: String, val isBullish: Boolean) {
    BULLISH_DEMAND_BLOCK("Bullish Order Block (OB+)", true),
    BEARISH_SUPPLY_BLOCK("Bearish Supply Block (OB-)", false)
}

data class OrderBlock(
    val type: OrderBlockType,
    val top: Double,
    val bottom: Double,
    val volume: Double,
    val isMitigated: Boolean,
    val candleIndex: Int,
    val description: String
)

enum class LiquidityType(val label: String, val targetDescription: String) {
    BUY_SIDE_LIQUIDITY_BSL("Buy-Side Liquidity (BSL)", "Retail stops above swing highs / equal highs"),
    SELL_SIDE_LIQUIDITY_SSL("Sell-Side Liquidity (SSL)", "Retail stops below swing lows / equal lows")
}

data class LiquidityPool(
    val type: LiquidityType,
    val priceLevel: Double,
    val sweepDetected: Boolean,
    val description: String
)

data class SmcMarketStructure(
    val trend: String, // "Bullish Continuation (BOS)", "Bearish CHoCH Reversal", "Range Compression"
    val lastBOS: Double?,
    val lastCHoCH: Double?,
    val activeFVGs: List<FairValueGap>,
    val activeOrderBlocks: List<OrderBlock>,
    val liquidityPools: List<LiquidityPool>,
    val recentLiquiditySweep: LiquidityPool?,
    val institutionalImbalanceSummary: String,
    val builtInSmcSignal: String = "NEUTRAL", // "BULLISH ACCUMULATION", "BEARISH DISTRIBUTION", "EQUILIBRIUM"
    val smcInternalStructure: String = "Internal CHoCH Aligned", // Internal BOS / CHoCH
    val smcSwingStructure: String = "Swing BOS Expansion", // Swing BOS / CHoCH
    val smcStructureTrend: String = "Bullish Order Flow", // Strong/Weak Highs/Lows
    val smcStrongHighLow: String = "Strong Low Protected", // "Strong Low Protected", "Weak High Targeted", etc.
    val premiumDiscountZone: String = "Discount Zone (<50%)", // "Premium Zone (>50%)", "Equilibrium (50%)", "Discount Zone (<50%)"
    val premiumDiscountPercent: Double = 50.0
) {
    val luxAlgoSmcSignal: String get() = builtInSmcSignal
    val luxAlgoInternalStructure: String get() = smcInternalStructure
    val luxAlgoSwingStructure: String get() = smcSwingStructure
    val gainzAlgoStructureTrend: String get() = smcStructureTrend
    val gainzAlgoStrongHighLow: String get() = smcStrongHighLow
}

data class VolumeProfileData(
    val poc: Double,
    val vah: Double,
    val valLevel: Double,
    val hvnList: List<Double> = emptyList(),
    val lvnList: List<Double> = emptyList()
)

data class TechnicalIndicators(
    val rsi14: Double,
    val sma20: Double,
    val sma50: Double,
    val ema12: Double,
    val ema26: Double,
    val macd: MacdData,
    val bollingerBands: BollingerBands,
    val supportLevel: Double,
    val resistanceLevel: Double,
    val averageTrueRange: Double,
    val volumeRatio: Double, // Current vs 20d avg
    val smc: SmcMarketStructure? = null,
    val ema50: Double = sma50,
    val ema200: Double = sma50,
    val adx: Double = 25.0,
    val plusDi: Double = 25.0,
    val minusDi: Double = 25.0,
    val bandwidthPercentile: Double = 50.0,
    val efficiencyRatio: Double = 0.5,
    val volumeProfile: VolumeProfileData? = null,
    val rvol: Double = volumeRatio,
    val sessionVwap: Double = 0.0,
    val atrPercentile: Double = 50.0,
    val range24hHigh: Double = 0.0,
    val range24hLow: Double = 0.0
)
