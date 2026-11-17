package com.example.domain.daytrader.replay

import com.example.domain.daytrader.model.MarketProfileType
import com.example.domain.model.PositionSide
import java.util.Locale
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure Kotlin implementation of the Replay Agent ("Time Machine") adapted from
 * the MIT-licensed day-trader-skill repo (Jake Schincariol).
 *
 * Simulates 10,000 seeded bootstrap replays using the symbol's own historical 5m bars,
 * evaluating stop-first vs target-first outcomes net of costs.
 */
object ReplayEngine {

    const val DEFAULT_SEED = 19L
    const val DEFAULT_NUM_REPLAYS = 10_000

    /**
     * Executes the Replay Agent simulation.
     */
    fun runReplay(
        plan: ReplayPlan,
        pool: SessionPool,
        seed: Long = DEFAULT_SEED,
        numReplays: Int = DEFAULT_NUM_REPLAYS,
        useBlockBootstrap: Boolean = true,
        useVolatilityScaling: Boolean = true
    ): ReplayResult {
        // Refusal checks
        val validationErr = plan.validate()
        if (validationErr != null) {
            return buildRefusalResult(plan, seed, validationErr)
        }
        if (!pool.isSufficient) {
            return buildRefusalResult(
                plan,
                seed,
                "Replay refused: fewer than 20 full sessions available (${pool.totalSessions}/20 found in 60-day history)."
            )
        }

        val asIsMetrics = runSimulationPass(
            plan = plan,
            pool = pool,
            modeLabel = "As-Is",
            seed = seed,
            numReplays = numReplays,
            useBlockBootstrap = useBlockBootstrap,
            useVolatilityScaling = useVolatilityScaling
        )

        val driftNeutralPool = pool.toDriftNeutralPool()
        val driftNeutralMetrics = runSimulationPass(
            plan = plan,
            pool = driftNeutralPool,
            modeLabel = "Drift-Neutral",
            seed = seed,
            numReplays = numReplays,
            useBlockBootstrap = useBlockBootstrap,
            useVolatilityScaling = useVolatilityScaling
        )

        // Choose the LESS favorable of the two modes for the verdict and Calibration P(win)
        val useDriftNeutralAsConservative =
            driftNeutralMetrics.observedTargetShare < asIsMetrics.observedTargetShare - 1e-9 ||
                (abs(driftNeutralMetrics.observedTargetShare - asIsMetrics.observedTargetShare) <= 1e-9 &&
                    driftNeutralMetrics.netAverageR <= asIsMetrics.netAverageR)

        val lessFavorable = if (useDriftNeutralAsConservative) driftNeutralMetrics else asIsMetrics
        val conservativeLabel = lessFavorable.modeLabel

        val shareDiffPts = abs(asIsMetrics.observedTargetShare - driftNeutralMetrics.observedTargetShare) * 100.0
        val rawTargetDiffPts = abs(asIsMetrics.targetFirstPct - driftNeutralMetrics.targetFirstPct)
        val oddsDiffPoints = max(shareDiffPts, rawTargetDiffPts)

        val driftDivergenceNote = if (shareDiffPts > 5.0 || rawTargetDiffPts > 5.0) {
            "As-Is (${String.format(Locale.US, "%.1f", asIsMetrics.observedTargetShare * 100)}% P(win)) vs Drift-Neutral (${String.format(Locale.US, "%.1f", driftNeutralMetrics.observedTargetShare * 100)}% P(win)) differ by ${String.format(Locale.US, "%.1f", oddsDiffPoints)} pts (>5 pts); using $conservativeLabel."
        } else {
            null
        }

        val breakEvenTargetShare = 1.0 / (1.0 + plan.netR) // e.g. 1 / (1 + 2) = 33.3%

        val verdict = computeVerdict(
            observedTargetShare = lessFavorable.observedTargetShare,
            breakEvenTargetShare = breakEvenTargetShare,
            netR = plan.netR,
            averageR = lessFavorable.netAverageR,
            neitherPct = lessFavorable.neitherPct,
            history = lessFavorable.history ?: HistoricalBacktestSummary(0, 0, 0, 0, 0.0),
            seed = seed,
            conservativeModeLabel = conservativeLabel,
            driftDivergenceNote = driftDivergenceNote,
            resolvedCount = lessFavorable.resolvedCount,
            sessionsCount = lessFavorable.sessionsCount,
            numReplays = numReplays
        )

        val verdictWithPool = if (!pool.poolNotice.isNullOrBlank()) {
            verdict.copy(details = listOf(pool.poolNotice) + verdict.details)
        } else verdict

        return ReplayResult(
            symbol = plan.symbol,
            seed = seed,
            numReplays = numReplays,
            targetFirstPct = lessFavorable.targetFirstPct,
            stopFirstPct = lessFavorable.stopFirstPct,
            neitherPct = lessFavorable.neitherPct,
            greenPct = lessFavorable.greenPct,
            medianPrice = lessFavorable.medianPrice,
            p5Price = lessFavorable.p5Price,
            p95Price = lessFavorable.p95Price,
            averageR = lessFavorable.averageR,
            netAverageR = lessFavorable.netAverageR,
            fanChartBands = lessFavorable.fanChartBands,
            histogramBins = lessFavorable.histogramBins,
            verdict = verdictWithPool,
            history = asIsMetrics.history,
            plainTargetFirstPct = lessFavorable.targetFirstPct,
            plainStopFirstPct = lessFavorable.stopFirstPct,
            asIs = asIsMetrics,
            driftNeutral = driftNeutralMetrics,
            effectivePWin = lessFavorable.observedTargetShare,
            conservativeModeLabel = conservativeLabel,
            oddsDiffPoints = oddsDiffPoints,
            driftDivergenceNote = driftDivergenceNote,
            resolvedCount = lessFavorable.resolvedCount,
            sessionsCount = lessFavorable.sessionsCount,
            isWeekendFallbackCombined = pool.isWeekendFallbackCombined,
            poolNotice = pool.poolNotice
        )
    }

    private fun runSimulationPass(
        plan: ReplayPlan,
        pool: SessionPool,
        modeLabel: String,
        seed: Long,
        numReplays: Int,
        useBlockBootstrap: Boolean,
        useVolatilityScaling: Boolean
    ): ReplayPassMetrics {
        val random = Random(seed)
        val maxBars = if (plan.profileType == MarketProfileType.CRYPTO) 288 else 78
        val horizon = plan.horizonBars.coerceIn(1, maxBars)

        var targetCount = 0
        var stopCount = 0
        var neitherCount = 0
        var greenCount = 0

        val rValues = mutableListOf<Double>()
        val finalPrices = mutableListOf<Double>()

        // Price matrices for fan chart: stepIndex -> List of prices
        val stepPrices = Array(horizon) { mutableListOf<Double>() }

        // Costs in R units
        val costInDollars = plan.entryPrice * ((plan.takerFeeBps * 2.0 + plan.slippageBps + plan.fundingBpsPer8h) / 10000.0)
        val costInR = if (plan.riskPerUnit > 0.0) costInDollars / plan.riskPerUnit else 0.02

        // Main Simulation Loop
        for (rep in 0 until numReplays) {
            var simPrice = plan.entryPrice
            var hitOutcome: Double? = null // in net R
            var step = 0

            // If block bootstrap is enabled, we draw in runs of 3 bars
            val blockQueue: MutableList<BarRatio> = mutableListOf()

            while (step < horizon) {
                val currentTimeIndex = (plan.timeOfDayIndex + step) % maxBars

                val ratio: BarRatio? = if (useBlockBootstrap) {
                    if (blockQueue.isEmpty()) {
                        val newBlock = pool.sampleBlock(
                            timeOfDayIndex = currentTimeIndex,
                            blockSize = 3,
                            windowRadius = 2,
                            random = random,
                            applyVolatilityScaling = useVolatilityScaling
                        )
                        blockQueue.addAll(newBlock)
                    }
                    if (blockQueue.isNotEmpty()) blockQueue.removeAt(0) else null
                } else {
                    pool.sampleBar(
                        timeOfDayIndex = currentTimeIndex,
                        windowRadius = 2,
                        random = random,
                        applyVolatilityScaling = useVolatilityScaling
                    )
                }

                if (ratio == null) {
                    step++
                    continue
                }

                val prevClose = simPrice
                val barOpen = prevClose * ratio.openRatio
                val barHigh = max(barOpen, prevClose * ratio.highRatio)
                val barLow = min(barOpen, prevClose * ratio.lowRatio)
                val barClose = prevClose * ratio.closeRatio

                // Test bar by bar: Stop first, then targets
                if (plan.side == PositionSide.LONG) {
                    val touchedStop = barLow <= plan.stopLossPrice
                    val touchedTarget = barHigh >= plan.takeProfitPrice

                    if (touchedStop) {
                        // Even if target also touched, stop-first rule applies. Gap through stop = -1R
                        stopCount++
                        hitOutcome = -1.0 - costInR
                        simPrice = plan.stopLossPrice
                        stepPrices[step].add(simPrice)
                        break
                    } else if (touchedTarget) {
                        targetCount++
                        hitOutcome = plan.nominalR - costInR
                        simPrice = plan.takeProfitPrice
                        stepPrices[step].add(simPrice)
                        break
                    }
                } else {
                    // SHORT setup
                    val touchedStop = barHigh >= plan.stopLossPrice
                    val touchedTarget = barLow <= plan.takeProfitPrice

                    if (touchedStop) {
                        stopCount++
                        hitOutcome = -1.0 - costInR
                        simPrice = plan.stopLossPrice
                        stepPrices[step].add(simPrice)
                        break
                    } else if (touchedTarget) {
                        targetCount++
                        hitOutcome = plan.nominalR - costInR
                        simPrice = plan.takeProfitPrice
                        stepPrices[step].add(simPrice)
                        break
                    }
                }

                simPrice = barClose
                stepPrices[step].add(simPrice)
                step++
            }

            finalPrices.add(simPrice)

            if (hitOutcome != null) {
                rValues.add(hitOutcome)
                if (hitOutcome > 0.0) greenCount++
            } else {
                neitherCount++
                val grossR = if (plan.side == PositionSide.LONG) {
                    (simPrice - plan.entryPrice) / plan.riskPerUnit
                } else {
                    (plan.entryPrice - simPrice) / plan.riskPerUnit
                }
                val netR = grossR - costInR
                rValues.add(netR)
                if (grossR >= 0.0) greenCount++
            }
        }

        // Fan Chart percentiles per step
        val fanChartBands = mutableListOf<ReplayDistributionPoint>()
        for (s in 0 until horizon) {
            val pricesAtStep = stepPrices[s].sorted()
            if (pricesAtStep.isNotEmpty()) {
                fanChartBands.add(
                    ReplayDistributionPoint(
                        barIndex = s + 1,
                        p5 = pricesAtStep[(pricesAtStep.size * 0.05).toInt().coerceIn(0, pricesAtStep.lastIndex)],
                        p25 = pricesAtStep[(pricesAtStep.size * 0.25).toInt().coerceIn(0, pricesAtStep.lastIndex)],
                        p50 = pricesAtStep[(pricesAtStep.size * 0.50).toInt().coerceIn(0, pricesAtStep.lastIndex)],
                        p75 = pricesAtStep[(pricesAtStep.size * 0.75).toInt().coerceIn(0, pricesAtStep.lastIndex)],
                        p95 = pricesAtStep[(pricesAtStep.size * 0.95).toInt().coerceIn(0, pricesAtStep.lastIndex)]
                    )
                )
            }
        }

        val sortedFinal = finalPrices.sorted()
        val medianPrice = if (sortedFinal.isNotEmpty()) sortedFinal[sortedFinal.size / 2] else plan.entryPrice
        val p5Price = if (sortedFinal.isNotEmpty()) sortedFinal[(sortedFinal.size * 0.05).toInt().coerceIn(0, sortedFinal.lastIndex)] else plan.entryPrice
        val p95Price = if (sortedFinal.isNotEmpty()) sortedFinal[(sortedFinal.size * 0.95).toInt().coerceIn(0, sortedFinal.lastIndex)] else plan.entryPrice

        val histogramBins = buildPriceHistogram(sortedFinal, numBins = 15)
        val historySummary = runHistoricalPoolBacktest(plan, pool)

        val sideHitCount = targetCount + stopCount
        val observedTargetShare = if (sideHitCount > 0) targetCount.toDouble() / sideHitCount else 0.5

        val targetPct = (targetCount.toDouble() / numReplays) * 100.0
        val stopPct = (stopCount.toDouble() / numReplays) * 100.0
        val neitherPct = (neitherCount.toDouble() / numReplays) * 100.0
        val greenPct = (greenCount.toDouble() / numReplays) * 100.0
        val avgR = rValues.average()

        return ReplayPassMetrics(
            modeLabel = modeLabel,
            targetFirstPct = targetPct,
            stopFirstPct = stopPct,
            neitherPct = neitherPct,
            greenPct = greenPct,
            medianPrice = medianPrice,
            p5Price = p5Price,
            p95Price = p95Price,
            averageR = avgR + costInR,
            netAverageR = avgR,
            observedTargetShare = observedTargetShare,
            fanChartBands = fanChartBands,
            histogramBins = histogramBins,
            history = historySummary,
            resolvedCount = sideHitCount,
            sessionsCount = pool.totalSessions
        )
    }

    /**
     * Historical backtest: re-runs the exact plan rule across every full session in the pool.
     */
    private fun runHistoricalPoolBacktest(
        plan: ReplayPlan,
        pool: SessionPool
    ): HistoricalBacktestSummary {
        var fired = 0
        var targetHits = 0
        var stopHits = 0
        var neitherHits = 0
        val rList = mutableListOf<Double>()

        pool.sessions.forEach { session ->
            val startIdx = plan.timeOfDayIndex.coerceIn(0, maxOf(0, session.bars.lastIndex))
            val sessionBars = session.bars.drop(startIdx).take(plan.horizonBars)
            if (sessionBars.isNotEmpty()) {
                fired++
                var simP = plan.entryPrice
                var decided = false

                for (bRatio in sessionBars) {
                    val prevP = simP
                    val o = prevP * bRatio.openRatio
                    val h = max(o, prevP * bRatio.highRatio)
                    val l = min(o, prevP * bRatio.lowRatio)
                    val c = prevP * bRatio.closeRatio

                    if (plan.side == PositionSide.LONG) {
                        val touchStop = l <= plan.stopLossPrice
                        val touchTarget = h >= plan.takeProfitPrice

                        if (touchStop) {
                            stopHits++
                            rList.add(-1.0)
                            decided = true
                            break
                        } else if (touchTarget) {
                            targetHits++
                            rList.add(plan.nominalR)
                            decided = true
                            break
                        }
                    } else {
                        val touchStop = h >= plan.stopLossPrice
                        val touchTarget = l <= plan.takeProfitPrice

                        if (touchStop) {
                            stopHits++
                            rList.add(-1.0)
                            decided = true
                            break
                        } else if (touchTarget) {
                            targetHits++
                            rList.add(plan.nominalR)
                            decided = true
                            break
                        }
                    }
                    simP = c
                }

                if (!decided) {
                    neitherHits++
                    val grossR = if (plan.side == PositionSide.LONG) (simP - plan.entryPrice) / plan.riskPerUnit
                    else (plan.entryPrice - simP) / plan.riskPerUnit
                    rList.add(grossR)
                }
            }
        }

        return HistoricalBacktestSummary(
            firedCount = fired,
            targetCount = targetHits,
            stopCount = stopHits,
            neitherCount = neitherHits,
            averageR = if (rList.isNotEmpty()) rList.average() else 0.0
        )
    }

    private fun computeVerdict(
        observedTargetShare: Double,
        breakEvenTargetShare: Double,
        netR: Double,
        averageR: Double,
        neitherPct: Double,
        history: HistoricalBacktestSummary,
        seed: Long,
        conservativeModeLabel: String,
        driftDivergenceNote: String?,
        resolvedCount: Int = 200,
        sessionsCount: Int = 0,
        numReplays: Int = DEFAULT_NUM_REPLAYS
    ): ReplayVerdict {
        // If price reached neither target nor stop in most simulations, the replay cannot judge the plan.
        // (Before, 3% "stop first" + 97% "neither" was shown as "Strongly toward the stop", which is misleading.)
        val mostlyOpen = neitherPct > 75.0
        if (resolvedCount < 200 || mostlyOpen) {
            val headline = if (mostlyOpen && resolvedCount >= 200) {
                "Inconclusive: in ${String.format(Locale.US, "%.0f", neitherPct)}% of simulations price hit neither target nor stop in this time window"
            } else {
                "Not enough resolved replays ($resolvedCount/200 across $sessionsCount sessions)"
            }
            val details = mutableListOf(
                "Only $resolvedCount/$numReplays simulated paths touched target or stop within the horizon (minimum 200 resolved paths required for a directional lean).",
                "Target/stop distance is too wide relative to horizon volatility (${String.format(Locale.US, "%.1f", neitherPct)}% remained open at horizon)."
            )
            if (driftDivergenceNote != null) details.add(driftDivergenceNote)
            details.add("Reproducible draw seed: $seed")
            return ReplayVerdict(
                lean = ReplayLean.INSUFFICIENT_RESOLVED,
                headline = headline,
                details = details,
                breakEvenTargetShare = breakEvenTargetShare,
                observedTargetShare = observedTargetShare,
                seed = seed
            )
        }

        val diffPoints = (observedTargetShare - breakEvenTargetShare) * 100.0
        val is1R = abs(netR - 1.0) < 0.15

        val lean = when {
            diffPoints >= 15.0 -> ReplayLean.STRONGLY_TARGET
            diffPoints in 5.0..14.999 -> ReplayLean.LEANS_TARGET
            diffPoints in -5.0..4.999 -> ReplayLean.CLOSE_TO_BREAKEVEN
            diffPoints in -14.999..-5.001 -> ReplayLean.LEANS_STOP
            else -> ReplayLean.STRONGLY_STOP
        }

        val headline = when (lean) {
            ReplayLean.STRONGLY_TARGET -> "Strongly toward the target (+${String.format(Locale.US, "%.1f", diffPoints)} pts above break-even)"
            ReplayLean.LEANS_TARGET -> "Leans toward the target (+${String.format(Locale.US, "%.1f", diffPoints)} pts above break-even)"
            ReplayLean.CLOSE_TO_BREAKEVEN -> if (is1R) "Close to a coin flip (~50% at 1R)" else "Close to break-even (${String.format(Locale.US, "%.1f", diffPoints)} pts from ${String.format(Locale.US, "%.1f", breakEvenTargetShare * 100)}% BE)"
            ReplayLean.LEANS_STOP -> "Leans toward the stop (${String.format(Locale.US, "%.1f", diffPoints)} pts below break-even)"
            ReplayLean.STRONGLY_STOP -> "Strongly toward the stop (${String.format(Locale.US, "%.1f", diffPoints)} pts below break-even)"
            ReplayLean.INSUFFICIENT_RESOLVED -> "Not enough resolved replays ($resolvedCount/200)"
            ReplayLean.REFUSED -> "Replay Refused"
        }

        val details = mutableListOf<String>()
        details.add("Target-first share among $resolvedCount decided ($conservativeModeLabel): ${String.format(Locale.US, "%.1f", observedTargetShare * 100)}% vs Break-even requirement: ${String.format(Locale.US, "%.1f", breakEvenTargetShare * 100)}% (Net R: ${String.format(Locale.US, "%.2f", netR)})")
        if (driftDivergenceNote != null) {
            details.add(driftDivergenceNote)
        }

        if (neitherPct > 40.0) {
            details.add("Warning: High neither rate (${String.format(Locale.US, "%.1f", neitherPct)}% of runs remained open at the horizon).")
        }

        val leanFavorsTarget = lean == ReplayLean.STRONGLY_TARGET || lean == ReplayLean.LEANS_TARGET
        val leanFavorsStop = lean == ReplayLean.STRONGLY_STOP || lean == ReplayLean.LEANS_STOP
        if ((averageR < 0.0 && leanFavorsTarget) || (averageR > 0.0 && leanFavorsStop)) {
            details.add("Notice: Average R sign (${String.format(Locale.US, "%+.2f", averageR)}R) conflicts with binary target-vs-stop lean.")
        }

        if (history.firedCount > 0) {
            val histFavorsTarget = history.averageR > 0.1
            val histFavorsStop = history.averageR < -0.1
            if ((histFavorsTarget && leanFavorsStop) || (histFavorsStop && leanFavorsTarget)) {
                details.add("Notice: Historical record (${String.format(Locale.US, "%+.2f", history.averageR)}R over ${history.firedCount} sessions) diverges from simulated random draw.")
            }
        }

        details.add("Reproducible draw seed: $seed")

        return ReplayVerdict(
            lean = lean,
            headline = headline,
            details = details,
            breakEvenTargetShare = breakEvenTargetShare,
            observedTargetShare = observedTargetShare,
            seed = seed
        )
    }

    private fun buildPriceHistogram(prices: List<Double>, numBins: Int = 15): List<PriceHistogramBin> {
        if (prices.isEmpty()) return emptyList()
        val minP = prices.first()
        val maxP = prices.last()
        val span = maxP - minP
        if (span <= 0.0) {
            return listOf(PriceHistogramBin(minP, maxP, prices.size, 100.0))
        }

        val binSize = span / numBins
        val bins = Array(numBins) { i ->
            val bMin = minP + i * binSize
            val bMax = if (i == numBins - 1) maxP else bMin + binSize
            PriceHistogramBin(bMin, bMax, 0, 0.0)
        }

        val binCounts = IntArray(numBins)
        prices.forEach { p ->
            val binIdx = ((p - minP) / binSize).toInt().coerceIn(0, numBins - 1)
            binCounts[binIdx]++
        }

        val total = prices.size.toDouble()
        return bins.mapIndexed { idx, bin ->
            bin.copy(
                count = binCounts[idx],
                percent = (binCounts[idx] / total) * 100.0
            )
        }
    }

    private fun buildRefusalResult(plan: ReplayPlan, seed: Long, reason: String): ReplayResult {
        return ReplayResult(
            symbol = plan.symbol,
            seed = seed,
            numReplays = 0,
            targetFirstPct = 0.0,
            stopFirstPct = 0.0,
            neitherPct = 0.0,
            greenPct = 0.0,
            medianPrice = plan.entryPrice,
            p5Price = plan.entryPrice,
            p95Price = plan.entryPrice,
            averageR = 0.0,
            netAverageR = 0.0,
            fanChartBands = emptyList(),
            histogramBins = emptyList(),
            verdict = ReplayVerdict(
                lean = ReplayLean.REFUSED,
                headline = "Replay Refused",
                details = listOf(reason),
                breakEvenTargetShare = 0.5,
                observedTargetShare = 0.0,
                seed = seed
            ),
            history = null,
            refusalReason = reason
        )
    }
}
