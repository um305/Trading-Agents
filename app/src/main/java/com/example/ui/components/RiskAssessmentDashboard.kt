package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.WatchlistItem
import com.example.domain.model.MarketQuote
import com.example.domain.model.PositionSide
import com.example.domain.model.TradeSetupPlan
import com.example.domain.util.PriceFormatter
import com.example.ui.theme.AgentPurple
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
import kotlin.math.abs

@Composable
fun RiskAssessmentDashboard(
    quote: MarketQuote?,
    setupPlan: TradeSetupPlan?,
    sentimentScore: Double, // -1.0 to 1.0
    sentimentLabel: String,
    selectedLeverage: Int,
    watchlist: List<WatchlistItem>,
    onSelectLeverage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }
    var simulatedSentiment by remember {
        mutableFloatStateOf(((sentimentScore.coerceIn(-1.0, 1.0) + 1.0) * 50.0).toFloat()) // 0 to 100
    }
    var activeTab by remember { mutableIntStateOf(0) } // 0: Native Risk Arc, 1: Native Stress Curve

    val currentPrice = quote?.currentPrice ?: 100.0
    val symbol = quote?.symbol ?: "BTC"
    val isLong = setupPlan?.side != PositionSide.SHORT

    // Calculate real-time liquidation buffer and risk index
    val lev = selectedLeverage.coerceIn(1, 100)
    val maintenanceMarginRate = 0.005 // Binance standard 0.5%
    val liqPrice = if (isLong) {
        currentPrice * (1.0 - (1.0 / lev) + maintenanceMarginRate)
    } else {
        currentPrice * (1.0 + (1.0 / lev) - maintenanceMarginRate)
    }.coerceAtLeast(0.0)

    val rawLiqBufferPercent = (abs(currentPrice - liqPrice) / currentPrice) * 100.0
    val sentimentFactor = (simulatedSentiment - 50.0) / 50.0 // -1.0 to +1.0
    val effectiveSentimentDampener = if (isLong) -sentimentFactor * 10.0 else sentimentFactor * 10.0

    // Adjusted Risk Score (0 = ultra safe, 100 = extreme liquidation hazard)
    val baseLeverageRisk = (lev.toDouble() / 50.0) * 50.0
    val bufferRiskPenalty = (100.0 - (rawLiqBufferPercent * 4.0)).coerceIn(0.0, 50.0)
    val adjustedRiskScore = (baseLeverageRisk + bufferRiskPenalty + effectiveSentimentDampener).coerceIn(5.0, 98.0)

    val riskColor = when {
        adjustedRiskScore >= 75.0 -> BearishRed
        adjustedRiskScore >= 50.0 -> RiskAmber
        adjustedRiskScore >= 25.0 -> TechCyan
        else -> BullishGreen
    }

    val riskTierLabel = when {
        adjustedRiskScore >= 75.0 -> "HIGH RISK EXPOSURE"
        adjustedRiskScore >= 50.0 -> "ELEVATED RISK"
        adjustedRiskScore >= 25.0 -> "MODERATE RISK"
        else -> "LOW CONSERVATIVE RISK"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_risk_assessment_dashboard"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(riskColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = riskColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "RISK & LIQUIDATION DASHBOARD",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Native Compose Canvas • $riskTierLabel",
                            fontSize = 9.sp,
                            color = riskColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(riskColor.copy(alpha = 0.2f))
                            .border(1.dp, riskColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${adjustedRiskScore.toInt()}/100",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = riskColor
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    // Tab Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTab == 0) TechCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { activeTab = 0 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📊 Liquidation Arc",
                                fontSize = 10.sp,
                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeTab == 0) TechCyan else TextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (activeTab == 1) AgentPurple.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { activeTab = 1 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🌊 Stress Curve",
                                fontSize = 10.sp,
                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (activeTab == 1) AgentPurple else TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Native Compose Canvas Risk Visualizer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkBackground)
                            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeTab == 0) {
                            // Tab 0: Native Liquidation Gauge Arc
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(180.dp, 120.dp),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        val strokeWidth = 14.dp.toPx()
                                        val arcSize = Size(size.width - strokeWidth, (size.height * 2) - strokeWidth)
                                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                                        // Background Arc (180 to 360 degrees)
                                        drawArc(
                                            color = DarkSurfaceElevated,
                                            startAngle = 180f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                        )

                                        // Value Arc
                                        val sweep = (adjustedRiskScore.toFloat() / 100f) * 180f
                                        drawArc(
                                            color = riskColor,
                                            startAngle = 180f,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                        )
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = "${adjustedRiskScore.toInt()}%",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = riskColor
                                        )
                                        Text(
                                            text = "LIQUIDATION HAZARD",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Est. Liquidation Price: ${PriceFormatter.formatCurrency(liqPrice)} (${PriceFormatter.formatPercent(rawLiqBufferPercent, 1)} buffer)",
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            // Tab 1: Native Sentiment Stress Curve
                            Canvas(modifier = Modifier.matchParentSize()) {
                                val w = size.width
                                val h = size.height

                                // Draw baseline horizontal axis (zero line)
                                val midY = h * 0.5f
                                drawLine(
                                    color = DarkBorder,
                                    start = Offset(0f, midY),
                                    end = Offset(w, midY),
                                    strokeWidth = 1.dp.toPx()
                                )

                                // Draw Liquidation Boundary Line
                                val liqY = if (isLong) h * 0.85f else h * 0.15f
                                drawLine(
                                    color = BearishRed.copy(alpha = 0.5f),
                                    start = Offset(0f, liqY),
                                    end = Offset(w, liqY),
                                    strokeWidth = 1.dp.toPx()
                                )

                                // Stress Curve Path
                                val curvePath = Path()
                                curvePath.moveTo(0f, if (isLong) h * 0.75f else h * 0.25f)

                                val step = w / 20f
                                for (i in 0..20) {
                                    val x = i * step
                                    val t = (i / 20f) // 0 to 1
                                    val pnlMultiplier = if (isLong) (t - 0.5f) * 2f else (0.5f - t) * 2f
                                    val y = midY - (pnlMultiplier * (h * 0.35f))
                                    curvePath.lineTo(x, y.coerceIn(10f, h - 10f))
                                }

                                drawPath(
                                    path = curvePath,
                                    color = AgentPurple,
                                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                )

                                // Active stress point dot
                                val currentT = simulatedSentiment / 100f
                                val activeX = currentT * w
                                val activePnl = if (isLong) (currentT - 0.5f) * 2f else (0.5f - currentT) * 2f
                                val activeY = (midY - (activePnl * (h * 0.35f))).coerceIn(10f, h - 10f)

                                drawCircle(
                                    color = TechCyan,
                                    radius = 6.dp.toPx(),
                                    center = Offset(activeX, activeY)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 2.5.dp.toPx(),
                                    center = Offset(activeX, activeY)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Interactive AI Sentiment Stress-Testing Slider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = AgentPurple,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sentiment Shock Stress-Test",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = when {
                                        simulatedSentiment >= 70f -> "Extreme Greed (+${String.format(Locale.US, "%.0f", simulatedSentiment - 50)})"
                                        simulatedSentiment >= 55f -> "Mild Bullish (+${String.format(Locale.US, "%.0f", simulatedSentiment - 50)})"
                                        simulatedSentiment >= 45f -> "Neutral Tape (0)"
                                        simulatedSentiment >= 30f -> "Bearish Pressure (${String.format(Locale.US, "%.0f", simulatedSentiment - 50)})"
                                        else -> "Extreme Panic (${String.format(Locale.US, "%.0f", simulatedSentiment - 50)})"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (simulatedSentiment >= 55f) BullishGreen else if (simulatedSentiment >= 45f) RiskAmber else BearishRed
                                )
                            }

                            Slider(
                                value = simulatedSentiment,
                                onValueChange = { simulatedSentiment = it },
                                valueRange = 0f..100f,
                                colors = SliderDefaults.colors(
                                    thumbColor = TechCyan,
                                    activeTrackColor = if (simulatedSentiment >= 50f) BullishGreen else BearishRed,
                                    inactiveTrackColor = DarkBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Leverage Selector Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Simulate Leverage Multiplier:",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        Row {
                            listOf(1, 3, 5, 10, 20, 50).forEach { levOption ->
                                val isSelected = selectedLeverage == levOption
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) TechCyan.copy(alpha = 0.25f) else DarkSurfaceElevated)
                                        .border(1.dp, if (isSelected) TechCyan else DarkBorder, RoundedCornerShape(6.dp))
                                        .clickable { onSelectLeverage(levOption) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${levOption}x",
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TechCyan else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4 Metric Summary Tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Tile 1: Liquidation Buffer
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceElevated)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("LIQ BUFFER", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", rawLiqBufferPercent)}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (rawLiqBufferPercent > 10.0) BullishGreen else BearishRed
                                )
                                Text(
                                    text = "${PriceFormatter.formatCurrency(abs(currentPrice - liqPrice))} away",
                                    fontSize = 8.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Tile 2: Stop Loss Cushion
                        val stopDistance = setupPlan?.takeIf { it.recommendedEntry > 0.0 && it.stopLoss > 0.0 }?.let { abs(it.recommendedEntry - it.stopLoss) } ?: (currentPrice * 0.02)
                        val stopDistancePercent = if (currentPrice > 0.0) (stopDistance / currentPrice) * 100.0 else Double.NaN
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceElevated)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("STOP CUSHION", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = PriceFormatter.formatPercent(stopDistancePercent, 1),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = RiskAmber
                                )
                                Text(
                                    text = "${PriceFormatter.formatCurrency(stopDistance)} gap",
                                    fontSize = 8.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Tile 3: Effective Leverage
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceElevated)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("LEVERAGE", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${selectedLeverage}x",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selectedLeverage <= 10) BullishGreen else BearishRed
                                )
                                Text(
                                    text = "Notional Sizing",
                                    fontSize = 8.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Tile 4: Margin Health
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceElevated)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("MARGIN CUSHION", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", (100.0 - (100.0 / lev)).coerceAtLeast(0.0))}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TechCyan
                                )
                                Text(
                                    text = "Collateral Ratio",
                                    fontSize = 8.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chief Risk Officer Recommendation Note
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(riskColor.copy(alpha = 0.08f))
                            .border(1.dp, riskColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (adjustedRiskScore >= 50.0) Icons.Default.Warning else Icons.Default.Security,
                                contentDescription = null,
                                tint = riskColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    adjustedRiskScore >= 75.0 -> "CRITICAL WARNING: High leverage (${selectedLeverage}x) with adverse sentiment poses liquidation risk within ${String.format(Locale.US, "%.1f", rawLiqBufferPercent)}% price fluctuation. Reduce leverage or set tight stop loss."
                                    adjustedRiskScore >= 50.0 -> "CAUTION: Moderate liquidation vulnerability. Current sentiment buffer is tightening. Ensure adequate margin collateral."
                                    else -> "SAFE EXPOSURE: Position maintains a robust ${String.format(Locale.US, "%.1f", rawLiqBufferPercent)}% cushion from liquidation. AI sentiment supports favorable risk-to-reward."
                                },
                                fontSize = 10.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatPriceNumber(price: Double): String {
    return PriceFormatter.formatPrice(price)
}
