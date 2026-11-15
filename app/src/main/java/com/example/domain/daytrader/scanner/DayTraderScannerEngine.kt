package com.example.domain.daytrader.scanner

import com.example.domain.analytics.QuantitativeEngine
import com.example.domain.daytrader.model.MarketProfileType
import com.example.domain.daytrader.model.TimeWindow
import com.example.domain.daytrader.replay.ReplayEngine
import com.example.domain.daytrader.replay.ReplayPlan
import com.example.domain.daytrader.replay.SessionPool
import com.example.domain.engine.SignalCalibrationEngine
import com.example.domain.engine.SignalEngineV2
import com.example.domain.engine.SignalVetoDetector
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.OffHoursMode
import com.example.domain.model.PositionSide
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import com.example.domain.util.PriceFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Universal Day-Trader Scanner for Stocks and Crypto (Actionability Patch).
 * Uses SignalEngineV2 as the single source of truth for graded "Setups",
 * alongside "Raw rule matches" backed by quantitative closed-bar pattern metrics.
 */
object DayTraderScannerEngine {

    data class SymbolChartBundle(
        val symbol: String,
        val name: String,
        val assetType: AssetType,
        val dailyCandles: List<CandlePoint>,
        val fiveMinuteCandles: List<CandlePoint>,
        val volume24hUsd: Double,
        val currentPrice: Double,
        val sessionBadge: String = "OPEN",
        val setupCandles15m: List<CandlePoint> = emptyList(),
        val htfCandles1h: List<CandlePoint> = emptyList(),
        val change24hPercent: Double? = null
    )

    data class UnifiedScanResult(
        val setups: List<ScanSetupItem>,
        val marketSummary: ScanMarketSummary,
        val rawRuleHits: List<ScanHitItem>,
        val diagnostics: ScanDiagnosticReport
    )

    /**
     * Unified Scanner: evaluates both graded SignalEngineV2 setups (single source of truth
     * with Terminal) and quantitative closed-bar rule hits + market breadth summary.
     */
    fun scanSetupsAndMarketSummary(
        bundles: List<SymbolChartBundle>,
        marketType: TradingMarketType = TradingMarketType.SPOT,
        universeLabel: String = "Watchlist + Top Liquid",
        sessionPoolProvider: ((String, MarketProfileType) -> SessionPool?)? = null,
        topReplayCount: Int = 10,
        offHoursMode: OffHoursMode = OffHoursMode.CAUTION,
        futuresPerpChecker: (String, AssetType) -> Boolean = { _, asset -> asset == AssetType.CRYPTO },
        customStockEntryWindows: List<TimeWindow>? = null
    ): UnifiedScanResult {
        val startTime = System.currentTimeMillis()
        val distinctBundles = bundles.distinctBy { "${it.symbol.uppercase(Locale.US)}_${it.assetType.name}" }
        val (rawHits, baseDiagnostics) = scanUniverse(
            bundles = distinctBundles,
            sessionPoolProvider = sessionPoolProvider,
            topReplayCount = topReplayCount,
            universeLabel = universeLabel
        )

        val effectiveMarketType = if (universeLabel.contains("Perp", ignoreCase = true)) {
            TradingMarketType.FUTURES
        } else {
            marketType
        }

        val hitReplayBySymbol = rawHits.associateBy({ it.symbol.uppercase(Locale.US) }, { it.replayChipLabel })

        val setups = mutableListOf<ScanSetupItem>()
        val blockerCounts = mutableMapOf<String, Int>()
        var belowEma20Count = 0
        var belowEma50Count = 0
        var breadthSampleCount = 0
        var btcRegime = "N/A"

        for (bundle in distinctBundles) {
            val validDaily = bundle.dailyCandles.filter { it.isClosed && it.open > 0 && it.high >= it.low && it.close > 0 }
            val valid15m = bundle.setupCandles15m.filter { it.isClosed && it.open > 0 && it.high >= it.low && it.close > 0 }
            val valid5m = bundle.fiveMinuteCandles.filter { it.isClosed && it.open > 0 && it.high >= it.low && it.close > 0 }
            val valid1h = bundle.htfCandles1h.filter { it.isClosed && it.open > 0 && it.high >= it.low && it.close > 0 }

            val breadthCandles = when {
                validDaily.size >= 20 -> validDaily
                valid1h.size >= 20 -> valid1h
                valid15m.size >= 20 -> valid15m
                else -> valid5m
            }
            if (breadthCandles.size >= 20) {
                breadthSampleCount++
                val closes = breadthCandles.map { it.close }
                val ema20 = QuantitativeEngine.calculateEMA(closes, 20)
                val ema50 = if (closes.size >= 50) QuantitativeEngine.calculateEMA(closes, 50) else ema20
                if (bundle.currentPrice < ema20) belowEma20Count++
                if (bundle.currentPrice < ema50) belowEma50Count++
            }

            if (bundle.symbol.equals("BTC", ignoreCase = true)) {
                val btcSeries = when {
                    valid1h.size >= 50 -> valid1h
                    valid15m.size >= 50 -> valid15m
                    validDaily.size >= 20 -> validDaily
                    else -> valid5m
                }
                if (btcSeries.size >= 20) {
                    val ind = QuantitativeEngine.computeIndicators(btcSeries)
                    btcRegime = SignalVetoDetector.classifyMarketRegime(ind).label
                }
            }

            val (setupCandles, setupTf, htfCandles) = when {
                valid15m.size >= 100 -> Triple(valid15m, Timeframe.FIFTEEN_MIN, valid1h)
                valid5m.size >= 100 -> Triple(valid5m, Timeframe.FIVE_MIN, valid15m.ifEmpty { valid1h })
                valid1h.size >= 100 -> Triple(valid1h, Timeframe.ONE_HOUR, validDaily)
                validDaily.size >= 100 -> Triple(validDaily, Timeframe.ONE_DAY, emptyList())
                else -> Triple(valid15m.ifEmpty { valid5m.ifEmpty { validDaily } }, Timeframe.FIFTEEN_MIN, valid1h)
            }

            val output = SignalEngineV2.generateSignal(
                SignalEngineV2.SignalEngineInputs(
                    symbol = bundle.symbol,
                    currentPrice = bundle.currentPrice,
                    timeframe = setupTf,
                    marketType = effectiveMarketType,
                    assetType = bundle.assetType,
                    candles = setupCandles,
                    higherTimeframeCandles = htfCandles,
                    offHoursMode = offHoursMode,
                    futuresPerpAvailable = futuresPerpChecker(bundle.symbol, bundle.assetType),
                    customStockEntryWindows = customStockEntryWindows
                )
            )

            val setup = output.primarySetup
            val triggerPrice = setup?.entryPrice ?: 0.0
            val distPct = if (triggerPrice > 0.0 && bundle.currentPrice > 0.0) {
                (abs(triggerPrice - bundle.currentPrice) / bundle.currentPrice) * 100.0
            } else {
                null
            }

            val change24h = bundle.change24hPercent ?: if (validDaily.size >= 2 && validDaily[validDaily.size - 2].close > 0) {
                ((validDaily.last().close - validDaily[validDaily.size - 2].close) / validDaily[validDaily.size - 2].close) * 100.0
            } else {
                0.0
            }

            val rvol = calculateRvol(validDaily.ifEmpty { setupCandles })

            output.vetoReasons.forEach { reason ->
                val shortReason = reason.substringBefore(":").trim().ifBlank { reason }
                blockerCounts[shortReason] = (blockerCounts[shortReason] ?: 0) + 1
            }
            output.softFlags.forEach { flag ->
                blockerCounts[flag.label] = (blockerCounts[flag.label] ?: 0) + 1
            }

            setups.add(
                ScanSetupItem(
                    symbol = bundle.symbol,
                    name = bundle.name,
                    assetType = bundle.assetType,
                    timeframe = setupTf,
                    verdict = output.verdictState,
                    grade = output.setupGrade,
                    modelTitle = setup?.model?.title ?: if (output.bullScenario.isActive) "Bullish Watch" else "Conditional Watch",
                    side = setup?.direction ?: if (output.bullScenario.isActive) PositionSide.LONG else PositionSide.SHORT,
                    currentPrice = bundle.currentPrice,
                    change24hPercent = change24h,
                    volume24hUsd = bundle.volume24hUsd,
                    rvol = rvol,
                    marketRegime = output.marketRegime.label,
                    familiesAligned = output.familiesAlignedCount,
                    softFlags = output.softFlags,
                    hardVetoes = output.vetoReasons,
                    distanceToTriggerPercent = distPct,
                    netRrTp1 = setup?.netRiskRewardTp1 ?: 0.0,
                    triggerPrice = triggerPrice,
                    stopLevel = setup?.stopLossPrice ?: 0.0,
                    targetLevel = setup?.takeProfit1 ?: 0.0,
                    needsCondition = output.needsCondition.ifBlank { output.oneLineWhy },
                    replayChipLabel = hitReplayBySymbol[bundle.symbol.uppercase(Locale.US)],
                    sessionBadge = bundle.sessionBadge,
                    barsLoaded = output.barsLoaded
                )
            )
        }

        val sortedSetups = setups.sortedWith(
            compareBy<ScanSetupItem> {
                when (it.verdict) {
                    SignalVerdictState.TRADE -> 0
                    SignalVerdictState.WAIT -> 1
                    SignalVerdictState.NO_TRADE -> 2
                }
            }.thenBy {
                when (it.grade) {
                    SetupGrade.A -> 0
                    SetupGrade.B -> 1
                    SetupGrade.C_WATCH -> 2
                    null -> 3
                }
            }.thenBy { it.softFlags.size }
                .thenBy { it.distanceToTriggerPercent ?: Double.MAX_VALUE }
                .thenByDescending { it.familiesAligned }
                .thenByDescending { it.rvol }
        )

        val gradeACount = sortedSetups.count { it.grade == SetupGrade.A }
        val gradeBCount = sortedSetups.count { it.grade == SetupGrade.B }
        val watchCount = sortedSetups.count { it.grade == SetupGrade.C_WATCH || (it.verdict == SignalVerdictState.WAIT && it.grade == null) }
        val noTradeCount = sortedSetups.count { it.verdict == SignalVerdictState.NO_TRADE }

        val rankedBlockers = blockerCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { BlockerReasonCount(it.key, it.value) }

        val closestWatchSetups = if (gradeACount == 0 && gradeBCount == 0) {
            sortedSetups.filter { it.verdict != SignalVerdictState.NO_TRADE && it.triggerPrice > 0.0 }.take(3)
        } else {
            emptyList()
        }

        val pctBelow20 = if (breadthSampleCount > 0) (belowEma20Count.toDouble() / breadthSampleCount) * 100.0 else 0.0
        val pctBelow50 = if (breadthSampleCount > 0) (belowEma50Count.toDouble() / breadthSampleCount) * 100.0 else 0.0

        val marketSummary = ScanMarketSummary(
            universeLabel = universeLabel,
            totalEvaluated = sortedSetups.size,
            pctBelowEma20 = pctBelow20,
            pctBelowEma50 = pctBelow50,
            btcRegime = btcRegime,
            gradeACount = gradeACount,
            gradeBCount = gradeBCount,
            watchCount = watchCount,
            noTradeCount = noTradeCount,
            rankedBlockers = rankedBlockers,
            closestWatchSetups = closestWatchSetups
        )

        val finalDiagnostics = baseDiagnostics.copy(
            executionTimeMs = System.currentTimeMillis() - startTime,
            universeLabel = universeLabel
        )

        return UnifiedScanResult(
            setups = sortedSetups,
            marketSummary = marketSummary,
            rawRuleHits = rawHits,
            diagnostics = finalDiagnostics
        )
    }

    /**
     * Scans a list of symbol chart bundles against the 12 day-trading rules.
     */
    fun scanUniverse(
        bundles: List<SymbolChartBundle>,
        sessionPoolProvider: ((String, MarketProfileType) -> SessionPool?)? = null,
        topReplayCount: Int = 10,
        universeLabel: String = "Watchlist + Top Symbols"
    ): Pair<List<ScanHitItem>, ScanDiagnosticReport> {
        val startTime = System.currentTimeMillis()
        var chartsReturned = 0
        var usableCharts = 0
        val rawHits = mutableListOf<ScanHitItem>()

        for (bundle in bundles.distinctBy { "${it.symbol.uppercase(Locale.US)}_${it.assetType.name}" }) {
            chartsReturned++
            // Filter completed bars only
            val validDaily = bundle.dailyCandles.filter { it.isClosed && it.open > 0 && it.high >= it.low && it.close > 0 }
            val valid5m = bundle.fiveMinuteCandles.filter { it.isClosed && it.open > 0 && it.high >= it.low && it.close > 0 }

            if (validDaily.size < 21 && valid5m.size < 4) {
                continue // Not enough bars to evaluate any daily or OR rule
            }
            usableCharts++

            val matchedRules = evaluateRulesForSymbol(bundle.symbol, validDaily, valid5m)
            for (hit in matchedRules) {
                val rvol = calculateRvol(validDaily)
                val regime = if (validDaily.size >= 14) {
                    val atr = QuantitativeEngine.calculateATR(validDaily)
                    if (atr / bundle.currentPrice > 0.03) "HIGH VOL EXPANSION" else "NORMAL TREND"
                } else "NORMAL"

                val familiesAligned = when (hit.matchedRule) {
                    ScanRuleType.BREAKOUT_20D_HIGH, ScanRuleType.BREAKDOWN_20D_LOW -> 5
                    ScanRuleType.BULL_FLAG, ScanRuleType.BEAR_FLAG -> 6
                    ScanRuleType.OR_BREAKOUT_BULL, ScanRuleType.OR_BREAKDOWN_BEAR -> 5
                    ScanRuleType.RECLAIM_SMA20, ScanRuleType.LOST_SMA20 -> 4
                    else -> 3
                }

                val change24h = bundle.change24hPercent ?: if (validDaily.size >= 2 && validDaily[validDaily.size - 2].close > 0) {
                    ((validDaily.last().close - validDaily[validDaily.size - 2].close) / validDaily[validDaily.size - 2].close) * 100.0
                } else 0.0

                rawHits.add(
                    ScanHitItem(
                        symbol = bundle.symbol,
                        name = bundle.name,
                        assetType = bundle.assetType,
                        matchedRule = hit.matchedRule,
                        ruleDescription = "${hit.matchedRule.label}: ${hit.quantitativeSummary}",
                        side = hit.matchedRule.side,
                        currentPrice = bundle.currentPrice,
                        change24hPercent = change24h,
                        volume24hUsd = bundle.volume24hUsd,
                        rvol = rvol,
                        marketRegime = regime,
                        familiesAligned = familiesAligned,
                        sessionBadge = bundle.sessionBadge,
                        triggerLevel = hit.triggerLevel,
                        stopLevel = hit.stopLevel,
                        targetLevel = hit.targetLevel,
                        quantitativeSummary = hit.quantitativeSummary
                    )
                )
            }
        }

        // Rank hits by familiesAligned DESC, then RVOL DESC
        val rankedHits = rawHits.sortedWith(
            compareByDescending<ScanHitItem> { it.familiesAligned }.thenByDescending { it.rvol }
        )

        // Run Replay Agent only on the top N ranked hits
        val finalHits = rankedHits.mapIndexed { idx, hit ->
            if (idx < topReplayCount && sessionPoolProvider != null) {
                val profileType = if (hit.assetType == AssetType.STOCK) MarketProfileType.US_EQUITY else MarketProfileType.CRYPTO
                val pool = sessionPoolProvider(hit.symbol, profileType)
                if (pool != null && pool.isSufficient) {
                    val plan = ReplayPlan(
                        symbol = hit.symbol,
                        side = hit.side,
                        entryPrice = hit.currentPrice,
                        stopLossPrice = hit.stopLevel,
                        takeProfitPrice = hit.targetLevel,
                        entryBarTimestamp = System.currentTimeMillis() - 300000L,
                        timeOfDayIndex = 15,
                        horizonBars = 30,
                        profileType = profileType
                    )
                    val replayResult = ReplayEngine.runReplay(plan, pool, seed = 19L, numReplays = 2000)
                    if (replayResult.refusalReason == null && replayResult.verdict.lean != com.example.domain.daytrader.replay.ReplayLean.INSUFFICIENT_RESOLVED) {
                        SignalCalibrationEngine.recordReplayPWin(
                            symbol = hit.symbol,
                            pWin = replayResult.effectivePWin,
                            conservativeMode = replayResult.conservativeModeLabel,
                            asIsPWin = replayResult.asIs?.observedTargetShare ?: replayResult.effectivePWin,
                            driftNeutralPWin = replayResult.driftNeutral?.observedTargetShare ?: replayResult.effectivePWin,
                            oddsDiffPoints = replayResult.oddsDiffPoints
                        )
                    }
                    val chipText = if (replayResult.verdict.lean == com.example.domain.daytrader.replay.ReplayLean.INSUFFICIENT_RESOLVED) {
                        replayResult.verdict.headline
                    } else {
                        "${replayResult.verdict.headline} (${replayResult.conservativeModeLabel}: TP ${String.format(Locale.US, "%.0f", replayResult.targetFirstPct)}% vs SL ${String.format(Locale.US, "%.0f", replayResult.stopFirstPct)}%)"
                    }
                    hit.copy(
                        replayVerdict = replayResult.verdict,
                        replayChipLabel = chipText
                    )
                } else hit
            } else hit
        }

        val report = ScanDiagnosticReport(
            totalSymbolsScanned = bundles.size,
            chartsReturnedCount = chartsReturned,
            usableChartsCount = usableCharts,
            executionTimeMs = System.currentTimeMillis() - startTime,
            universeLabel = universeLabel
        )

        return Pair(finalHits, report)
    }

    data class InternalRuleHit(
        val matchedRule: ScanRuleType,
        val triggerLevel: Double,
        val stopLevel: Double,
        val targetLevel: Double,
        val quantitativeSummary: String = ""
    )

    /**
     * Evaluates daily patterns and 15m opening range breakouts on completed bars with exact quantitative measurements.
     */
    fun evaluateRulesForSymbol(
        symbol: String,
        daily: List<CandlePoint>,
        fiveMin: List<CandlePoint>
    ): List<InternalRuleHit> {
        val hits = mutableListOf<InternalRuleHit>()
        val nDaily = daily.size
        val atr = if (nDaily >= 14) QuantitativeEngine.calculateATR(daily) else (daily.lastOrNull()?.close ?: 100.0) * 0.02

        // --- 1. 20-Day Closing High / Low Breakout / Breakdown ---
        if (nDaily >= 21) {
            val currentClose = daily.last().close
            val prior20Closes = daily.subList(nDaily - 21, nDaily - 1).map { it.close }
            val highestPriorClose = prior20Closes.maxOrNull() ?: currentClose
            val lowestPriorClose = prior20Closes.minOrNull() ?: currentClose

            if (currentClose > highestPriorClose && highestPriorClose > 0.0) {
                val breakPct = ((currentClose - highestPriorClose) / highestPriorClose) * 100.0
                hits.add(
                    InternalRuleHit(
                        matchedRule = ScanRuleType.BREAKOUT_20D_HIGH,
                        triggerLevel = highestPriorClose,
                        stopLevel = currentClose - (1.5 * atr),
                        targetLevel = currentClose + (3.0 * atr),
                        quantitativeSummary = "Close ${PriceFormatter.formatCurrency(currentClose)} > 20D high ${PriceFormatter.formatCurrency(highestPriorClose)} (+${String.format(Locale.US, "%.1f", breakPct)}%)"
                    )
                )
            } else if (currentClose < lowestPriorClose && lowestPriorClose > 0.0) {
                val breakPct = ((lowestPriorClose - currentClose) / lowestPriorClose) * 100.0
                hits.add(
                    InternalRuleHit(
                        matchedRule = ScanRuleType.BREAKDOWN_20D_LOW,
                        triggerLevel = lowestPriorClose,
                        stopLevel = currentClose + (1.5 * atr),
                        targetLevel = currentClose - (3.0 * atr),
                        quantitativeSummary = "Close ${PriceFormatter.formatCurrency(currentClose)} < 20D low ${PriceFormatter.formatCurrency(lowestPriorClose)} (-${String.format(Locale.US, "%.1f", breakPct)}%)"
                    )
                )
            }
        }

        // --- 2. Reclaim / Loss of 20-Day Average (SMA20) ---
        if (nDaily >= 24) {
            val sma20Values = (0 until 4).map { offset ->
                val end = nDaily - offset
                daily.subList(end - 20, end).map { it.close }.average()
            }
            val currClose = daily.last().close
            val currSma = sma20Values[0]

            val wasBelow3 = (1..3).all { daily[nDaily - 1 - it].close < sma20Values[it] }
            if (currClose > currSma && wasBelow3 && currSma > 0.0) {
                val distPct = ((currClose - currSma) / currSma) * 100.0
                hits.add(
                    InternalRuleHit(
                        matchedRule = ScanRuleType.RECLAIM_SMA20,
                        triggerLevel = currSma,
                        stopLevel = currClose - (1.5 * atr),
                        targetLevel = currClose + (2.5 * atr),
                        quantitativeSummary = "Reclaimed 20D SMA ${PriceFormatter.formatCurrency(currSma)} (+${String.format(Locale.US, "%.1f", distPct)}% after >=3 closes below)"
                    )
                )
            }

            val wasAbove3 = (1..3).all { daily[nDaily - 1 - it].close > sma20Values[it] }
            if (currClose < currSma && wasAbove3 && currSma > 0.0) {
                val distPct = ((currSma - currClose) / currSma) * 100.0
                hits.add(
                    InternalRuleHit(
                        matchedRule = ScanRuleType.LOST_SMA20,
                        triggerLevel = currSma,
                        stopLevel = currClose + (1.5 * atr),
                        targetLevel = currClose - (2.5 * atr),
                        quantitativeSummary = "Lost 20D SMA ${PriceFormatter.formatCurrency(currSma)} (-${String.format(Locale.US, "%.1f", distPct)}% after >=3 closes above)"
                    )
                )
            }
        }

        // --- 3. RSI14 Oversold / Overbought & Bounces ---
        if (nDaily >= 16) {
            val rsiToday = calculateWilderRsi(daily)
            val rsiYesterday = calculateWilderRsi(daily.dropLast(1))
            val isUpToday = daily.last().close > daily.last().open
            val isDownToday = daily.last().close < daily.last().open
            val currP = daily.last().close

            if (rsiToday < 30.0) {
                hits.add(
                    InternalRuleHit(
                        matchedRule = ScanRuleType.OVERSOLD_RSI,
                        triggerLevel = currP,
                        stopLevel = currP - (1.5 * atr),
                        targetLevel = currP + (2.5 * atr),
                        quantitativeSummary = "RSI14 = ${String.format(Locale.US, "%.1f", rsiToday)} (< 30 oversold)"
                    )
                )
            } else if (rsiToday > 70.0) {
                hits.add(
                    InternalRuleHit(
                        matchedRule = ScanRuleType.OVERBOUGHT_RSI,
                        triggerLevel = currP,
                        stopLevel = currP + (1.5 * atr),
                        targetLevel = currP - (2.5 * atr),
                        quantitativeSummary = "RSI14 = ${String.format(Locale.US, "%.1f", rsiToday)} (> 70 overbought)"
                    )
                )
            }

            if (rsiYesterday < 30.0 && isUpToday) {
                hits.add(
                    InternalRuleHit(
                        matchedRule = ScanRuleType.OVERSOLD_BOUNCE,
                        triggerLevel = daily.last().high,
                        stopLevel = daily.last().low,
                        targetLevel = currP + (2.0 * (currP - daily.last().low)),
                        quantitativeSummary = "RSI14 bounced from ${String.format(Locale.US, "%.1f", rsiYesterday)} to ${String.format(Locale.US, "%.1f", rsiToday)} on up close"
                    )
                )
            } else if (rsiYesterday > 70.0 && isDownToday) {
                hits.add(
                    InternalRuleHit(
                        matchedRule = ScanRuleType.OVERBOUGHT_REJECTION,
                        triggerLevel = daily.last().low,
                        stopLevel = daily.last().high,
                        targetLevel = currP - (2.0 * (daily.last().high - currP)),
                        quantitativeSummary = "RSI14 rejected from ${String.format(Locale.US, "%.1f", rsiYesterday)} to ${String.format(Locale.US, "%.1f", rsiToday)} on red close"
                    )
                )
            }
        }

        // --- 4. Bull & Bear Flags ---
        if (nDaily >= 15) {
            val currP = daily.last().close
            val window10 = daily.takeLast(10)
            val start10 = window10.first().open
            val peak10 = window10.maxOf { it.high }
            val trough10 = window10.minOf { it.low }

            val bullImpulse = peak10 - start10
            if (bullImpulse >= 2.0 * atr && start10 > 0.0) {
                val pullback = peak10 - currP
                if (pullback <= 0.38 * bullImpulse && pullback > 0.0) {
                    val polePct = (bullImpulse / start10) * 100.0
                    val retracePct = (pullback / bullImpulse) * 100.0
                    hits.add(
                        InternalRuleHit(
                            matchedRule = ScanRuleType.BULL_FLAG,
                            triggerLevel = peak10,
                            stopLevel = currP - (1.2 * atr),
                            targetLevel = peak10 + bullImpulse * 0.618,
                            quantitativeSummary = "Pole +${String.format(Locale.US, "%.1f", polePct)}%, pullback ${String.format(Locale.US, "%.0f", retracePct)}% over 10 bars, break > ${PriceFormatter.formatCurrency(peak10)}"
                        )
                    )
                }
            }

            val bearImpulse = start10 - trough10
            if (bearImpulse >= 2.0 * atr && start10 > 0.0) {
                val pullback = currP - trough10
                if (pullback <= 0.38 * bearImpulse && pullback > 0.0) {
                    val polePct = (bearImpulse / start10) * 100.0
                    val retracePct = (pullback / bearImpulse) * 100.0
                    hits.add(
                        InternalRuleHit(
                            matchedRule = ScanRuleType.BEAR_FLAG,
                            triggerLevel = trough10,
                            stopLevel = currP + (1.2 * atr),
                            targetLevel = trough10 - bearImpulse * 0.618,
                            quantitativeSummary = "Pole -${String.format(Locale.US, "%.1f", polePct)}%, bounce ${String.format(Locale.US, "%.0f", retracePct)}% over 10 bars, break < ${PriceFormatter.formatCurrency(trough10)}"
                        )
                    )
                }
            }
        }

        // --- 5. Opening Range Breakout / Breakdown (15m = first 3 completed 5m bars) ---
        if (fiveMin.size >= 4) {
            val orBars = fiveMin.take(3)
            val orHighestClose = orBars.maxOf { it.close }
            val orLowestClose = orBars.minOf { it.close }
            val orMidpoint = (orBars.maxOf { it.high } + orBars.minOf { it.low }) / 2.0

            val postOrBars = fiveMin.drop(3)
            val latestCompleted5m = postOrBars.last()

            if (latestCompleted5m.close > orHighestClose && orHighestClose > 0.0) {
                val breakPct = ((latestCompleted5m.close - orHighestClose) / orHighestClose) * 100.0
                hits.add(
                    InternalRuleHit(
                        matchedRule = ScanRuleType.OR_BREAKOUT_BULL,
                        triggerLevel = orHighestClose,
                        stopLevel = orMidpoint,
                        targetLevel = latestCompleted5m.close + (2.0 * abs(latestCompleted5m.close - orMidpoint)),
                        quantitativeSummary = "5m close ${PriceFormatter.formatCurrency(latestCompleted5m.close)} > 15m OR high ${PriceFormatter.formatCurrency(orHighestClose)} (+${String.format(Locale.US, "%.2f", breakPct)}%)"
                    )
                )
            } else if (latestCompleted5m.close < orLowestClose && orLowestClose > 0.0) {
                val breakPct = ((orLowestClose - latestCompleted5m.close) / orLowestClose) * 100.0
                hits.add(
                    InternalRuleHit(
                        matchedRule = ScanRuleType.OR_BREAKDOWN_BEAR,
                        triggerLevel = orLowestClose,
                        stopLevel = orMidpoint,
                        targetLevel = latestCompleted5m.close - (2.0 * abs(orMidpoint - latestCompleted5m.close)),
                        quantitativeSummary = "5m close ${PriceFormatter.formatCurrency(latestCompleted5m.close)} < 15m OR low ${PriceFormatter.formatCurrency(orLowestClose)} (-${String.format(Locale.US, "%.2f", breakPct)}%)"
                    )
                )
            }
        }

        return hits
    }

    private fun calculateWilderRsi(candles: List<CandlePoint>, period: Int = 14): Double {
        if (candles.size <= period) return 50.0
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val diff = candles[i].close - candles[i - 1].close
            if (diff >= 0) gainSum += diff else lossSum += -diff
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period

        for (i in (period + 1) until candles.size) {
            val diff = candles[i].close - candles[i - 1].close
            val gain = if (diff >= 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }

        if (avgLoss <= 0.000001) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private fun calculateRvol(candles: List<CandlePoint>): Double {
        if (candles.size < 10) return 1.0
        val recentVolume = candles.last().volume
        val avgVolume = candles.takeLast(10).dropLast(1).map { it.volume }.average()
        return if (avgVolume > 0) recentVolume / avgVolume else 1.0
    }
}
