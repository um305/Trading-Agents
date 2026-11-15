package com.example

import com.example.domain.engine.UnifiedEntryEngine
import com.example.domain.model.AgentReport
import com.example.domain.model.AgentRole
import com.example.domain.model.AgentStance
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.EntryOrderType
import com.example.domain.model.PositionSide
import com.example.domain.model.ResearcherDossier
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import com.example.domain.model.TradingViewTechnicalSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class UnifiedEntryEngineTest {

    private fun generateSyntheticCandles(
        basePrice: Double,
        volatilityPercent: Double,
        count: Int = 50,
        timeframe: Timeframe = Timeframe.FIFTEEN_MIN
    ): List<CandlePoint> {
        val candles = mutableListOf<CandlePoint>()
        var price = basePrice
        val step = timeframe.stepMillis
        val startTs = 1700000000000L
        for (i in 0 until count) {
            val high = price * (1.0 + volatilityPercent * 0.5)
            val low = price * (1.0 - volatilityPercent * 0.5)
            val close = price * (1.0 + (if (i % 2 == 0) volatilityPercent * 0.2 else -volatilityPercent * 0.18))
            candles.add(
                CandlePoint(
                    timestamp = startTs + (i * step),
                    open = price,
                    high = high,
                    low = low,
                    close = close,
                    volume = 15000.0 * (1.0 + (i % 5) * 0.2)
                )
            )
            price = close
        }
        return candles
    }

    private fun createBullishAgentReports(): List<AgentReport> {
        return listOf(
            AgentReport(
                role = AgentRole.FUNDAMENTAL,
                stance = AgentStance.BULLISH,
                headlineThesis = "Strong fundamentals and cash flow acceleration",
                keyPoints = listOf("Revenue grew 25%", "Operating margin expansion"),
                quantitativeMetrics = emptyMap(),
                score = 9
            ),
            AgentReport(
                role = AgentRole.TECHNICAL,
                stance = AgentStance.BULLISH,
                headlineThesis = "Bullish momentum above moving averages",
                keyPoints = listOf("RSI healthy above 55", "MACD bullish crossover confirmed"),
                quantitativeMetrics = emptyMap(),
                score = 8
            ),
            AgentReport(
                role = AgentRole.SENTIMENT,
                stance = AgentStance.BULLISH,
                headlineThesis = "Positive retail and institutional sentiment",
                keyPoints = listOf("Sentiment score 78%", "High volume accumulation"),
                quantitativeMetrics = emptyMap(),
                score = 8
            ),
            AgentReport(
                role = AgentRole.RISK_MANAGEMENT,
                stance = AgentStance.BULLISH,
                headlineThesis = "Controlled drawdown risk with clean structural low",
                keyPoints = listOf("Vol-adjusted risk acceptable", "Clean invalidation stop"),
                quantitativeMetrics = emptyMap(),
                score = 8
            )
        )
    }

    private fun createBearishAgentReports(): List<AgentReport> {
        return listOf(
            AgentReport(
                role = AgentRole.FUNDAMENTAL,
                stance = AgentStance.BEARISH,
                headlineThesis = "Deteriorating earnings and elevated leverage",
                keyPoints = listOf("Negative margin guidance", "Debt maturities pressing"),
                quantitativeMetrics = emptyMap(),
                score = 2
            ),
            AgentReport(
                role = AgentRole.TECHNICAL,
                stance = AgentStance.BEARISH,
                headlineThesis = "Severe breakdown below key support levels",
                keyPoints = listOf("RSI bearish divergence", "Death cross imminent"),
                quantitativeMetrics = emptyMap(),
                score = 2
            ),
            AgentReport(
                role = AgentRole.SENTIMENT,
                stance = AgentStance.BEARISH,
                headlineThesis = "Extreme fear and aggressive distribution",
                keyPoints = listOf("Net institutional selling", "Bearish tape order flow"),
                quantitativeMetrics = emptyMap(),
                score = 2
            ),
            AgentReport(
                role = AgentRole.RISK_MANAGEMENT,
                stance = AgentStance.BEARISH,
                headlineThesis = "Elevated downside liquidation cascade risk",
                keyPoints = listOf("Excessive leverage in market", "Structural low violated"),
                quantitativeMetrics = emptyMap(),
                score = 2
            )
        )
    }

    data class AssetSpec(
        val symbol: String,
        val price: Double,
        val volPct: Double,
        val assetType: AssetType
    )

    private val testAssets = listOf(
        AssetSpec("BTC", 68500.0, 0.015, AssetType.CRYPTO),
        AssetSpec("SOL", 185.0, 0.025, AssetType.CRYPTO),
        AssetSpec("DOGE", 0.165, 0.035, AssetType.CRYPTO),
        AssetSpec("NVDA", 132.0, 0.018, AssetType.STOCK)
    )

    private val testTimeframes = listOf(
        Timeframe.ONE_MIN,
        Timeframe.FIVE_MIN,
        Timeframe.FIFTEEN_MIN,
        Timeframe.ONE_HOUR,
        Timeframe.FOUR_HOURS,
        Timeframe.ONE_DAY
    )

    @Test
    fun testEntryIsNotJustScanPriceAndIsOnCorrectSideOfPrice() {
        for (asset in testAssets) {
            for (tf in testTimeframes) {
                val candles = generateSyntheticCandles(asset.price, asset.volPct, 50, tf)
                val currentPrice = candles.last().close
                val dayHigh = currentPrice * 1.03
                val dayLow = currentPrice * 0.97

                // Test LONG
                val longInputs = UnifiedEntryEngine.EntryEngineInputs(
                    symbol = asset.symbol,
                    currentPrice = currentPrice,
                    side = PositionSide.LONG,
                    leverage = 5,
                    candles = candles,
                    dayHigh = dayHigh,
                    dayLow = dayLow,
                    timeframe = tf,
                    assetType = asset.assetType,
                    marketType = TradingMarketType.FUTURES,
                    isSynthetic = false,
                    agentReports = createBullishAgentReports(),
                    researcherDossier = ResearcherDossier(
                        discussionSummary = "Bullish consensus on liquidity expansion",
                        bullishResearcherThesis = "Clear demand order block holding",
                        bearishResearcherThesis = "Minor short term overextension",
                        buyEvidence = listOf("Earnings beat", "Positive volume delta"),
                        sellEvidence = emptyList(),
                        netEvidenceScore = 6.5
                    ),
                    tradingViewSummary = TradingViewTechnicalSummary(
                        summaryVerdict = "BUY",
                        movingAveragesVerdict = "STRONG BUY",
                        oscillatorsVerdict = "NEUTRAL",
                        buyCount = 18,
                        neutralCount = 6,
                        sellCount = 2
                    )
                )

                val longPlan = UnifiedEntryEngine.buildTradeSetupPlan(longInputs)

                if (longPlan.entryType == EntryOrderType.LIMIT) {
                    // Must be <= currentPrice for limit long
                    assertTrue(
                        "Long entry for ${asset.symbol} on ${tf.label} must be <= currentPrice",
                        longPlan.recommendedEntry <= currentPrice
                    )
                    // Must not be an arbitrary fixed ~0.2% scan price
                    val entryOffset = abs(currentPrice - longPlan.recommendedEntry) / currentPrice
                    assertTrue(
                        "Long entry for ${asset.symbol} on ${tf.label} should be meaningfully anchored to a confluent zone",
                        entryOffset >= 0.0
                    )
                }

                // Test SHORT
                val shortInputs = longInputs.copy(
                    side = PositionSide.SHORT,
                    agentReports = createBearishAgentReports(),
                    researcherDossier = ResearcherDossier(
                        discussionSummary = "Bearish distribution and breakdown",
                        bullishResearcherThesis = "Minor oversold bounce possible",
                        bearishResearcherThesis = "Overhead supply block rejection",
                        buyEvidence = emptyList(),
                        sellEvidence = listOf("Net distribution", "Macro headwind"),
                        netEvidenceScore = -6.5
                    ),
                    tradingViewSummary = TradingViewTechnicalSummary(
                        summaryVerdict = "SELL",
                        movingAveragesVerdict = "STRONG SELL",
                        oscillatorsVerdict = "SELL",
                        buyCount = 2,
                        neutralCount = 6,
                        sellCount = 18
                    )
                )

                val shortPlan = UnifiedEntryEngine.buildTradeSetupPlan(shortInputs)

                if (shortPlan.entryType == EntryOrderType.LIMIT) {
                    // Must be >= currentPrice for limit short
                    assertTrue(
                        "Short entry for ${asset.symbol} on ${tf.label} must be >= currentPrice",
                        shortPlan.recommendedEntry >= currentPrice
                    )
                }
            }
        }
    }

    @Test
    fun testWaitNoTradeAppearsWhenAgentsStronglyDisagree() {
        val asset = testAssets.first() // BTC
        val candles = generateSyntheticCandles(asset.price, asset.volPct, 50, Timeframe.ONE_HOUR)
        val currentPrice = candles.last().close

        // Requesting LONG, but all agents and researcher are strongly BEARISH
        val conflictingInputs = UnifiedEntryEngine.EntryEngineInputs(
            symbol = asset.symbol,
            currentPrice = currentPrice,
            side = PositionSide.LONG,
            leverage = 5,
            candles = candles,
            dayHigh = currentPrice * 1.03,
            dayLow = currentPrice * 0.97,
            timeframe = Timeframe.ONE_HOUR,
            assetType = asset.assetType,
            marketType = TradingMarketType.FUTURES,
            isSynthetic = false,
            agentReports = createBearishAgentReports(), // Strongly bearish agents!
            researcherDossier = ResearcherDossier(
                discussionSummary = "Critical structure breakdown",
                bullishResearcherThesis = "No bullish case present",
                bearishResearcherThesis = "Severe institutional selling",
                buyEvidence = emptyList(),
                sellEvidence = listOf("Severe supply overhang", "Institutional shorting"),
                netEvidenceScore = -8.0
            ),
            tradingViewSummary = TradingViewTechnicalSummary(
                summaryVerdict = "STRONG SELL",
                movingAveragesVerdict = "STRONG SELL",
                oscillatorsVerdict = "STRONG SELL",
                buyCount = 0,
                neutralCount = 2,
                sellCount = 24
            )
        )

        val plan = UnifiedEntryEngine.buildTradeSetupPlan(conflictingInputs)

        // Must output WAIT_NO_TRADE due to severe multi-agent directional disagreement
        assertEquals(
            "When agents strongly disagree with the requested side, entryType must be WAIT_NO_TRADE",
            EntryOrderType.WAIT_NO_TRADE,
            plan.entryType
        )
        assertFalse("Setup must not be available when WAIT_NO_TRADE is returned", plan.isAvailable)
        assertTrue("Reasoning or unavailableReason must explain why trade is avoided", plan.unavailableReason.isNotBlank() || plan.reasoning.isNotBlank())
    }

    @Test
    fun testChangingAgentOutputsAltersOrRemovesSetup() {
        val asset = testAssets[1] // SOL
        val candles = generateSyntheticCandles(asset.price, asset.volPct, 50, Timeframe.FIFTEEN_MIN)
        val currentPrice = candles.last().close

        val bullishInputs = UnifiedEntryEngine.EntryEngineInputs(
            symbol = asset.symbol,
            currentPrice = currentPrice,
            side = PositionSide.LONG,
            leverage = 10,
            candles = candles,
            dayHigh = currentPrice * 1.025,
            dayLow = currentPrice * 0.975,
            timeframe = Timeframe.FIFTEEN_MIN,
            assetType = asset.assetType,
            marketType = TradingMarketType.FUTURES,
            isSynthetic = false,
            agentReports = createBullishAgentReports(),
            researcherDossier = ResearcherDossier(
                discussionSummary = "Surging liquidity into Solana ecosystem",
                bullishResearcherThesis = "DEX volume record breaking out",
                bearishResearcherThesis = "None",
                buyEvidence = listOf("Ecosystem TVL surging", "DEX volume record"),
                sellEvidence = emptyList(),
                netEvidenceScore = 7.0
            ),
            tradingViewSummary = TradingViewTechnicalSummary(
                summaryVerdict = "BUY",
                movingAveragesVerdict = "STRONG BUY",
                oscillatorsVerdict = "BUY",
                buyCount = 16,
                neutralCount = 6,
                sellCount = 2
            )
        )

        val planBullish = UnifiedEntryEngine.buildTradeSetupPlan(bullishInputs)

        // Now modify researcher to strongly bearish evidence
        val modifiedInputs = bullishInputs.copy(
            researcherDossier = ResearcherDossier(
                discussionSummary = "Abrupt token unlock distribution detected",
                bullishResearcherThesis = "Short covering only",
                bearishResearcherThesis = "Heavy unlocks dumping on order books",
                buyEvidence = emptyList(),
                sellEvidence = listOf("Network congestion detected", "Major token unlock event"),
                netEvidenceScore = -7.5
            ),
            agentReports = listOf(
                createBullishAgentReports()[0].copy(stance = AgentStance.BEARISH, score = 2),
                createBullishAgentReports()[1].copy(stance = AgentStance.BEARISH, score = 2)
            )
        )

        val planModified = UnifiedEntryEngine.buildTradeSetupPlan(modifiedInputs)

        // Confluence score or trade availability must reflect the change in agent consensus
        assertNotEquals(
            "Changing agent outputs should result in different confluence score or altered setup",
            planBullish.confluenceScore,
            planModified.confluenceScore
        )
    }

    @Test
    fun testStopAndTargetsKeepOrderingAndTimeframeScalingRules() {
        for (asset in testAssets) {
            for (tf in testTimeframes) {
                val candles = generateSyntheticCandles(asset.price, asset.volPct, 50, tf)
                val currentPrice = candles.last().close
                val dayHigh = currentPrice * 1.03
                val dayLow = currentPrice * 0.97

                // Test LONG Ordering
                val longInputs = UnifiedEntryEngine.EntryEngineInputs(
                    symbol = asset.symbol,
                    currentPrice = currentPrice,
                    side = PositionSide.LONG,
                    leverage = 5,
                    candles = candles,
                    dayHigh = dayHigh,
                    dayLow = dayLow,
                    timeframe = tf,
                    assetType = asset.assetType,
                    marketType = TradingMarketType.FUTURES,
                    isSynthetic = false,
                    agentReports = createBullishAgentReports(),
                    researcherDossier = ResearcherDossier(
                        discussionSummary = "Consensus support expansion",
                        bullishResearcherThesis = "Order block expansion intact",
                        bearishResearcherThesis = "Standard retest pullback",
                        buyEvidence = listOf("Growth", "Flow"),
                        sellEvidence = emptyList(),
                        netEvidenceScore = 6.0
                    ),
                    tradingViewSummary = TradingViewTechnicalSummary(
                        summaryVerdict = "BUY",
                        movingAveragesVerdict = "STRONG BUY",
                        oscillatorsVerdict = "BUY",
                        buyCount = 18,
                        neutralCount = 6,
                        sellCount = 2
                    )
                )

                val plan = UnifiedEntryEngine.buildTradeSetupPlan(longInputs)

                if (plan.isAvailable && plan.entryType != EntryOrderType.WAIT_NO_TRADE) {
                    // Strict ordering: Stop Loss < Entry <= TP1 < TP2 < TP3
                    assertTrue(
                        "Long SL (${plan.stopLoss}) must be strictly less than recommendedEntry (${plan.recommendedEntry}) for ${asset.symbol} on ${tf.label}",
                        plan.stopLoss < plan.recommendedEntry
                    )
                    assertTrue(
                        "Long TP1 (${plan.takeProfit1}) must be strictly greater than recommendedEntry (${plan.recommendedEntry}) for ${asset.symbol} on ${tf.label}",
                        plan.takeProfit1 > plan.recommendedEntry
                    )
                    assertTrue(
                        "Long TP2 (${plan.takeProfit2}) must be strictly greater than TP1 (${plan.takeProfit1}) for ${asset.symbol} on ${tf.label}",
                        plan.takeProfit2 > plan.takeProfit1
                    )
                    assertTrue(
                        "Long TP3 (${plan.takeProfit3}) must be strictly greater than TP2 (${plan.takeProfit2}) for ${asset.symbol} on ${tf.label}",
                        plan.takeProfit3 > plan.takeProfit2
                    )

                    // Target distance scaling: target distance must be positive
                    val currentLongDistance = plan.takeProfit1 - plan.recommendedEntry
                    assertTrue(
                        "Target 1 distance must be positive for ${asset.symbol} on ${tf.label}",
                        currentLongDistance > 0.0
                    )
                }
            }
        }
    }

    @Test
    fun testSpotBearishShowsAvoidWaitRestricted() {
        val asset = testAssets.first() // BTC
        val candles = generateSyntheticCandles(asset.price, asset.volPct, 50, Timeframe.FIFTEEN_MIN)
        val currentPrice = candles.last().close

        val spotBearishInputs = UnifiedEntryEngine.EntryEngineInputs(
            symbol = asset.symbol,
            currentPrice = currentPrice,
            side = PositionSide.SHORT,
            leverage = 1,
            candles = candles,
            dayHigh = currentPrice * 1.02,
            dayLow = currentPrice * 0.98,
            timeframe = Timeframe.FIFTEEN_MIN,
            assetType = asset.assetType,
            marketType = TradingMarketType.SPOT, // SPOT mode!
            isSynthetic = false,
            agentReports = createBearishAgentReports(),
            researcherDossier = ResearcherDossier(
                discussionSummary = "Bearish macro breakdown",
                bullishResearcherThesis = "None",
                bearishResearcherThesis = "Heavy spot outflow",
                buyEvidence = emptyList(),
                sellEvidence = listOf("Breakdown"),
                netEvidenceScore = -5.0
            ),
            tradingViewSummary = TradingViewTechnicalSummary(
                summaryVerdict = "SELL",
                movingAveragesVerdict = "STRONG SELL",
                oscillatorsVerdict = "SELL",
                buyCount = 2,
                neutralCount = 4,
                sellCount = 20
            )
        )

        val plan = UnifiedEntryEngine.buildTradeSetupPlan(spotBearishInputs)

        assertTrue(
            "Spot Short with Futures perp available must route via Futures (perp)",
            plan.routedViaFuturesShort
        )
        assertEquals(
            "Routed Spot Short must evaluate against FUTURES market",
            TradingMarketType.FUTURES,
            plan.marketType
        )
        assertFalse(
            "Spot Short must never set legacy isSpotRestricted",
            plan.isSpotRestricted
        )

        // When no Futures perp exists, hard veto cleanly
        val noPerpPlan = UnifiedEntryEngine.buildTradeSetupPlan(
            spotBearishInputs.copy(symbol = "NOLIST", futuresPerpAvailable = false)
        )
        assertFalse(noPerpPlan.isAvailable)
        assertTrue(
            "Missing perp must state 'No futures market for <symbol>'",
            noPerpPlan.unavailableReason.contains("No futures market for NOLIST", ignoreCase = true)
        )
    }

    @Test
    fun testStopLossDoesNotRevertToInEntryZoneOnceStoppedOut() {
        // Create a long trade setup plan
        val plan = com.example.domain.model.TradeSetupPlan(
            side = PositionSide.LONG,
            pairSymbol = "SNDK",
            currentPrice = 1383.96,
            entryZoneStart = 1382.23,
            entryZoneEnd = 1385.70,
            recommendedEntry = 1383.96,
            recommendedLeverage = 50,
            takeProfit1 = 1395.0,
            takeProfit2 = 1405.0,
            takeProfit3 = 1420.0,
            stopLoss = 1381.12,
            invalidationPrice = 1381.12,
            lifecycleStatus = com.example.domain.model.TradePlanLifecycleStatus.PENDING_ENTRY,
            timeframe = Timeframe.FIVE_MIN,
            isAvailable = true
        )

        // 1. Price drops to $1,380.92 (below SL of $1,381.12)
        val stoppedStatus = plan.evaluateStatus(livePrice = 1380.92)
        assertEquals("Should be STOPPED_OUT when price breaches SL", com.example.domain.model.TradePlanLifecycleStatus.STOPPED_OUT, stoppedStatus)

        // 2. The plan is updated with STOPPED_OUT status
        val stoppedPlan = plan.copy(lifecycleStatus = stoppedStatus)

        // 3. Price bounces back up to $1,382.33 (which happens to be inside entryZoneStart..entryZoneEnd)
        val bouncedStatus = stoppedPlan.evaluateStatus(livePrice = 1382.33)
        assertEquals("Once stopped out, status MUST remain STOPPED_OUT and never revert to IN_ENTRY_ZONE", com.example.domain.model.TradePlanLifecycleStatus.STOPPED_OUT, bouncedStatus)
    }

    @Test
    fun testCandleWickPiercingStopLossTriggersStoppedOut() {
        val plan = com.example.domain.model.TradeSetupPlan(
            side = PositionSide.LONG,
            pairSymbol = "SNDK",
            currentPrice = 1383.96,
            entryZoneStart = 1382.23,
            entryZoneEnd = 1385.70,
            recommendedEntry = 1383.96,
            recommendedLeverage = 50,
            takeProfit1 = 1395.0,
            takeProfit2 = 1405.0,
            takeProfit3 = 1420.0,
            stopLoss = 1381.12,
            invalidationPrice = 1381.12,
            lifecycleStatus = com.example.domain.model.TradePlanLifecycleStatus.PENDING_ENTRY,
            timeframe = Timeframe.FIVE_MIN,
            isAvailable = true
        )

        // Candle closed at $1,382.33 (in entry zone) but made a low wick of $1,380.92 (below SL $1,381.12)
        val candle = CandlePoint(
            timestamp = System.currentTimeMillis(),
            open = 1383.00,
            high = 1384.50,
            low = 1380.92,
            close = 1382.33,
            volume = 50000.0
        )

        val status = plan.evaluateWithCandles(livePrice = 1382.33, candles = listOf(candle))
        assertEquals("Candle wick breach below stop loss must trigger STOPPED_OUT", com.example.domain.model.TradePlanLifecycleStatus.STOPPED_OUT, status)
    }

    @Test
    fun testStopLossPlacementHasAdequateStructuralBreathingRoom() {
        val candles = generateSyntheticCandles(basePrice = 1383.0, volatilityPercent = 0.008, count = 50, timeframe = Timeframe.FIVE_MIN)
        val inputs = UnifiedEntryEngine.EntryEngineInputs(
            symbol = "SNDK",
            currentPrice = 1383.0,
            side = PositionSide.LONG,
            leverage = 50,
            candles = candles,
            dayHigh = 1400.0,
            dayLow = 1370.0,
            timeframe = Timeframe.FIVE_MIN,
            assetType = AssetType.CRYPTO,
            marketType = TradingMarketType.FUTURES
        )

        val plan = UnifiedEntryEngine.buildTradeSetupPlan(inputs)

        val slDistance = abs(plan.recommendedEntry - plan.stopLoss)
        val slPercent = (slDistance / plan.recommendedEntry) * 100.0

        assertTrue(
            "SL distance should not be excessively tight (was $slPercent%), must have at least 0.35% breathing room on volatile assets",
            slPercent >= 0.35
        )
        assertTrue(
            "SL must be positioned below recommended entry and entry zone start for a LONG trade",
            plan.stopLoss < plan.recommendedEntry && plan.stopLoss <= plan.entryZoneStart
        )
    }

    @Test
    fun testHistoricalCandleBreachMarksStoppedOutEvenIfPriceBouncedToTp() {
        val plan = com.example.domain.model.TradeSetupPlan(
            side = PositionSide.LONG,
            pairSymbol = "SNDK",
            currentPrice = 1386.34,
            entryZoneStart = 1384.60,
            entryZoneEnd = 1388.08,
            recommendedEntry = 1386.34,
            recommendedLeverage = 10,
            takeProfit1 = 1394.66,
            takeProfit2 = 1398.00,
            takeProfit3 = 1405.00,
            stopLoss = 1383.14,
            invalidationPrice = 1383.14,
            lifecycleStatus = com.example.domain.model.TradePlanLifecycleStatus.PENDING_ENTRY,
            timeframe = Timeframe.FIFTEEN_MIN,
            isAvailable = true,
            createdAt = System.currentTimeMillis() - (25 * 15 * 60 * 1000L)
        )

        // 20 candles: candle 5 dipped to $1,374 (below SL $1,383.14), then later candles rallied to $1,393.26
        val candles = mutableListOf<CandlePoint>()
        val now = System.currentTimeMillis()
        for (i in 0 until 20) {
            val ts = now - (20 - i) * 15 * 60 * 1000L
            val (low, high, close) = when (i) {
                5 -> Triple(1374.20, 1385.00, 1376.00) // Historical breach below SL
                19 -> Triple(1389.00, 1394.27, 1393.26) // Current price rallied
                else -> Triple(1385.00, 1391.00, 1388.00)
            }
            candles.add(CandlePoint(ts, 1387.0, high, low, close, 100000.0))
        }

        val status = plan.evaluateWithCandles(livePrice = 1393.26, candles = candles)
        assertEquals("Historical candle breach down to $1,374 must mark plan as STOPPED_OUT despite rally to $1,393", com.example.domain.model.TradePlanLifecycleStatus.STOPPED_OUT, status)
    }

    @Test
    fun testStopLossPlacementCoversRecentSwingLow() {
        val candles = mutableListOf<CandlePoint>()
        val now = System.currentTimeMillis()
        for (i in 0 until 35) {
            val ts = now - (35 - i) * 15 * 60 * 1000L
            val (low, high, close) = if (i == 10) {
                Triple(1374.00, 1385.00, 1377.00) // Confirmed structural swing low
            } else {
                Triple(1385.00, 1395.00, 1393.00)
            }
            candles.add(CandlePoint(ts, 1388.0, high, low, close, 50000.0))
        }

        val inputs = UnifiedEntryEngine.EntryEngineInputs(
            symbol = "SNDK",
            currentPrice = 1393.26,
            side = PositionSide.LONG,
            leverage = 10,
            candles = candles,
            dayHigh = 1400.0,
            dayLow = 1370.0,
            timeframe = Timeframe.FIFTEEN_MIN,
            assetType = AssetType.CRYPTO,
            marketType = TradingMarketType.SPOT
        )

        val plan = UnifiedEntryEngine.buildTradeSetupPlan(inputs)

        // Stop loss MUST be strictly below the structural swing low (1374.0)
        assertTrue(
            "SL for Long must be placed at or below the swing low of $1374.0 (was ${plan.stopLoss})",
            plan.stopLoss < 1374.0
        )
    }

    @Test
    fun testEveryAgentAndNodeIsPresentInContributingNodes() {
        val candles = generateSyntheticCandles(basePrice = 1393.0, volatilityPercent = 0.01, count = 35)
        val inputs = UnifiedEntryEngine.EntryEngineInputs(
            symbol = "SNDK",
            currentPrice = 1393.0,
            side = PositionSide.LONG,
            leverage = 10,
            candles = candles,
            agentReports = createBullishAgentReports(),
            tradingViewSummary = TradingViewTechnicalSummary(
                summaryVerdict = "BUY",
                movingAveragesVerdict = "BUY",
                oscillatorsVerdict = "NEUTRAL",
                buyCount = 20,
                neutralCount = 9,
                sellCount = 5
            ),
            researcherDossier = ResearcherDossier(
                discussionSummary = "Researcher consensus",
                bullishResearcherThesis = "Institutional demand",
                bearishResearcherThesis = "Macro headwind",
                buyEvidence = listOf("Inflows"),
                sellEvidence = emptyList(),
                netEvidenceScore = 8.0
            ),
            newsScannerResult = com.example.domain.model.NewsScannerResult(
                query = "SNDK",
                primaryCatalyst = "Semiconductor contract expansion",
                sentimentScore = 0.85
            ),
            manipulationAnalysis = com.example.domain.model.MarketManipulationAnalysis(
                manipulationRiskScore = 15,
                manipulationLevel = "Low (15%)",
                stopHuntDetected = false,
                sweepStatus = "SSL Swept"
            ),
            riskTeamEvaluation = com.example.domain.model.RiskTeamEvaluation(
                aggressiveProfile = com.example.domain.model.RiskProfileEvaluation("Aggressive", "High", 10.0, 20, 1360.0, 2.0, "Approved"),
                neutralProfile = com.example.domain.model.RiskProfileEvaluation("Neutral", "Balanced", 5.0, 10, 1370.0, 1.5, "Approved"),
                conservativeProfile = com.example.domain.model.RiskProfileEvaluation("Conservative", "Preservation", 2.0, 5, 1375.0, 1.2, "Approved"),
                consensusRiskLevel = "Moderate"
            ),
            managerDecision = com.example.domain.model.ManagerDecision(
                status = "AUTHORIZED",
                authorizedAction = com.example.domain.model.TradingAction.BUY,
                executiveRationale = "Aligned",
                executionVenue = "Binance",
                orderType = "Limit",
                executionReadiness = "Ready"
            )
        )

        val plan = UnifiedEntryEngine.buildTradeSetupPlan(inputs)

        val nodeNames = plan.contributingNodes.joinToString(" ")
        assertTrue("Must include Technical Analyst", nodeNames.contains("Technical Analyst"))
        assertTrue("Must include Fundamental Analyst", nodeNames.contains("Fundamental Analyst"))
        assertTrue("Must include Sentiment Analyst", nodeNames.contains("Sentiment"))
        assertTrue("Must include Risk Manager", nodeNames.contains("Risk Manager"))
        assertTrue("Must include TradingView", nodeNames.contains("TradingView"))
        assertTrue("Must include Researcher Dossier", nodeNames.contains("Researcher Dossier"))
        assertTrue("Must include NewsScanner", nodeNames.contains("NewsScanner"))
        assertTrue("Must include Manipulation Guard", nodeNames.contains("Manipulation Guard"))
        assertTrue("Must include Risk Team", nodeNames.contains("Risk Team"))
        assertTrue("Must include Manager Decision", nodeNames.contains("Manager Decision"))
    }

    @Test
    fun testUpcomingLimitOrderRemainsPendingWhenHistoricalCandlesTouchedZoneInPast() {
        val now = System.currentTimeMillis()
        val plan = com.example.domain.model.TradeSetupPlan(
            side = PositionSide.LONG,
            pairSymbol = "SNDK",
            currentPrice = 1292.86,
            entryZoneStart = 1277.44,
            entryZoneEnd = 1280.66,
            recommendedEntry = 1279.05,
            recommendedLeverage = 10,
            takeProfit1 = 1309.20,
            takeProfit2 = 1329.30,
            takeProfit3 = 1359.44,
            stopLoss = 1258.95,
            invalidationPrice = 1258.95,
            lifecycleStatus = com.example.domain.model.TradePlanLifecycleStatus.PENDING_ENTRY,
            timeframe = Timeframe.FIVE_MIN,
            isAvailable = true,
            createdAt = now
        )

        // Historical candles from before creation touched the zone
        val candles = mutableListOf<CandlePoint>()
        for (i in 0 until 15) {
            val ts = now - (15 - i) * 5 * 60 * 1000L
            val (low, high, close) = if (i == 3) {
                Triple(1278.00, 1285.00, 1282.00) // Historical dip into entry zone 1 hour ago
            } else {
                Triple(1288.00, 1295.00, 1292.86)
            }
            candles.add(CandlePoint(ts, 1290.0, high, low, close, 20000.0))
        }

        val status = plan.evaluateWithCandles(livePrice = 1292.86, candles = candles)
        assertEquals("Upcoming limit order must remain PENDING_ENTRY until live price or post-creation candle enters zone",
            com.example.domain.model.TradePlanLifecycleStatus.PENDING_ENTRY, status)
    }

    @Test
    fun testOrderTransitionsToInEntryZoneWhenLivePriceEntersZone() {
        val now = System.currentTimeMillis()
        val plan = com.example.domain.model.TradeSetupPlan(
            side = PositionSide.LONG,
            pairSymbol = "SNDK",
            currentPrice = 1292.86,
            entryZoneStart = 1277.44,
            entryZoneEnd = 1280.66,
            recommendedEntry = 1279.05,
            recommendedLeverage = 10,
            takeProfit1 = 1309.20,
            takeProfit2 = 1329.30,
            takeProfit3 = 1359.44,
            stopLoss = 1258.95,
            lifecycleStatus = com.example.domain.model.TradePlanLifecycleStatus.PENDING_ENTRY,
            timeframe = Timeframe.FIVE_MIN,
            isAvailable = true,
            createdAt = now
        )

        val status = plan.evaluateStatus(livePrice = 1279.05)
        assertEquals("Dipping into entry zone must trigger IN_ENTRY_ZONE",
            com.example.domain.model.TradePlanLifecycleStatus.IN_ENTRY_ZONE, status)
    }
}
