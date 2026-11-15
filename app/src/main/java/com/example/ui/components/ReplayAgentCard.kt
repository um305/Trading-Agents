package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.daytrader.replay.PriceHistogramBin
import com.example.domain.daytrader.replay.ReplayDistributionPoint
import com.example.domain.daytrader.replay.ReplayLean
import com.example.domain.daytrader.replay.ReplayPassMetrics
import com.example.domain.daytrader.replay.ReplayResult
import com.example.domain.util.PriceFormatter
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun ReplayAgentCard(
    replayResult: ReplayResult?,
    entryPrice: Double = 0.0,
    stopLossPrice: Double = 0.0,
    targetPrice: Double = 0.0,
    modifier: Modifier = Modifier
) {
    if (replayResult == null) return

    val isRefusal = replayResult.refusalReason != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, TechCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("replay_agent_card")
    ) {
        // Card Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(TechCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AvTimer,
                        contentDescription = "Time Machine Replay",
                        tint = TechCyan,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "TIME MACHINE • REPLAY AGENT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${replayResult.numReplays} seeded bootstrap replays • Seed: ${replayResult.seed}",
                        fontSize = 8.sp,
                        color = TextMuted
                    )
                }
            }

            // Verdict Lean Badge
            val leanColor = when (replayResult.verdict.lean) {
                ReplayLean.STRONGLY_TARGET, ReplayLean.LEANS_TARGET -> BullishGreen
                ReplayLean.CLOSE_TO_BREAKEVEN -> TechCyan
                ReplayLean.LEANS_STOP, ReplayLean.STRONGLY_STOP -> BearishRed
                ReplayLean.INSUFFICIENT_RESOLVED, ReplayLean.REFUSED -> RiskAmber
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(leanColor.copy(alpha = 0.16f))
                    .border(1.dp, leanColor.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = replayResult.verdict.lean.label,
                    color = leanColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Question: If the next hours play out like this symbol's own recent 5m bars, how often does this plan hit target first, stop first, or neither?",
            fontSize = 9.sp,
            color = TextSecondary,
            lineHeight = 13.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isRefusal) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RiskAmber.copy(alpha = 0.12f))
                    .border(1.dp, RiskAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RiskAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = replayResult.refusalReason ?: "Replay refused: insufficient data.",
                        color = RiskAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            if (replayResult.verdict.lean == ReplayLean.INSUFFICIENT_RESOLVED) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(RiskAmber.copy(alpha = 0.14f))
                        .border(1.dp, RiskAmber.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                        .testTag("replay_insufficient_resolved_banner")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = RiskAmber, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${replayResult.verdict.headline}. Targets/stops exceeded horizon range — no directional edge claimed.",
                            color = RiskAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Active Conservative / Calibration Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TechCyan.copy(alpha = 0.1f))
                    .border(1.dp, TechCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (replayResult.verdict.lean == ReplayLean.INSUFFICIENT_RESOLVED) "RESOLVED PATHS" else "VERDICT & CALIBRATION P(WIN)",
                    color = TechCyan,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (replayResult.verdict.lean == ReplayLean.INSUFFICIENT_RESOLVED) {
                        "${replayResult.resolvedCount}/200 across ${replayResult.sessionsCount} sessions (no lean)"
                    } else {
                        "${replayResult.conservativeModeLabel} (less favorable • ${PriceFormatter.formatPercent(replayResult.effectivePWin * 100.0, 1)})"
                    },
                    color = TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Weekend / Pool Notice when weekend pool fell back to combined weekday+weekend
            replayResult.poolNotice?.takeIf { it.isNotBlank() }?.let { poolMsg ->
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TechCyan.copy(alpha = 0.12f))
                        .border(1.dp, TechCyan.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("replay_pool_notice")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Session pool notice",
                            tint = TechCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = poolMsg,
                            color = TechCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 12.sp
                        )
                    }
                }
            }

            // Divergence Alert when As-Is and Drift-Neutral differ by > 5 points
            replayResult.driftDivergenceNote?.let { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(RiskAmber.copy(alpha = 0.14f))
                        .border(1.dp, RiskAmber.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("replay_drift_divergence_line")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Drift divergence warning",
                            tint = RiskAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = note,
                            color = RiskAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 12.sp
                        )
                    }
                }
            }

            // Dual-Mode Side-by-Side Comparison: As-Is vs Drift-Neutral
            val asIs = replayResult.asIs
            val driftNeutral = replayResult.driftNeutral
            if (asIs != null && driftNeutral != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ReplayModeComparisonCard(
                        metrics = asIs,
                        isSelectedConservative = replayResult.conservativeModeLabel == asIs.modeLabel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("replay_as_is_card")
                    )
                    ReplayModeComparisonCard(
                        metrics = driftNeutral,
                        isSelectedConservative = replayResult.conservativeModeLabel == driftNeutral.modeLabel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("replay_drift_neutral_card")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4 Odds Metric Bars (driven by less favorable mode)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "ACTIVE DISTRIBUTION (${replayResult.conservativeModeLabel.uppercase(Locale.US)})",
                    color = TextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                OddsBar(
                    label = "Target First",
                    percent = replayResult.targetFirstPct,
                    barColor = BullishGreen,
                    subtext = "Touched target before stop"
                )
                OddsBar(
                    label = "Stop First",
                    percent = replayResult.stopFirstPct,
                    barColor = BearishRed,
                    subtext = "Touched stop before target"
                )
                OddsBar(
                    label = "Neither (Open at Horizon)",
                    percent = replayResult.neitherPct,
                    barColor = TextMuted,
                    subtext = "Still open at max-hold horizon"
                )
                OddsBar(
                    label = "Green %",
                    percent = replayResult.greenPct,
                    barColor = BullishGreen,
                    subtext = "Target first or above entry at horizon"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Break-even and Expectancy stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Break-even 1/(1+R)", color = TextMuted, fontSize = 8.sp)
                    Text(
                        text = PriceFormatter.formatPercent(replayResult.verdict.breakEvenTargetShare * 100.0, 1),
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Observed Target-First", color = TextMuted, fontSize = 8.sp)
                    val diff = (replayResult.verdict.observedTargetShare - replayResult.verdict.breakEvenTargetShare) * 100.0
                    Text(
                        text = "${PriceFormatter.formatPercent(replayResult.verdict.observedTargetShare * 100.0, 1)} (${PriceFormatter.formatSignedPercent(diff, 1)})",
                        color = if (diff >= 0) BullishGreen else BearishRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Average Net R", color = TextMuted, fontSize = 8.sp)
                    Text(
                        text = PriceFormatter.formatRMultiple(replayResult.netAverageR),
                        color = if (replayResult.netAverageR >= 0) BullishGreen else BearishRed,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }
            }

            // Historical Backtest Re-run Summary
            replayResult.history?.let { hist ->
                Spacer(modifier = Modifier.height(6.dp))
                val targetShare = if (hist.firedCount > 0) (hist.targetCount.toDouble() / hist.firedCount) * 100.0 else 0.0
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Historical record across ${hist.firedCount} sessions: ${hist.targetCount} target first (${PriceFormatter.formatPercent(targetShare, 1)}), ${hist.stopCount} stop first, avg ${PriceFormatter.formatRMultiple(hist.averageR)}",
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }
            }

            // Fan-Chart Canvas
            if (replayResult.fanChartBands.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SIMULATED FAN-CHART (p5, p25, median, p75, p95 across horizon)",
                    color = TextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                FanChartCanvas(
                    bands = replayResult.fanChartBands,
                    entryPrice = entryPrice,
                    stopPrice = stopLossPrice,
                    targetPrice = targetPrice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF070E20))
                )
            }

            // Price Distribution Histogram
            if (replayResult.histogramBins.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PRICE DISTRIBUTION AT HORIZON (Median: ${PriceFormatter.formatCurrency(replayResult.medianPrice)})",
                    color = TextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                PriceHistogramView(
                    bins = replayResult.histogramBins,
                    medianPrice = replayResult.medianPrice,
                    entryPrice = entryPrice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF070E20))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mandatory Disclaimer Notice
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkBackground.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = replayResult.disclaimerNotice,
                    color = TextMuted,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReplayModeComparisonCard(
    metrics: ReplayPassMetrics,
    isSelectedConservative: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelectedConservative) TechCyan.copy(alpha = 0.7f) else DarkBorder
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = metrics.modeLabel.uppercase(Locale.US),
                color = if (isSelectedConservative) TechCyan else TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (isSelectedConservative) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TechCyan.copy(alpha = 0.18f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "USED",
                        color = TechCyan,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
        ComparisonStatRow("P(win)", PriceFormatter.formatPercent(metrics.observedTargetShare * 100.0, 1), if (metrics.observedTargetShare >= 0.5) BullishGreen else TextPrimary)
        ComparisonStatRow("Target 1st", PriceFormatter.formatPercent(metrics.targetFirstPct, 1), BullishGreen)
        ComparisonStatRow("Stop 1st", PriceFormatter.formatPercent(metrics.stopFirstPct, 1), BearishRed)
        ComparisonStatRow("Neither", PriceFormatter.formatPercent(metrics.neitherPct, 1), TextMuted)
        ComparisonStatRow("Green %", PriceFormatter.formatPercent(metrics.greenPct, 1), TextSecondary)
        ComparisonStatRow("Avg Net R", PriceFormatter.formatRMultiple(metrics.netAverageR), if (metrics.netAverageR >= 0) BullishGreen else BearishRed)
        ComparisonStatRow("Median", PriceFormatter.formatCurrency(metrics.medianPrice), TextSecondary)
    }
}

@Composable
private fun ComparisonStatRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 8.sp)
        Text(value, color = valueColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OddsBar(
    label: String,
    percent: Double,
    barColor: Color,
    subtext: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "${String.format(Locale.US, "%.1f", percent)}%",
                color = barColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(DarkBackground)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percent / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun FanChartCanvas(
    bands: List<ReplayDistributionPoint>,
    entryPrice: Double,
    stopPrice: Double,
    targetPrice: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (bands.size < 2) return@Canvas

        val w = size.width
        val h = size.height

        val allPrices = bands.flatMap { listOf(it.p5, it.p25, it.p50, it.p75, it.p95) } +
                listOfNotNull(entryPrice.takeIf { it > 0 }, stopPrice.takeIf { it > 0 }, targetPrice.takeIf { it > 0 })

        val minP = allPrices.minOrNull() ?: entryPrice * 0.95
        val maxP = allPrices.maxOrNull() ?: entryPrice * 1.05
        val pRange = (maxP - minP).coerceAtLeast(0.0001)

        fun yForPrice(p: Double): Float = (h - ((p - minP) / pRange * h)).toFloat().coerceIn(0f, h)
        fun xForIndex(idx: Int): Float = (idx.toFloat() / (bands.size - 1)) * w

        // Draw outer band (p5 to p95)
        val p5_p95Path = Path().apply {
            moveTo(xForIndex(0), yForPrice(bands[0].p95))
            for (i in 1 until bands.size) {
                lineTo(xForIndex(i), yForPrice(bands[i].p95))
            }
            for (i in bands.indices.reversed()) {
                lineTo(xForIndex(i), yForPrice(bands[i].p5))
            }
            close()
        }
        drawPath(p5_p95Path, color = TechCyan.copy(alpha = 0.12f))

        // Draw inner band (p25 to p75)
        val p25_p75Path = Path().apply {
            moveTo(xForIndex(0), yForPrice(bands[0].p75))
            for (i in 1 until bands.size) {
                lineTo(xForIndex(i), yForPrice(bands[i].p75))
            }
            for (i in bands.indices.reversed()) {
                lineTo(xForIndex(i), yForPrice(bands[i].p25))
            }
            close()
        }
        drawPath(p25_p75Path, color = TechCyan.copy(alpha = 0.22f))

        // Draw Median line (p50)
        val medianPath = Path().apply {
            moveTo(xForIndex(0), yForPrice(bands[0].p50))
            for (i in 1 until bands.size) {
                lineTo(xForIndex(i), yForPrice(bands[i].p50))
            }
        }
        drawPath(medianPath, color = TechCyan, style = Stroke(width = 1.5f, cap = StrokeCap.Round))

        // Draw entry reference line
        if (entryPrice > 0.0) {
            val yEntry = yForPrice(entryPrice)
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(0f, yEntry),
                end = Offset(w, yEntry),
                strokeWidth = 1f
            )
        }

        // Target and stop reference lines
        if (targetPrice > 0.0) {
            val yTarget = yForPrice(targetPrice)
            drawLine(
                color = BullishGreen.copy(alpha = 0.6f),
                start = Offset(0f, yTarget),
                end = Offset(w, yTarget),
                strokeWidth = 1f
            )
        }
        if (stopPrice > 0.0) {
            val yStop = yForPrice(stopPrice)
            drawLine(
                color = BearishRed.copy(alpha = 0.6f),
                start = Offset(0f, yStop),
                end = Offset(w, yStop),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
private fun PriceHistogramView(
    bins: List<PriceHistogramBin>,
    medianPrice: Double,
    entryPrice: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (bins.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height

        val maxPct = bins.maxOfOrNull { it.percent }?.coerceAtLeast(1.0) ?: 100.0
        val binWidth = w / bins.size

        bins.forEachIndexed { idx, bin ->
            val barHeight = ((bin.percent / maxPct) * h).toFloat()
            val x = idx * binWidth
            val y = h - barHeight

            val isGreen = bin.binMin >= entryPrice
            val barColor = if (isGreen) BullishGreen.copy(alpha = 0.7f) else BearishRed.copy(alpha = 0.7f)

            drawRect(
                color = barColor,
                topLeft = Offset(x + 1f, y),
                size = androidx.compose.ui.geometry.Size((binWidth - 2f).coerceAtLeast(1f), barHeight)
            )
        }
    }
}
