package com.example.domain.engine

import com.example.domain.analytics.QuantitativeEngine
import com.example.domain.daytrader.model.TimeWindow
import com.example.domain.model.AssetType
import com.example.domain.model.BestTimeframeSummary
import com.example.domain.model.CandidateSetup
import com.example.domain.model.CandlePoint
import com.example.domain.model.CryptoFuturesData
import com.example.domain.model.EntryModel
import com.example.domain.model.LiquidityType
import com.example.domain.model.MarketQuote
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSessionStatus
import com.example.domain.model.NewsItem
import com.example.domain.model.NewsScannerResult
import com.example.domain.model.OffHoursMode
import com.example.domain.model.OrderBlockType
import com.example.domain.model.OrderFlowReport
import com.example.domain.model.PositionSide
import com.example.domain.model.ScenarioPlan
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalEngineOutput
import com.example.domain.model.SignalFamily
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.SoftFlag
import com.example.domain.model.SoftFlagType
import com.example.domain.model.TechnicalIndicators
import com.example.domain.model.Timeframe
import com.example.domain.model.TimeframeTriplet
import com.example.domain.model.TradingMarketType
import com.example.domain.util.PriceFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Signal Engine v2: Institutional-Grade Signal & Verdict Generation (Actionability Patch)
 * Single source of truth with strict Hard Veto vs Soft Flag separation,
 * Setup Grading (A / B / C_WATCH), always-numeric conditional WAIT plans, and best-TF triplet evaluation.
 */
object SignalEngineV2 {

    data class SignalEngineInputs(
        val symbol: String,
        val currentPrice: Double,
        val timeframe: Timeframe = Timeframe.FIFTEEN_MIN,
        val marketType: TradingMarketType = TradingMarketType.SPOT,
        val assetType: AssetType = AssetType.CRYPTO,
        val leverage: Int = 10,
        val candles: List<CandlePoint> = emptyList(),
        val higherTimeframeCandles: List<CandlePoint> = emptyList(),
        val orderFlowReport: OrderFlowReport? = null,
        val futuresData: CryptoFuturesData? = null,
        val news: List<NewsItem> = emptyList(),
        val scannerResult: NewsScannerResult? = null,
        val offHoursMode: OffHoursMode = OffHoursMode.CAUTION,
        val spreadPercent: Double = 0.01,
        val perpMarkPrice: Double? = null,
        val spotPrice: Double? = null,
        val highImpactEventWithin30Min: Boolean = false,
        val futuresPerpAvailable: Boolean = (assetType == AssetType.CRYPTO),
        val currentTimeMillis: Long = System.currentTimeMillis(),
        val customStockEntryWindows: List<TimeWindow>? = null,
        val preferredSide: PositionSide? = null,
        val aiShiftPoints: Int = 0,
        val aiShiftModelName: String? = null,
        val dataMixedFromSpot: Boolean = false,
        val newsShockCaution: Boolean = false
    )

    fun generateSignal(inputs: SignalEngineInputs): SignalEngineOutput {
        val rawCandles = inputs.candles.filter {
            it.isClosed &&
                    !it.open.isNaN() && !it.high.isNaN() && !it.low.isNaN() && !it.close.isNaN() &&
                    it.open > 0.0 && it.high > 0.0 && it.low > 0.0 && it.close > 0.0 &&
                    it.high >= it.low
        }
        val barsLoaded = rawCandles.size
        val safePrice = if (inputs.currentPrice > 0.0 && !inputs.currentPrice.isNaN() && !inputs.currentPrice.isInfinite()) {
            inputs.currentPrice
        } else {
            rawCandles.lastOrNull()?.close ?: 0.0
        }
        val clampedAiShift = inputs.aiShiftPoints.coerceIn(-15, 15)

        // History Guard check: if setup TF has < 100 bars, veto immediately (hard safety gate)
        if (barsLoaded < 100 || safePrice <= 0.0) {
            val sessionStatus = SignalVetoDetector.determineSessionStatus(inputs.symbol, inputs.assetType, inputs.currentTimeMillis)
            val why = if (safePrice <= 0.0) {
                "Data Integrity Veto: Invalid live price for ${inputs.symbol}."
            } else {
                "History Guard Veto: Insufficient history ($barsLoaded/100 completed bars required)."
            }
            val scenarioRef = if (safePrice > 0.0) safePrice else 100.0
            return SignalEngineOutput(
                verdictState = SignalVerdictState.NO_TRADE,
                symbol = inputs.symbol,
                currentLivePrice = safePrice,
                primarySetup = null,
                bullScenario = ScenarioPlan(
                    isBull = true,
                    isActive = false,
                    triggerCondition = "Bullish MSS pending 100+ bar history",
                    invalidationLevel = if (safePrice > 0.0) scenarioRef * 0.98 else 0.0,
                    targetLevel = if (safePrice > 0.0) scenarioRef * 1.03 else 0.0,
                    narrative = "Waiting for data feed history."
                ),
                bearScenario = ScenarioPlan(
                    isBull = false,
                    isActive = false,
                    triggerCondition = "Bearish MSS pending 100+ bar history",
                    invalidationLevel = if (safePrice > 0.0) scenarioRef * 1.02 else 0.0,
                    targetLevel = if (safePrice > 0.0) scenarioRef * 0.97 else 0.0,
                    narrative = "Waiting for data feed history."
                ),
                vetoReasons = listOf(why),
                oneLineWhy = why,
                sessionStatus = sessionStatus,
                calibratedConfidence = 0,
                marketRegime = MarketRegime.DEAD,
                setupGrade = null,
                softFlags = emptyList(),
                needsCondition = why,
                barsLoaded = barsLoaded,
                offHoursMode = inputs.offHoursMode,
                aiShiftPoints = clampedAiShift,
                aiShiftModelName = inputs.aiShiftModelName
            )
        }

        val candles = rawCandles.sortedBy { it.timestamp }
        val htfCandles = if (inputs.higherTimeframeCandles.size >= 15) inputs.higherTimeframeCandles.sortedBy { it.timestamp } else emptyList()

        val indicators = QuantitativeEngine.computeIndicators(candles)
        val htfIndicators = if (htfCandles.isNotEmpty()) QuantitativeEngine.computeIndicators(htfCandles) else null

        val atr = indicators.averageTrueRange.coerceAtLeast(safePrice * 0.001)
        val regime = SignalVetoDetector.classifyMarketRegime(indicators)
        val offHoursPolicy = inputs.offHoursMode

        val expansionRange = if (candles.size >= 20) {
            val recent20 = candles.takeLast(20)
            (recent20.maxOf { it.high } - recent20.minOf { it.low }) * 0.5
        } else {
            atr * 1.5
        }
        val plannedStopRef = if (regime == MarketRegime.DEAD) {
            maxOf(atr * 1.5, expansionRange, safePrice * 0.004)
        } else {
            atr * 1.5
        }

        // Evaluate L0 Vetoes (Hard Safety vs Soft Quality Flags)
        val sideForVetoCheck = inputs.preferredSide ?: PositionSide.LONG
        val vetoResult = SignalVetoDetector.evaluateL0Vetoes(
            symbol = inputs.symbol,
            side = sideForVetoCheck,
            assetType = inputs.assetType,
            marketType = inputs.marketType,
            livePrice = safePrice,
            candles = candles,
            indicators = indicators,
            timeframe = inputs.timeframe,
            orderFlow = inputs.orderFlowReport,
            offHoursMode = offHoursPolicy,
            perpPrice = inputs.perpMarkPrice ?: safePrice,
            spotPrice = inputs.spotPrice ?: safePrice,
            spreadPercent = inputs.spreadPercent,
            plannedStopDistance = plannedStopRef,
            highImpactEventWithin30Min = inputs.highImpactEventWithin30Min,
            futuresPerpAvailable = inputs.futuresPerpAvailable,
            dataMixedFromSpot = inputs.dataMixedFromSpot,
            currentTimeMillis = inputs.currentTimeMillis,
            customStockEntryWindows = inputs.customStockEntryWindows,
            newsShockCaution = inputs.newsShockCaution
        )

        // Base scenarios bounded cleanly within min(5x ATR, 25%) from live price
        val maxOffset = minOf(atr * 5.0, safePrice * 0.25)
        val recentCandles = candles.takeLast(minOf(30, candles.size))
        val rawSwingHigh = recentCandles.maxOf { it.high }
        val rawSwingLow = recentCandles.minOf { it.low }

        val safeSwingHigh = rawSwingHigh.coerceIn(safePrice - maxOffset, safePrice + maxOffset)
        val safeSwingLow = rawSwingLow.coerceIn(safePrice - maxOffset, safePrice + maxOffset)

        val baseBullTrigger = (safeSwingHigh * 0.998).coerceIn(safePrice - maxOffset, safePrice + maxOffset)
        val baseBullInvalidation = (safeSwingLow * 0.995).coerceIn(safePrice - maxOffset, safePrice + maxOffset)
        val baseBullTarget = (safeSwingHigh * 1.025).coerceIn(safePrice - maxOffset, safePrice + maxOffset)

        val baseBearTrigger = (safeSwingLow * 1.002).coerceIn(safePrice - maxOffset, safePrice + maxOffset)
        val baseBearInvalidation = (safeSwingHigh * 1.005).coerceIn(safePrice - maxOffset, safePrice + maxOffset)
        val baseBearTarget = (safeSwingLow * 0.975).coerceIn(safePrice - maxOffset, safePrice + maxOffset)

        val defaultSide = if (inputs.marketType != TradingMarketType.SPOT && indicators.rsi14 < 45.0) PositionSide.SHORT else PositionSide.LONG
        val baselineFamilyResult = SignalFamilyScorer.evaluateFamilies(
            candidateSide = defaultSide,
            quote = MarketQuote(
                symbol = inputs.symbol,
                name = inputs.symbol,
                assetType = inputs.assetType,
                marketType = inputs.marketType,
                currentPrice = safePrice,
                change = 0.0,
                changePercent = 0.0,
                open = safePrice,
                dayHigh = safeSwingHigh,
                dayLow = safeSwingLow,
                volume = candles.lastOrNull()?.volume ?: 100000.0,
                candles = candles,
                barsLoadedCount = barsLoaded
            ),
            candles = candles,
            indicators = indicators,
            htfIndicators = htfIndicators,
            orderFlow = inputs.orderFlowReport,
            futuresData = inputs.futuresData,
            news = inputs.news,
            scannerResult = inputs.scannerResult,
            regime = regime
        )

        val baseBullScenario = ScenarioPlan(
            isBull = true,
            isActive = indicators.rsi14 >= 45.0,
            triggerCondition = "Bullish MSS / CHoCH confirmation with displacement above ${PriceFormatter.formatCurrency(baseBullTrigger)} and RVOL >= 1.2",
            invalidationLevel = baseBullInvalidation,
            targetLevel = baseBullTarget,
            narrative = "Reclaim of demand zone with institutional absorption targeting upper liquidity."
        )
        val baseBearScenario = ScenarioPlan(
            isBull = false,
            isActive = indicators.rsi14 < 45.0,
            triggerCondition = "Bearish MSS / CHoCH confirmation with rejection below ${PriceFormatter.formatCurrency(baseBearTrigger)} and RVOL >= 1.2",
            invalidationLevel = baseBearInvalidation,
            targetLevel = baseBearTarget,
            narrative = "Rejection at overhead supply with liquidity sweep targeting sell-side stops."
        )

        // HARD L0 Veto -> NO_TRADE (only genuine safety gates reach here)
        if (vetoResult.isVetoed) {
            return SignalEngineOutput(
                verdictState = SignalVerdictState.NO_TRADE,
                symbol = inputs.symbol,
                currentLivePrice = safePrice,
                primarySetup = null,
                bullScenario = baseBullScenario,
                bearScenario = baseBearScenario,
                vetoReasons = vetoResult.hardVetoes,
                oneLineWhy = vetoResult.hardVetoes.firstOrNull() ?: "Hard L0 Veto active: Market conditions unsafe for capital deployment.",
                familiesAlignedCount = baselineFamilyResult.alignedCount,
                totalFamiliesCount = 7,
                alignedFamilies = baselineFamilyResult.alignedFamilies,
                allFamilyVotes = baselineFamilyResult.votes,
                conflicts = baselineFamilyResult.conflicts,
                sessionStatus = vetoResult.sessionStatus,
                calibratedConfidence = 0,
                redTeamConflictScore = baselineFamilyResult.redTeamConflictScore,
                marketRegime = regime,
                setupGrade = null,
                softFlags = vetoResult.softFlags,
                needsCondition = vetoResult.hardVetoes.firstOrNull() ?: "",
                barsLoaded = barsLoaded,
                offHoursMode = offHoursPolicy,
                aiShiftPoints = clampedAiShift,
                aiShiftModelName = inputs.aiShiftModelName
            )
        }

        // Context-only timeframes (5D, 1M, 3M, 1Y): show full analysis + scenarios + WAIT ("Best now" via triplet)
        val isContextOnlyTf = inputs.timeframe == Timeframe.FIVE_DAYS ||
            inputs.timeframe == Timeframe.ONE_MONTH ||
            inputs.timeframe == Timeframe.THREE_MONTHS ||
            inputs.timeframe == Timeframe.ONE_YEAR

        if (isContextOnlyTf) {
            return SignalEngineOutput(
                verdictState = SignalVerdictState.WAIT,
                symbol = inputs.symbol,
                currentLivePrice = safePrice,
                primarySetup = null,
                bullScenario = baseBullScenario,
                bearScenario = baseBearScenario,
                vetoReasons = emptyList(),
                oneLineWhy = "${inputs.timeframe.label} is context-only (higher-timeframe bias & scenarios). Check Best now from 1D / 4h / 1h.",
                familiesAlignedCount = baselineFamilyResult.alignedCount,
                totalFamiliesCount = 7,
                alignedFamilies = baselineFamilyResult.alignedFamilies,
                allFamilyVotes = baselineFamilyResult.votes,
                conflicts = baselineFamilyResult.conflicts,
                sessionStatus = vetoResult.sessionStatus,
                calibratedConfidence = 0,
                redTeamConflictScore = baselineFamilyResult.redTeamConflictScore,
                marketRegime = regime,
                setupGrade = SetupGrade.C_WATCH,
                softFlags = vetoResult.softFlags,
                needsCondition = "Select 1D, 4h, or 1h (or tap Best now) for an actionable entry plan.",
                barsLoaded = barsLoaded,
                isContextOnly = true,
                offHoursMode = offHoursPolicy,
                aiShiftPoints = clampedAiShift,
                aiShiftModelName = inputs.aiShiftModelName
            )
        }

        // Generate Candidates for BOTH directions (shorts route via Futures perp when available!)
        val longCandidates = generateCandidatesForSide(
            side = PositionSide.LONG,
            safePrice = safePrice,
            candles = candles,
            indicators = indicators,
            htfIndicators = htfIndicators,
            inputs = inputs,
            regime = regime,
            vetoResult = vetoResult,
            atr = atr
        )

        val shortCandidates = if (inputs.marketType != TradingMarketType.SPOT || inputs.futuresPerpAvailable) {
            generateCandidatesForSide(
                side = PositionSide.SHORT,
                safePrice = safePrice,
                candles = candles,
                indicators = indicators,
                htfIndicators = htfIndicators,
                inputs = inputs,
                regime = regime,
                vetoResult = vetoResult,
                atr = atr
            )
        } else emptyList()

        val bestLong = longCandidates.maxByOrNull { it.expectedValueR }
        val bestShort = shortCandidates.maxByOrNull { it.expectedValueR }
        val bestCandidate = when (inputs.preferredSide) {
            PositionSide.LONG -> bestLong ?: bestShort
            PositionSide.SHORT -> bestShort ?: bestLong
            null -> (longCandidates + shortCandidates).maxByOrNull { it.expectedValueR }
        }

        if (bestCandidate == null) {
            val metricsStr = SignalVetoDetector.formatRegimeMetrics(indicators)
            val reason = "Invalid market geometry for entry planning. $metricsStr"
            return SignalEngineOutput(
                verdictState = SignalVerdictState.NO_TRADE,
                symbol = inputs.symbol,
                currentLivePrice = safePrice,
                primarySetup = null,
                bullScenario = baseBullScenario,
                bearScenario = baseBearScenario,
                vetoReasons = listOf(reason),
                oneLineWhy = reason,
                familiesAlignedCount = baselineFamilyResult.alignedCount,
                totalFamiliesCount = 7,
                alignedFamilies = baselineFamilyResult.alignedFamilies,
                allFamilyVotes = baselineFamilyResult.votes,
                conflicts = baselineFamilyResult.conflicts,
                sessionStatus = vetoResult.sessionStatus,
                calibratedConfidence = 0,
                redTeamConflictScore = baselineFamilyResult.redTeamConflictScore,
                marketRegime = regime,
                setupGrade = null,
                softFlags = vetoResult.softFlags,
                needsCondition = "Verify live quote & bar feed integrity",
                barsLoaded = barsLoaded,
                offHoursMode = offHoursPolicy,
                aiShiftPoints = clampedAiShift,
                aiShiftModelName = inputs.aiShiftModelName
            )
        }

        // Refine scenarios with real numeric candidate levels
        val bullScenario = if (bestLong != null) {
            ScenarioPlan(
                isBull = true,
                isActive = bestCandidate.direction == PositionSide.LONG,
                triggerCondition = bestLong.triggerCondition,
                invalidationLevel = bestLong.stopLossPrice,
                targetLevel = bestLong.takeProfit1,
                narrative = "${bestLong.model.title} LONG: Entry ${PriceFormatter.formatCurrency(bestLong.entryPrice)}, SL ${PriceFormatter.formatCurrency(bestLong.stopLossPrice)}, TP1 ${PriceFormatter.formatCurrency(bestLong.takeProfit1)} (1:${String.format(Locale.US, "%.1f", bestLong.netRiskRewardTp1)})"
            )
        } else baseBullScenario.copy(isActive = false)

        val bearScenario = if (bestShort != null) {
            ScenarioPlan(
                isBull = false,
                isActive = bestCandidate.direction == PositionSide.SHORT,
                triggerCondition = bestShort.triggerCondition,
                invalidationLevel = bestShort.stopLossPrice,
                targetLevel = bestShort.takeProfit1,
                narrative = "${bestShort.model.title} SHORT: Entry ${PriceFormatter.formatCurrency(bestShort.entryPrice)}, SL ${PriceFormatter.formatCurrency(bestShort.stopLossPrice)}, TP1 ${PriceFormatter.formatCurrency(bestShort.takeProfit1)} (1:${String.format(Locale.US, "%.1f", bestShort.netRiskRewardTp1)})"
            )
        } else baseBearScenario.copy(isActive = false)

        // Family Scoring for the best candidate direction
        val familyResult = if (bestCandidate.direction == defaultSide) baselineFamilyResult else SignalFamilyScorer.evaluateFamilies(
            candidateSide = bestCandidate.direction,
            quote = MarketQuote(
                symbol = inputs.symbol,
                name = inputs.symbol,
                assetType = inputs.assetType,
                marketType = inputs.marketType,
                currentPrice = safePrice,
                change = 0.0,
                changePercent = 0.0,
                open = safePrice,
                dayHigh = safeSwingHigh,
                dayLow = safeSwingLow,
                volume = candles.lastOrNull()?.volume ?: 100000.0,
                candles = candles,
                barsLoadedCount = barsLoaded
            ),
            candles = candles,
            indicators = indicators,
            htfIndicators = htfIndicators,
            orderFlow = inputs.orderFlowReport,
            futuresData = inputs.futuresData,
            news = inputs.news,
            scannerResult = inputs.scannerResult,
            regime = regime
        )

        val hasStructureLaggingConflict = familyResult.structureHasFreshBreak &&
                familyResult.structureBreakDirection != null &&
                familyResult.trendMovingAverageDirection != familyResult.structureBreakDirection
        val isRedTeamVetoed = familyResult.redTeamConflictScore >= 0.35

        // Collect all SoftFlags (including candidate geometry / R:R flags)
        val allSoftFlags = (vetoResult.softFlags + bestCandidate.softFlags).toMutableList()
        if (familyResult.isMixedStructure) {
            allSoftFlags.add(
                SoftFlag(
                    type = SoftFlagType.MIXED_STRUCTURE,
                    label = "Mixed structure",
                    detail = "Swing and internal SMC structures point in opposite directions.",
                    actionHint = "Wait for swing and internal structure alignment"
                )
            )
        }
        if (bestCandidate.isAgainstHtfTrend) {
            allSoftFlags.add(
                SoftFlag(
                    type = SoftFlagType.HTF_OPPOSED,
                    label = "HTF opposed",
                    detail = "Setup opposes higher-timeframe EMA50 bias.",
                    actionHint = "Trade 0.5x counter-trend sweep or wait for HTF reclaim"
                )
            )
        }
        if (indicators.rvol < 0.80) {
            allSoftFlags.add(
                SoftFlag(
                    type = SoftFlagType.LOW_RVOL,
                    label = "Low RVOL (${String.format(Locale.US, "%.2f", indicators.rvol)}x)",
                    detail = "Relative volume below 0.80x 14-bar baseline.",
                    actionHint = "Wait for expansion candle with RVOL >= 1.2"
                )
            )
        }
        if (bestCandidate.isUnproven) {
            allSoftFlags.add(
                SoftFlag(
                    type = SoftFlagType.UNPROVEN_MODEL,
                    label = "Unproven model (n=${bestCandidate.sampleSize} < 30)",
                    detail = "Fewer than 30 tracked historical setups for this model/session.",
                    actionHint = "Half-size only until sample size reaches n >= 30"
                )
            )
        }
        if (familyResult.alignedCount < 3) {
            allSoftFlags.add(
                SoftFlag(
                    type = SoftFlagType.LOW_ALIGNMENT,
                    label = "Low confluence (${familyResult.alignedCount}/7 families)",
                    detail = "Fewer than 3 of 7 independent signal families aligned.",
                    actionHint = "Wait for >= 3/7 signal family alignment"
                )
            )
        }
        val replayEst = SignalCalibrationEngine.getReplayPWin(inputs.symbol)
        if (replayEst != null) {
            val be = 1.0 / (1.0 + bestCandidate.netRiskRewardTp1.coerceAtLeast(1.0))
            if (abs(replayEst.pWin - be) <= 0.05) {
                allSoftFlags.add(
                    SoftFlag(
                        type = SoftFlagType.REPLAY_BREAKEVEN,
                        label = "Replay near break-even",
                        detail = "Time Machine (${replayEst.conservativeMode}) sits within 5 pts of break-even.",
                        actionHint = "Wait for stronger asymmetric displacement or pullback"
                    )
                )
            }
        }

        val distinctFlags = allSoftFlags.distinctBy { it.type }
        val nonUnprovenFlags = distinctFlags.filter { it.type != SoftFlagType.UNPROVEN_MODEL }
        val forcesWatchFlag = distinctFlags.any {
            it.type == SoftFlagType.RR_BELOW_1_5 || it.type == SoftFlagType.OUTSIDE_ENTRY_WINDOW
        }

        // Grading rule:
        // A = 0 soft flags & >= 4 families aligned & no conflicts & positive EV (full size 1.0x)
        // B = <= 1 soft flag (or exactly 3 families aligned) & no severe conflicts (0.5x size, UNPROVEN tag if n < 30)
        // C / WATCH = 2+ soft flags, or structure/red-team conflict, or < 3 aligned, or EV <= 0, or R:R/EntryWindow WATCH flag
        val hasBlockingConflict = hasStructureLaggingConflict ||
                isRedTeamVetoed ||
                familyResult.alignedCount < 3 ||
                bestCandidate.expectedValueR <= 0.0 ||
                forcesWatchFlag ||
                (bestCandidate.isUnproven && bestCandidate.netRiskRewardTp1 < 2.0)

        val setupGrade = when {
            hasBlockingConflict || distinctFlags.size >= 2 -> SetupGrade.C_WATCH
            distinctFlags.isEmpty() && familyResult.alignedCount >= 4 -> SetupGrade.A
            else -> SetupGrade.B
        }

        // Needs string (plain English actionable requirement)
        val range20High = candles.takeLast(20).maxOf { it.high }
        val range20Low = candles.takeLast(20).minOf { it.low }
        val isDead = regime == MarketRegime.DEAD
        val isOverExtended = distinctFlags.any { it.type == SoftFlagType.OVER_EXTENDED }
        val outsideWindowFlag = distinctFlags.firstOrNull { it.type == SoftFlagType.OUTSIDE_ENTRY_WINDOW }
        val rrFlag = distinctFlags.firstOrNull { it.type == SoftFlagType.RR_BELOW_1_5 }
        val zoneStr = PriceFormatter.formatZoneRange(bestCandidate.entryZoneMin, bestCandidate.entryZoneMax)

        val needsCondition = when {
            rrFlag != null && bestCandidate.needsCondition.isNotBlank() -> bestCandidate.needsCondition
            outsideWindowFlag != null -> "needs: ${outsideWindowFlag.label}"
            isDead -> "needs: wait for expansion — close above ${PriceFormatter.formatCurrency(range20High)} or below ${PriceFormatter.formatCurrency(range20Low)} with RVOL >= 1.2"
            isOverExtended -> "needs: wait for pullback to EMA20 / VWAP / OB near ${PriceFormatter.formatCurrency(bestCandidate.entryPrice)}"
            hasStructureLaggingConflict -> "needs: pullback into $zoneStr, then ${inputs.timeframe.label} structural close above/below MA trend"
            familyResult.isMixedStructure -> "needs: swing and internal SMC structure agreement on ${inputs.timeframe.label}"
            familyResult.alignedCount < 3 -> "needs: >= 3/7 signal families aligned (currently ${familyResult.alignedCount}/7)"
            bestCandidate.isUnproven && bestCandidate.netRiskRewardTp1 < 2.0 -> "needs: R:R >= 2.0 for unproven model (currently 1:${String.format(Locale.US, "%.1f", bestCandidate.netRiskRewardTp1)})"
            distinctFlags.isNotEmpty() -> "needs: ${distinctFlags.first().actionHint.replaceFirstChar { it.lowercase(Locale.US) }}"
            else -> "Armed for limit entry inside $zoneStr"
        }

        val routedFuturesShort = bestCandidate.routedViaFuturesShort ||
            (bestCandidate.direction == PositionSide.SHORT && inputs.marketType == TradingMarketType.SPOT && inputs.futuresPerpAvailable)
        val perpRoutePrefix = if (routedFuturesShort) "SHORT via Futures (perp) · " else ""

        // Can only emit TRADE on Grade A, or Grade B without price-condition deferral
        val hasPriceDeferralFlag = isDead || isOverExtended || distinctFlags.any { it.type == SoftFlagType.OFF_HOURS_CAUTION || it.type == SoftFlagType.OUTSIDE_ENTRY_WINDOW || it.type == SoftFlagType.RR_BELOW_1_5 }
        val finalVerdictState: SignalVerdictState
        val oneLineWhy: String

        when {
            setupGrade == SetupGrade.C_WATCH -> {
                finalVerdictState = SignalVerdictState.WAIT
                val flagSummary = distinctFlags.take(2).joinToString(", ") { it.label }
                oneLineWhy = "${perpRoutePrefix}WATCH (Grade C): ${bestCandidate.model.title} ${bestCandidate.direction.label} ($flagSummary). ${needsCondition.replaceFirstChar { it.uppercase(Locale.US) }}"
            }
            hasPriceDeferralFlag -> {
                finalVerdictState = SignalVerdictState.WAIT
                oneLineWhy = "${perpRoutePrefix}WAIT (Grade ${if (setupGrade == SetupGrade.B) "B" else "A"}): ${bestCandidate.model.title} conditional plan prepared. ${needsCondition.replaceFirstChar { it.uppercase(Locale.US) }}"
            }
            setupGrade == SetupGrade.B -> {
                finalVerdictState = SignalVerdictState.TRADE
                val unprovenSuffix = if (bestCandidate.isUnproven) " • UNPROVEN (n=${bestCandidate.sampleSize}/30)" else ""
                val note = if (nonUnprovenFlags.isNotEmpty()) " (${nonUnprovenFlags.first().label})" else " (3/7 aligned)"
                oneLineWhy = "${perpRoutePrefix}TRADE [Grade B • 0.5x Size$unprovenSuffix]: ${bestCandidate.model.title} (${bestCandidate.direction.label})$note @ ${PriceFormatter.formatCurrency(bestCandidate.entryPrice)} (1:${String.format(Locale.US, "%.1f", bestCandidate.netRiskRewardTp1)})."
            }
            else -> {
                finalVerdictState = SignalVerdictState.TRADE
                oneLineWhy = "${perpRoutePrefix}TRADE [Grade A • 1.0x Size]: ${bestCandidate.model.title} armed (${bestCandidate.direction.label}) with ${familyResult.alignedCount}/7 families aligned, net R:R 1:${String.format(Locale.US, "%.1f", bestCandidate.netRiskRewardTp1)}."
            }
        }

        val gradedSizeMultiplier = if (setupGrade == SetupGrade.B) 0.5 else if (setupGrade == SetupGrade.C_WATCH) 0.0 else 1.0
        val adjustedSizePct = if (setupGrade == SetupGrade.B) {
            minOf(bestCandidate.recommendedPositionSizePercent, 12.5)
        } else {
            bestCandidate.recommendedPositionSizePercent * gradedSizeMultiplier.coerceAtLeast(0.5)
        }

        val evidence = SignalCalibrationEngine.getEvidenceForModel(
            symbol = inputs.symbol,
            model = bestCandidate.model,
            regime = regime,
            isWithTrend = !bestCandidate.isAgainstHtfTrend,
            rMultiple = bestCandidate.netRiskRewardTp1,
            sessionStatus = vetoResult.sessionStatus
        )

        val enrichedCandidate = bestCandidate.copy(
            alignedFamiliesCount = familyResult.alignedCount,
            alignedFamilies = familyResult.alignedFamilies,
            familyVotes = familyResult.votes,
            conflicts = familyResult.conflicts,
            redTeamConflictScore = familyResult.redTeamConflictScore,
            recommendedPositionSizePercent = adjustedSizePct,
            setupGrade = setupGrade,
            softFlags = distinctFlags,
            needsCondition = needsCondition,
            routedViaFuturesShort = routedFuturesShort
        )

        val adjustedConfidence = ((bestCandidate.pWin * 100.0).roundToInt() + clampedAiShift).coerceIn(5, 95)

        return SignalEngineOutput(
            verdictState = finalVerdictState,
            symbol = inputs.symbol,
            currentLivePrice = safePrice,
            primarySetup = enrichedCandidate,
            bullScenario = bullScenario,
            bearScenario = bearScenario,
            vetoReasons = emptyList(),
            oneLineWhy = oneLineWhy,
            familiesAlignedCount = familyResult.alignedCount,
            totalFamiliesCount = 7,
            alignedFamilies = familyResult.alignedFamilies,
            allFamilyVotes = familyResult.votes,
            conflicts = familyResult.conflicts,
            evidence = evidence,
            sessionStatus = vetoResult.sessionStatus,
            calibratedConfidence = adjustedConfidence,
            redTeamConflictScore = familyResult.redTeamConflictScore,
            marketRegime = regime,
            setupGrade = setupGrade,
            softFlags = distinctFlags,
            needsCondition = needsCondition,
            barsLoaded = barsLoaded,
            routedViaFuturesShort = routedFuturesShort,
            offHoursMode = offHoursPolicy,
            aiShiftPoints = clampedAiShift,
            aiShiftModelName = inputs.aiShiftModelName
        )
    }

    /**
     * Evaluates the chart timeframe against three structured intraday/swing triplets:
     * (5m / 15m / 1h), (15m / 1h / 4h), and (1h / 4h / 1D)
     * and answers "Best now: <TF> <side> <model>, grade <A/B/C>" + "Why not <chart TF>?"
     */
    fun evaluateTimeframeTriplets(
        symbol: String,
        chartTimeframe: Timeframe,
        quotesByTf: Map<Timeframe, MarketQuote>,
        marketType: TradingMarketType,
        assetType: AssetType,
        leverage: Int = 10,
        offHoursMode: OffHoursMode = OffHoursMode.CAUTION,
        futuresPerpAvailable: Boolean = (assetType == AssetType.CRYPTO),
        customStockEntryWindows: List<TimeWindow>? = null
    ): BestTimeframeSummary? {
        val triplets = listOf(
            TimeframeTriplet(Timeframe.ONE_HOUR, Timeframe.FIFTEEN_MIN, Timeframe.FIVE_MIN),
            TimeframeTriplet(Timeframe.FOUR_HOURS, Timeframe.ONE_HOUR, Timeframe.FIFTEEN_MIN),
            TimeframeTriplet(Timeframe.ONE_DAY, Timeframe.FOUR_HOURS, Timeframe.ONE_HOUR)
        )

        val evaluated = mutableListOf<Pair<Timeframe, SignalEngineOutput>>()
        for (triplet in triplets) {
            val triggerQuote = quotesByTf[triplet.triggerTf] ?: continue
            val setupQuote = quotesByTf[triplet.setupTf] ?: triggerQuote
            val biasQuote = quotesByTf[triplet.biasTf]
            if (triggerQuote.candles.count { it.isClosed } < 100) continue

            val out = generateSignal(
                SignalEngineInputs(
                    symbol = symbol,
                    currentPrice = triggerQuote.currentPrice,
                    timeframe = triplet.triggerTf,
                    marketType = marketType,
                    assetType = assetType,
                    leverage = leverage,
                    candles = triggerQuote.candles,
                    higherTimeframeCandles = biasQuote?.candles ?: setupQuote.candles,
                    futuresData = triggerQuote.futuresData,
                    offHoursMode = offHoursMode,
                    futuresPerpAvailable = futuresPerpAvailable,
                    customStockEntryWindows = customStockEntryWindows
                )
            )
            evaluated.add(triplet.triggerTf to out)
        }

        // Also ensure chartTimeframe itself is represented if not in the 3 trigger slots (e.g., 30m, 4h, 1D)
        if (evaluated.none { it.first == chartTimeframe }) {
            quotesByTf[chartTimeframe]?.let { cq ->
                if (cq.candles.count { it.isClosed } >= 100) {
                    val triplet = TimeframeTriplet.fromTriggerTimeframe(chartTimeframe)
                    val htf = quotesByTf[triplet.biasTf]?.candles ?: quotesByTf[triplet.setupTf]?.candles ?: emptyList()
                    val out = generateSignal(
                        SignalEngineInputs(
                            symbol = symbol,
                            currentPrice = cq.currentPrice,
                            timeframe = chartTimeframe,
                            marketType = marketType,
                            assetType = assetType,
                            leverage = leverage,
                            candles = cq.candles,
                            higherTimeframeCandles = htf,
                            futuresData = cq.futuresData,
                            offHoursMode = offHoursMode,
                            futuresPerpAvailable = futuresPerpAvailable,
                            customStockEntryWindows = customStockEntryWindows
                        )
                    )
                    evaluated.add(chartTimeframe to out)
                }
            }
        }

        val viable = evaluated.filter { it.second.primarySetup != null && it.second.verdictState != SignalVerdictState.NO_TRADE }
        if (viable.isEmpty()) return null

        val bestPair = viable.sortedWith(
            compareBy<Pair<Timeframe, SignalEngineOutput>> {
                when (it.second.setupGrade) {
                    SetupGrade.A -> 0
                    SetupGrade.B -> 1
                    SetupGrade.C_WATCH -> 2
                    null -> 3
                }
            }
                .thenBy { if (it.second.verdictState == SignalVerdictState.TRADE) 0 else 1 }
                .thenByDescending { it.second.familiesAlignedCount }
                .thenByDescending { it.second.primarySetup?.expectedValueR ?: -99.0 }
        ).first()

        val bestTf = bestPair.first
        val bestOut = bestPair.second
        val bestSetup = bestOut.primarySetup ?: return null
        val bestGrade = bestOut.setupGrade ?: SetupGrade.C_WATCH

        val sideWord = if (bestSetup.direction == PositionSide.LONG) "LONG" else "SHORT"
        val gradeWord = when (bestGrade) {
            SetupGrade.A -> "A"
            SetupGrade.B -> "B"
            SetupGrade.C_WATCH -> "C / WATCH"
        }
        val headline = "Best now: ${bestTf.label} $sideWord ${bestSetup.model.title}, Grade $gradeWord"

        val chartOut = evaluated.firstOrNull { it.first == chartTimeframe }?.second
        val whyNotChartTf = when {
            bestTf == chartTimeframe -> "${chartTimeframe.label} is already the highest-quality setup right now."
            chartOut == null -> "${chartTimeframe.label}: waiting for 100+ closed bars to grade."
            chartOut.verdictState == SignalVerdictState.NO_TRADE -> "${chartTimeframe.label} blocked: ${chartOut.vetoReasons.firstOrNull() ?: chartOut.oneLineWhy}"
            chartOut.softFlags.isNotEmpty() -> "${chartTimeframe.label} held at ${chartOut.setupGrade?.label ?: "WAIT"} due to ${chartOut.softFlags.take(2).joinToString(", ") { it.label }}."
            else -> "${chartTimeframe.label} has lower family alignment (${chartOut.familiesAlignedCount}/7) than ${bestTf.label} (${bestOut.familiesAlignedCount}/7)."
        }

        return BestTimeframeSummary(
            bestTf = bestTf,
            side = bestSetup.direction,
            modelTitle = bestSetup.model.title,
            grade = bestGrade,
            headline = headline,
            whyNotChartTf = whyNotChartTf
        )
    }

    private fun generateCandidatesForSide(
        side: PositionSide,
        safePrice: Double,
        candles: List<CandlePoint>,
        indicators: TechnicalIndicators,
        htfIndicators: TechnicalIndicators?,
        inputs: SignalEngineInputs,
        regime: MarketRegime,
        vetoResult: SignalVetoDetector.VetoCheckResult,
        atr: Double
    ): List<CandidateSetup> {
        val candidates = mutableListOf<CandidateSetup>()
        val isLong = side == PositionSide.LONG

        val htfEma50 = htfIndicators?.ema50?.takeIf { it > 0.0 } ?: indicators.ema50
        val isHtfAligned = if (htfEma50 > 0.0) {
            if (isLong) safePrice > htfEma50 else safePrice < htfEma50
        } else true
        val isHtfOpposed = !isHtfAligned

        val hasOverExtension = vetoResult.softFlags.any { it.type == SoftFlagType.OVER_EXTENDED }

        // Evaluate all natural models for this regime
        for (model in EntryModel.values()) {
            val allowedInRegime = when {
                regime == MarketRegime.DEAD -> model == EntryModel.C_BREAKOUT_RETEST || model == EntryModel.D_RANGE_FADE
                hasOverExtension -> model == EntryModel.A_TREND_PULLBACK || model == EntryModel.B_SWEEP_RECLAIM
                else -> regime in model.allowedRegimes
            }
            if (!allowedInRegime) continue

            // If HTF is opposed, allow B_SWEEP_RECLAIM or conditional A_TREND_PULLBACK when needed
            if (isHtfOpposed && model != EntryModel.B_SWEEP_RECLAIM && !hasOverExtension) continue

            buildCandidateForModel(
                model = model,
                side = side,
                safePrice = safePrice,
                candles = candles,
                indicators = indicators,
                inputs = inputs,
                regime = regime,
                vetoResult = vetoResult,
                atr = atr,
                isHtfOpposed = isHtfOpposed
            )?.let { candidates.add(it) }
        }

        // Guarantee at least one conditional fallback setup if market had >= 100 bars and zero natural models fit
        if (candidates.isEmpty()) {
            val fallbackModel = if (regime == MarketRegime.DEAD) EntryModel.C_BREAKOUT_RETEST else EntryModel.A_TREND_PULLBACK
            buildCandidateForModel(
                model = fallbackModel,
                side = side,
                safePrice = safePrice,
                candles = candles,
                indicators = indicators,
                inputs = inputs,
                regime = regime,
                vetoResult = vetoResult,
                atr = atr,
                isHtfOpposed = isHtfOpposed,
                isConditionalFallback = true
            )?.let { candidates.add(it) }
        }

        return candidates
    }

    private fun buildCandidateForModel(
        model: EntryModel,
        side: PositionSide,
        safePrice: Double,
        candles: List<CandlePoint>,
        indicators: TechnicalIndicators,
        inputs: SignalEngineInputs,
        regime: MarketRegime,
        vetoResult: SignalVetoDetector.VetoCheckResult,
        atr: Double,
        isHtfOpposed: Boolean,
        isConditionalFallback: Boolean = false
    ): CandidateSetup? {
        val isLong = side == PositionSide.LONG
        val smc = indicators.smc
        val spread = safePrice * (inputs.spreadPercent / 100.0)
        val rangeHigh = candles.takeLast(20).maxOf { it.high }
        val rangeLow = candles.takeLast(20).minOf { it.low }

        val farZonesList = mutableListOf<String>()
        val entryPrice: Double
        val zoneMin: Double
        val zoneMax: Double
        val triggerCondition: String

        when {
            regime == MarketRegime.DEAD -> {
                val breakoutLevel = if (isLong) rangeHigh else rangeLow
                entryPrice = breakoutLevel.coerceIn(safePrice * 0.85, safePrice * 1.15)
                zoneMin = entryPrice - (atr * 0.2)
                zoneMax = entryPrice + (atr * 0.2)
                val dirWord = if (isLong) "above" else "below"
                triggerCondition = "Expansion trigger: ${inputs.timeframe.label} close $dirWord ${PriceFormatter.formatCurrency(entryPrice)} with RVOL >= 1.2"
            }
            model == EntryModel.A_TREND_PULLBACK -> {
                val allDirectionalObs = if (isLong) {
                    smc?.activeOrderBlocks?.filter { it.type == OrderBlockType.BULLISH_DEMAND_BLOCK && !it.isMitigated }.orEmpty()
                } else {
                    smc?.activeOrderBlocks?.filter { it.type == OrderBlockType.BEARISH_SUPPLY_BLOCK && !it.isMitigated }.orEmpty()
                }
                allDirectionalObs.filter { abs(((it.top + it.bottom) / 2.0) - safePrice) > atr * 5.0 }.forEach { farOb ->
                    farZonesList.add("far: ${farOb.type.label} ${PriceFormatter.formatZoneRange(farOb.bottom, farOb.top)}")
                }
                val ob = allDirectionalObs
                    .filter { abs(((it.top + it.bottom) / 2.0) - safePrice) <= atr * 5.0 }
                    .minByOrNull { abs(((it.top + it.bottom) / 2.0) - safePrice) }

                val vwap = indicators.sessionVwap.takeIf { it > 0.0 } ?: safePrice
                val ema20 = indicators.sma20.takeIf { it > 0.0 } ?: vwap
                val pullbackAnchor = (vwap + ema20) / 2.0

                if (ob != null) {
                    val (clippedMin, clippedMax) = com.example.domain.analytics.QuantitativeEngine.clipZoneToDisplacementEdge(
                        rawMin = ob.bottom,
                        rawMax = ob.top,
                        isLongDemand = isLong,
                        atr = atr
                    )
                    entryPrice = (clippedMin + clippedMax) / 2.0
                    zoneMin = clippedMin
                    zoneMax = clippedMax
                } else {
                    entryPrice = if (isLong) minOf(safePrice, pullbackAnchor) else maxOf(safePrice, pullbackAnchor)
                    zoneMin = entryPrice - (atr * 0.25)
                    zoneMax = entryPrice + (atr * 0.25)
                }
                triggerCondition = "Pullback into ${if (isLong) "Demand / EMA20 / VWAP" else "Supply / EMA20 / VWAP"} near ${PriceFormatter.formatCurrency(entryPrice)}, then LTF MSS confirmation"
            }
            model == EntryModel.B_SWEEP_RECLAIM -> {
                val sweep = smc?.recentLiquiditySweep?.priceLevel?.takeIf { abs(it - safePrice) <= atr * 5.0 }
                if (smc?.recentLiquiditySweep != null && sweep == null) {
                    farZonesList.add("far: Sweep @ ${PriceFormatter.formatCurrency(smc.recentLiquiditySweep.priceLevel)}")
                }
                entryPrice = sweep ?: if (isLong) safePrice - (atr * 0.35) else safePrice + (atr * 0.35)
                zoneMin = entryPrice - (atr * 0.2)
                zoneMax = entryPrice + (atr * 0.2)
                triggerCondition = "Displacement reclaim above/below ${PriceFormatter.formatCurrency(entryPrice)} following ${if (isLong) "SSL" else "BSL"} liquidity sweep"
            }
            model == EntryModel.C_BREAKOUT_RETEST -> {
                val brokenLevel = if (isLong) candles.takeLast(15).maxOf { it.high } else candles.takeLast(15).minOf { it.low }
                entryPrice = brokenLevel
                zoneMin = brokenLevel - (atr * 0.15)
                zoneMax = brokenLevel + (atr * 0.15)
                triggerCondition = "Retest of ${PriceFormatter.formatCurrency(brokenLevel)} breakout with rising ADX and RVOL >= 1.2"
            }
            else -> { // D_RANGE_FADE
                val volProfile = indicators.volumeProfile
                val extreme = if (isLong) (volProfile?.valLevel ?: (safePrice - atr)) else (volProfile?.vah ?: (safePrice + atr))
                entryPrice = extreme.coerceIn(safePrice - minOf(atr * 4.5, safePrice * 0.20), safePrice + minOf(atr * 4.5, safePrice * 0.20))
                zoneMin = entryPrice - (atr * 0.2)
                zoneMax = entryPrice + (atr * 0.2)
                triggerCondition = "Range extreme rejection near ${PriceFormatter.formatCurrency(entryPrice)} back toward Value Area POC"
            }
        }

        if (entryPrice <= 0.0) return null

        val rawZoneWidth = abs(zoneMax - zoneMin)
        val (clippedZoneMin, clippedZoneMax) = com.example.domain.analytics.QuantitativeEngine.clipZoneToDisplacementEdge(
            rawMin = zoneMin,
            rawMax = zoneMax,
            isLongDemand = isLong,
            atr = atr
        )
        val wideZoneTag = if (rawZoneWidth > (atr * 0.75)) " (clipped to displacement edge <= 0.75x ATR)" else ""

        // 2. STOP LOSS CALCULATION (between 1.5x and 3.0x ATR, strictly outside zone's far edge)
        val tickSize = safePrice * 0.0001
        val buffer = maxOf(atr * 0.5, spread * 3.0, tickSize * 5.0) * vetoResult.bufferMultiplier

        val recent5 = candles.takeLast(minOf(5, candles.size))
        val structuralInvalidation = if (isLong) {
            val lowestWick = recent5.minOf { it.low }
            minOf(lowestWick, clippedZoneMin)
        } else {
            val highestWick = recent5.maxOf { it.high }
            maxOf(highestWick, clippedZoneMax)
        }

        var calculatedStop = if (isLong) structuralInvalidation - buffer else structuralInvalidation + buffer

        val pools = smc?.liquidityPools ?: emptyList()
        val relevantPools = if (isLong) {
            pools.filter { it.type == LiquidityType.SELL_SIDE_LIQUIDITY_SSL }
        } else {
            pools.filter { it.type == LiquidityType.BUY_SIDE_LIQUIDITY_BSL }
        }
        for (pool in relevantPools) {
            val poolDist = abs(calculatedStop - pool.priceLevel)
            if (poolDist < buffer * 1.2) {
                calculatedStop = if (isLong) pool.priceLevel - buffer else pool.priceLevel + buffer
            }
        }

        val stopRemainder = abs(calculatedStop % 1.0)
        if (stopRemainder in 0.98..1.0 || stopRemainder in 0.0..0.02 || stopRemainder in 0.48..0.52) {
            calculatedStop = if (isLong) calculatedStop - (tickSize * 3) else calculatedStop + (tickSize * 3)
        }

        indicators.volumeProfile?.lvnList?.forEach { lvn ->
            if (abs(calculatedStop - lvn) < atr * 0.2) {
                calculatedStop = if (isLong) calculatedStop - buffer * 0.5 else calculatedStop + buffer * 0.5
            }
        }

        // Bound structural invalidation distance cleanly inside [1.5x ATR, 3.0x ATR], and NEVER inside zone
        var finalStopDistance = abs(entryPrice - calculatedStop)
        if (finalStopDistance < atr * 1.5) {
            finalStopDistance = atr * 1.5
            calculatedStop = if (isLong) entryPrice - finalStopDistance else entryPrice + finalStopDistance
        } else if (finalStopDistance > atr * 3.0) {
            finalStopDistance = minOf(atr * 2.75, entryPrice * 0.24)
            calculatedStop = if (isLong) entryPrice - finalStopDistance else entryPrice + finalStopDistance
        }
        if (isLong && calculatedStop >= clippedZoneMin) {
            calculatedStop = clippedZoneMin - buffer.coerceAtLeast(tickSize * 5.0)
        } else if (!isLong && calculatedStop <= clippedZoneMax) {
            calculatedStop = clippedZoneMax + buffer.coerceAtLeast(tickSize * 5.0)
        }

        if (calculatedStop <= 0.0) return null

        // 3. TARGET CALCULATION: strictly sorted by distance from entry (TP1 nearest)
        val minTp1Distance = abs(entryPrice - calculatedStop) * 1.6
        val oppExtreme = if (isLong) candles.takeLast(20).maxOf { it.high } else candles.takeLast(20).minOf { it.low }

        val candidateTp1 = if (isLong) maxOf(oppExtreme, entryPrice + minTp1Distance) else minOf(oppExtreme, entryPrice - minTp1Distance)
        val candidateTp2 = if (isLong) entryPrice + (abs(entryPrice - calculatedStop) * 2.5) else entryPrice - (abs(entryPrice - calculatedStop) * 2.5)
        val candidateTp3 = if (isLong) entryPrice + (abs(entryPrice - calculatedStop) * 4.0) else entryPrice - (abs(entryPrice - calculatedStop) * 4.0)

        val sortedTargets = listOf(candidateTp1, candidateTp2, candidateTp3)
            .filter { it > 0.0 }
            .distinct()
            .sortedBy { abs(it - entryPrice) }

        var tp1 = sortedTargets.getOrNull(0) ?: (if (isLong) entryPrice + minTp1Distance else (entryPrice - minTp1Distance).coerceAtLeast(entryPrice * 0.1))
        var tp2 = sortedTargets.getOrNull(1) ?: (if (isLong) tp1 + abs(entryPrice - calculatedStop) * 0.9 else (tp1 - abs(entryPrice - calculatedStop) * 0.9).coerceAtLeast(entryPrice * 0.05))
        var tp3 = sortedTargets.getOrNull(2) ?: (if (isLong) tp2 + abs(entryPrice - calculatedStop) * 1.4 else (tp2 - abs(entryPrice - calculatedStop) * 1.4).coerceAtLeast(entryPrice * 0.02))

        val geometry = resolveGeometryForTargetR(
            entryPrice = entryPrice,
            stopLossPrice = calculatedStop,
            takeProfit1 = tp1,
            takeProfit2 = tp2,
            takeProfit3 = tp3,
            oppExtreme = oppExtreme,
            livePrice = safePrice,
            atr = atr,
            isLong = isLong,
            targetR = 1.50
        )
        val finalEntry = geometry.entryPrice
        val rawAdjMin = if (geometry.wasAdjusted) finalEntry - (atr * 0.15) else clippedZoneMin
        val rawAdjMax = if (geometry.wasAdjusted) finalEntry + (atr * 0.15) else clippedZoneMax
        val (safeAdjMin, safeAdjMax) = com.example.domain.analytics.QuantitativeEngine.clipZoneToDisplacementEdge(
            rawMin = rawAdjMin,
            rawMax = rawAdjMax,
            isLongDemand = isLong,
            atr = atr
        )
        // Strictly ensure stop is never inside adjusted zone
        if (isLong && calculatedStop >= safeAdjMin) {
            calculatedStop = safeAdjMin - maxOf(buffer * 0.5, atr * 0.25, tickSize * 5.0)
        } else if (!isLong && calculatedStop <= safeAdjMax) {
            calculatedStop = safeAdjMax + maxOf(buffer * 0.5, atr * 0.25, tickSize * 5.0)
        }
        if (calculatedStop <= 0.0) return null

        finalStopDistance = abs(finalEntry - calculatedStop)
        tp1 = geometry.tp1
        tp2 = geometry.tp2
        tp3 = geometry.tp3
        val netRrTp1 = if (finalStopDistance > 0.0) abs(tp1 - finalEntry) / finalStopDistance else geometry.netRrTp1
        val netRrTp2 = if (finalStopDistance > 0.0) abs(tp2 - finalEntry) / finalStopDistance else geometry.netRrTp2
        val netRrTp3 = if (finalStopDistance > 0.0) abs(tp3 - finalEntry) / finalStopDistance else geometry.netRrTp3
        val candidateSoftFlags = geometry.softFlags.toMutableList()
        val rrNeedsCondition = geometry.needsCondition

        val stopDistancePct = if (finalEntry > 0.0) (finalStopDistance / finalEntry) * 100.0 else 0.0

        // 4. Calibration & P(win) against zero-edge baseline 1 / (1 + R)
        val isWithTrend = !isHtfOpposed
        val (pWin, sampleCount) = SignalCalibrationEngine.calculateCalibratedPWin(
            symbol = inputs.symbol,
            model = model,
            regime = regime,
            isWithTrend = isWithTrend,
            rMultiple = netRrTp1,
            sessionStatus = vetoResult.sessionStatus
        )

        val isUnproven = sampleCount < 30
        val estimatedCostsInR = (spread * 2.0) / finalStopDistance.coerceAtLeast(1e-6)
        val expectedValueR = (pWin * netRrTp1) - ((1.0 - pWin) * 1.0) - estimatedCostsInR

        var sizePct = if (isHtfOpposed) 12.5 else 25.0
        sizePct *= vetoResult.sizeMultiplier
        val maxSafeLev = ((finalEntry / (3.0 * finalStopDistance.coerceAtLeast(1e-6))).toInt()).coerceIn(1, inputs.leverage)
        val routedFuturesShort = (!isLong && inputs.marketType == TradingMarketType.SPOT && inputs.futuresPerpAvailable)

        return CandidateSetup(
            direction = side,
            model = model,
            regimeNeeded = regime,
            entryPrice = finalEntry,
            entryZoneMin = safeAdjMin,
            entryZoneMax = safeAdjMax,
            stopLossPrice = calculatedStop,
            stopDistancePercent = stopDistancePct,
            stopBufferAmount = buffer,
            takeProfit1 = tp1,
            takeProfit2 = tp2,
            takeProfit3 = tp3,
            netRiskRewardTp1 = netRrTp1,
            netRiskRewardTp2 = netRrTp2,
            netRiskRewardTp3 = netRrTp3,
            probabilityOfTouchTp1 = (pWin * 1.15).coerceIn(0.30, 0.85),
            probabilityOfTouchTp2 = (pWin * 0.85).coerceIn(0.25, 0.65),
            probabilityOfTouchTp3 = (pWin * 0.55).coerceIn(0.15, 0.45),
            expectedValueR = expectedValueR,
            pWin = pWin,
            isUnproven = isUnproven,
            sampleSize = sampleCount,
            recommendedPositionSizePercent = sizePct,
            recommendedLeverage = maxSafeLev,
            triggerCondition = triggerCondition + wideZoneTag,
            invalidationDescription = "Structural invalidation beyond ${PriceFormatter.formatCurrency(structuralInvalidation)} + ${PriceFormatter.formatCurrency(buffer)} safety buffer",
            whyThisEntry = "${model.title} evaluated in ${regime.label} regime. Stop positioned strictly outside zone & liquidity clusters.$wideZoneTag",
            alignedFamiliesCount = 0,
            alignedFamilies = emptyList(),
            isAgainstHtfTrend = isHtfOpposed,
            gapRiskWarning = vetoResult.gapRiskWarning,
            softFlags = candidateSoftFlags,
            needsCondition = rrNeedsCondition,
            routedViaFuturesShort = routedFuturesShort,
            farZones = farZonesList.distinct()
        )
    }

    data class GeometryResolutionResult(
        val entryPrice: Double,
        val tp1: Double,
        val tp2: Double,
        val tp3: Double,
        val netRrTp1: Double,
        val netRrTp2: Double,
        val netRrTp3: Double,
        val softFlags: List<SoftFlag>,
        val needsCondition: String,
        val wasAdjusted: Boolean,
        val grade: SetupGrade? = null
    )

    fun resolveGeometryForTargetR(
        entryPrice: Double,
        stopLossPrice: Double,
        takeProfit1: Double,
        takeProfit2: Double,
        takeProfit3: Double,
        oppExtreme: Double,
        livePrice: Double,
        atr: Double,
        isLong: Boolean,
        targetR: Double = 1.50
    ): GeometryResolutionResult {
        var finalEntry = entryPrice
        var tp1 = takeProfit1
        var tp2 = takeProfit2
        var tp3 = takeProfit3
        var stopDist = abs(finalEntry - stopLossPrice)
        var netRrTp1 = if (stopDist > 0.0) abs(tp1 - finalEntry) / stopDist else 0.0
        var netRrTp2 = if (stopDist > 0.0) abs(tp2 - finalEntry) / stopDist else 0.0
        var netRrTp3 = if (stopDist > 0.0) abs(tp3 - finalEntry) / stopDist else 0.0

        if (netRrTp1 >= targetR) {
            return GeometryResolutionResult(
                entryPrice = finalEntry,
                tp1 = tp1,
                tp2 = tp2,
                tp3 = tp3,
                netRrTp1 = netRrTp1,
                netRrTp2 = netRrTp2,
                netRrTp3 = netRrTp3,
                softFlags = emptyList(),
                needsCondition = "",
                wasAdjusted = false
            )
        }

        val softFlags = mutableListOf<SoftFlag>()
        val solvedEntry = (tp1 + targetR * stopLossPrice) / (1.0 + targetR)
        val maxDrift = minOf(atr * 5.0, livePrice * 0.25)
        val solvedValid = solvedEntry > 0.0 &&
            ((isLong && solvedEntry > stopLossPrice && solvedEntry < tp1) ||
                (!isLong && solvedEntry < stopLossPrice && solvedEntry > tp1)) &&
            abs(solvedEntry - livePrice) <= maxDrift

        return if (solvedValid) {
            finalEntry = solvedEntry
            stopDist = abs(finalEntry - stopLossPrice)
            netRrTp1 = abs(tp1 - finalEntry) / stopDist
            netRrTp2 = abs(tp2 - finalEntry) / stopDist
            netRrTp3 = abs(tp3 - finalEntry) / stopDist
            val reqStr = PriceFormatter.formatCurrency(solvedEntry)
            val needs = "needs price at $reqStr for ${String.format(Locale.US, "%.1f", targetR)}R"
            softFlags.add(
                SoftFlag(
                    type = SoftFlagType.RR_BELOW_1_5,
                    label = "R:R requires pullback",
                    detail = "Live R:R < 1.5; solved limit $reqStr yields 1:${String.format(Locale.US, "%.1f", netRrTp1)}.",
                    actionHint = "Watch & set limit — $needs"
                )
            )
            GeometryResolutionResult(
                entryPrice = finalEntry,
                tp1 = tp1,
                tp2 = tp2,
                tp3 = tp3,
                netRrTp1 = netRrTp1,
                netRrTp2 = netRrTp2,
                netRrTp3 = netRrTp3,
                softFlags = softFlags,
                needsCondition = needs,
                wasAdjusted = true,
                grade = SetupGrade.C_WATCH
            )
        } else {
            val nearestOpposing = oppExtreme.takeIf { it > 0.0 && ((isLong && it > finalEntry) || (!isLong && it < finalEntry)) } ?: tp1
            tp1 = nearestOpposing
            tp2 = if (isLong) maxOf(tp1 + stopDist * 0.5, tp2) else minOf(tp1 - stopDist * 0.5, tp2).coerceAtLeast(finalEntry * 0.05)
            tp3 = if (isLong) maxOf(tp2 + stopDist * 0.75, tp3) else minOf(tp2 - stopDist * 0.75, tp3).coerceAtLeast(finalEntry * 0.02)
            netRrTp1 = if (stopDist > 0.0) abs(tp1 - finalEntry) / stopDist else 0.5
            netRrTp2 = if (stopDist > 0.0) abs(tp2 - finalEntry) / stopDist else 1.0
            netRrTp3 = if (stopDist > 0.0) abs(tp3 - finalEntry) / stopDist else 1.5
            val needs = "needs price at ${PriceFormatter.formatCurrency(finalEntry)} (opposing level R:R 1:${String.format(Locale.US, "%.1f", netRrTp1)})"
            softFlags.add(
                SoftFlag(
                    type = SoftFlagType.RR_BELOW_1_5,
                    label = "R:R 1:${String.format(Locale.US, "%.1f", netRrTp1)} (<1.5R)",
                    detail = "Nearest opposing level yields 1:${String.format(Locale.US, "%.1f", netRrTp1)}.",
                    actionHint = "Grade C WATCH — $needs"
                )
            )
            GeometryResolutionResult(
                entryPrice = finalEntry,
                tp1 = tp1,
                tp2 = tp2,
                tp3 = tp3,
                netRrTp1 = netRrTp1,
                netRrTp2 = netRrTp2,
                netRrTp3 = netRrTp3,
                softFlags = softFlags,
                needsCondition = needs,
                wasAdjusted = false,
                grade = SetupGrade.C_WATCH
            )
        }
    }
}
