package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.EntryOrderType
import com.example.domain.model.PositionSide
import com.example.domain.model.TradePlanLifecycleStatus
import com.example.domain.model.TradeSetupPlan
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
fun TradeExecutionSetupCard(
    setupPlan: TradeSetupPlan?,
    currentPrice: Double,
    selectedSide: PositionSide,
    selectedLeverage: Int,
    onSelectSide: (PositionSide) -> Unit,
    onSelectLeverage: (Int) -> Unit,
    onArmPriceAlert: (Double) -> Unit,
    onRecalibrate: (() -> Unit)? = null,
    candles: List<com.example.domain.model.CandlePoint> = emptyList(),
    modifier: Modifier = Modifier
) {
    val plan = setupPlan
    if (plan == null ||
        !plan.isAvailable ||
        plan.verdictState == com.example.domain.model.SignalVerdictState.NO_TRADE ||
        plan.entryType == EntryOrderType.WAIT_NO_TRADE ||
        plan.recommendedEntry <= 0.0 ||
        plan.stopLoss <= 0.0
    ) {
        return
    }
    val isLong = selectedSide == PositionSide.LONG
    val themeColor by animateColorAsState(if (isLong) BullishGreen else BearishRed, label = "themeColor")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, themeColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("trade_execution_setup_card")
    ) {
        // Header
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(themeColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLong) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = themeColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "POSITION EXECUTION MATRIX",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Exact Entry Zone & Safety • ${setupPlan?.timeframe?.label ?: "15m"} Execution Plan",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }
            }

            // Side Selector Toggle (LONG vs SHORT)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isLong) BullishGreen else Color.Transparent)
                        .clickable { onSelectSide(PositionSide.LONG) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("btn_side_long")
                ) {
                    Text(
                        text = "START LONG",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLong) Color.Black else TextSecondary,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (!isLong) BearishRed else Color.Transparent)
                        .clickable { onSelectSide(PositionSide.SHORT) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("btn_side_short")
                ) {
                    Text(
                        text = "START SHORT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isLong) Color.White else TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val plan = setupPlan
        if (plan == null || !plan.isAvailable || plan.entryType == EntryOrderType.WAIT_NO_TRADE || plan.lifecycleStatus == TradePlanLifecycleStatus.ANALYZING) {
            val isAnalyzing = plan?.lifecycleStatus == TradePlanLifecycleStatus.ANALYZING
            val reason = when {
                isAnalyzing -> "Analyzing agent pipeline across all nodes..."
                plan != null && plan.unavailableReason.isNotBlank() -> plan.unavailableReason
                else -> "WAIT / NO TRADE: Waiting for agent consensus & verified ${setupPlan?.timeframe?.label ?: "selected"} candles..."
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isAnalyzing) Icons.Default.Refresh else Icons.Default.Info,
                        contentDescription = null,
                        tint = TechCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = reason,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isAnalyzing) "Scanning SMC order blocks, news catalysts, and agent votes..." else "Trade setup will generate automatically once verified live conditions arrive.",
                        fontSize = 10.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            val livePrice = if (currentPrice > 0.0) currentPrice else plan.currentPrice
            val distToEntry = ((livePrice - plan.recommendedEntry) / plan.recommendedEntry) * 100.0
            val zoneMin = minOf(plan.entryZoneStart, plan.entryZoneEnd)
            val zoneMax = maxOf(plan.entryZoneStart, plan.entryZoneEnd)
            val isStrictlyInZone = livePrice in zoneMin..zoneMax && plan.verdictState == com.example.domain.model.SignalVerdictState.TRADE
            val isInZone = isStrictlyInZone

            val rawStatus = if (candles.isNotEmpty()) plan.evaluateWithCandles(livePrice, candles) else plan.evaluateStatus(livePrice)
            val status = if (rawStatus == TradePlanLifecycleStatus.IN_ENTRY_ZONE && !isStrictlyInZone) {
                TradePlanLifecycleStatus.PENDING_ENTRY
            } else {
                rawStatus
            }
            val statusLabel = when (status) {
                TradePlanLifecycleStatus.ANALYZING -> "STATUS: ANALYZING AGENT PIPELINE..."
                TradePlanLifecycleStatus.EXPIRED -> "STATUS: PLAN EXPIRED (RECALIBRATING)"
                TradePlanLifecycleStatus.INVALIDATED -> "STATUS: STRUCTURE INVALIDATED"
                TradePlanLifecycleStatus.TP1_REACHED -> "STATUS: TARGET 1 REACHED — SECURE PROFITS"
                TradePlanLifecycleStatus.TP2_REACHED -> "STATUS: TARGET 2 REACHED — RUNNER ACTIVE"
                TradePlanLifecycleStatus.TP3_REACHED -> "STATUS: ALL TARGETS COMPLETED"
                TradePlanLifecycleStatus.STOPPED_OUT -> "STATUS: STOP LOSS HIT — INVALIDATED"
                TradePlanLifecycleStatus.IN_ENTRY_ZONE -> "STATUS: IN ENTRY ZONE NOW (TRIGGER ARMED)"
                TradePlanLifecycleStatus.ACTIVE_IN_TRADE -> "STATUS: ACTIVE IN TRADE (WORKING TO TP1)"
                TradePlanLifecycleStatus.PENDING_ENTRY -> if (plan.verdictState == com.example.domain.model.SignalVerdictState.WAIT) "STATUS: WAIT — MONITORING FOR TRIGGER/ZONE" else "STATUS: UPCOMING PLANNED ENTRY (PENDING LIMIT)"
            }
            val statusColor = when (status) {
                TradePlanLifecycleStatus.ANALYZING -> TechCyan
                TradePlanLifecycleStatus.EXPIRED, TradePlanLifecycleStatus.INVALIDATED -> BearishRed
                TradePlanLifecycleStatus.TP1_REACHED, TradePlanLifecycleStatus.TP2_REACHED, TradePlanLifecycleStatus.TP3_REACHED -> BullishGreen
                TradePlanLifecycleStatus.STOPPED_OUT -> BearishRed
                TradePlanLifecycleStatus.IN_ENTRY_ZONE -> BullishGreen
                TradePlanLifecycleStatus.ACTIVE_IN_TRADE -> TechCyan
                TradePlanLifecycleStatus.PENDING_ENTRY -> TechCyan
            }

            if (plan.isAgainstTrend) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF331B0B))
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AGAINST TREND WARNING: ${plan.trendWarning ?: "Price action opposes this setup"}",
                            color = Color(0xFFF59E0B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Forward-Looking Execution Indicator Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${plan.timeframe.label} PLAN • FORWARD EXECUTION",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
            }

            val isFinished = status == TradePlanLifecycleStatus.TP3_REACHED ||
                    status == TradePlanLifecycleStatus.STOPPED_OUT ||
                    (kotlin.math.abs(livePrice - plan.recommendedEntry) / plan.recommendedEntry > 0.18)

            if (isFinished && onRecalibrate != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(TechCyan.copy(alpha = 0.16f))
                        .border(1.dp, TechCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable { onRecalibrate() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("btn_recalibrate_next_plan"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = TechCyan, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CALCULATE NEXT TRADE SETUP FOR $${formatPrice(livePrice)}",
                            color = TechCyan,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Execution Entry Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(themeColor.copy(alpha = 0.10f))
                    .border(1.dp, themeColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isLong) "PLANNED LONG ENTRY (LIMIT ORDER)" else "PLANNED SHORT ENTRY (LIMIT ORDER)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = themeColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val badgeColor = when (status) {
                                    TradePlanLifecycleStatus.STOPPED_OUT, TradePlanLifecycleStatus.INVALIDATED, TradePlanLifecycleStatus.EXPIRED -> BearishRed
                                    TradePlanLifecycleStatus.TP1_REACHED, TradePlanLifecycleStatus.TP2_REACHED, TradePlanLifecycleStatus.TP3_REACHED -> BullishGreen
                                    TradePlanLifecycleStatus.IN_ENTRY_ZONE -> BullishGreen
                                    else -> themeColor
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(badgeColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = when (status) {
                                            TradePlanLifecycleStatus.STOPPED_OUT -> "STOP LOSS HIT"
                                            TradePlanLifecycleStatus.INVALIDATED -> "INVALIDATED"
                                            TradePlanLifecycleStatus.EXPIRED -> "PLAN EXPIRED"
                                            TradePlanLifecycleStatus.TP3_REACHED -> "TP3 REACHED"
                                            TradePlanLifecycleStatus.TP2_REACHED -> "TP2 REACHED"
                                            TradePlanLifecycleStatus.TP1_REACHED -> "TP1 REACHED"
                                            TradePlanLifecycleStatus.IN_ENTRY_ZONE -> "ACTIVE IN ZONE"
                                            TradePlanLifecycleStatus.ACTIVE_IN_TRADE -> "ACTIVE TRADE"
                                            else -> "PENDING TRIGGER"
                                        },
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$${formatPrice(plan.recommendedEntry)}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = themeColor
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "ACCUMULATION ENTRY ZONE",
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$${formatPrice(plan.entryZoneStart)} – $${formatPrice(plan.entryZoneEnd)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = plan.reasoning,
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Real-Time Dynamic Market Distance & Status Tracker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, if (isInZone) BullishGreen.copy(alpha = 0.6f) else DarkBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isInZone) BullishGreen else TechCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val isTradeArmed = plan.verdictState == com.example.domain.model.SignalVerdictState.TRADE
                            val triggerName = plan.structureState.ifBlank { "armed trigger confirmation" }
                            Text(
                                text = when {
                                    !isTradeArmed -> "Conditional plan - waiting for: $triggerName"
                                    isInZone -> "● IN OPTIMAL ENTRY ZONE (ACTIONABLE RIGHT NOW)"
                                    isLong && livePrice > zoneMax -> "UPCOMING: WAITING FOR PULLBACK TO ENTRY"
                                    isLong && livePrice < zoneMin -> "UPCOMING: DEEP ACCUMULATION BELOW ZONE"
                                    !isLong && livePrice < zoneMin -> "UPCOMING: WAITING FOR RETEST TO ENTRY"
                                    else -> "UPCOMING: HIGH-CONVICTION SHORT SUPPLY"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTradeArmed && isInZone) BullishGreen else TechCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Live Market: $${formatPrice(livePrice)} • ${if (distToEntry >= 0) "+" else ""}${String.format(Locale.US, "%.2f", distToEntry)}% ($${formatPrice(Math.abs(livePrice - plan.recommendedEntry))}) away from Planned Limit Entry",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }

                    if (onRecalibrate != null) {
                        IconButton(
                            onClick = onRecalibrate,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkBorder.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-Anchor Entry to Market Structure",
                                tint = TechCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // "Why this entry" Confluence & Agents Breakdown Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, TechCyan.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "WHY THIS ENTRY • CONFLUENCE ${plan.confluenceScore}/100",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TechCyan,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TechCyan.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = plan.entryType.label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TechCyan,
                                maxLines = 1
                            )
                        }
                    }
                    if (plan.contributingNodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Contributors: ${plan.contributingNodes.joinToString(" • ")}",
                            fontSize = 9.sp,
                            color = TextSecondary,
                            lineHeight = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Invalidation: $${formatPrice(plan.invalidationPrice)}",
                            fontSize = 9.sp,
                            color = BearishRed,
                            fontWeight = FontWeight.Bold
                        )
                        val expiryMinutes = if (plan.expiryTimestamp > System.currentTimeMillis()) {
                            (plan.expiryTimestamp - System.currentTimeMillis()) / 60000L
                        } else 0L
                        Text(
                            text = "Expiry: ${expiryMinutes}m (6 ${plan.timeframe.label} candles)",
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }
                    if (plan.trendWarning != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Warning: ${plan.trendWarning}",
                            fontSize = 9.sp,
                            color = RiskAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Leverage Selector (1x, 3x, 5x, 10x, 20x, 50x)
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Leverage Multiplier",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Text(
                        text = "${selectedLeverage}x Isolated",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TechCyan
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(2, 5, 10, 20, 50).forEach { lev ->
                        val isLevSelected = lev == selectedLeverage
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isLevSelected) TechCyan.copy(alpha = 0.25f) else DarkSurfaceElevated)
                                .border(1.dp, if (isLevSelected) TechCyan else DarkBorder, RoundedCornerShape(6.dp))
                                .clickable { onSelectLeverage(lev) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${lev}x",
                                fontSize = 11.sp,
                                fontWeight = if (isLevSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isLevSelected) TechCyan else TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val liveTp1Percent = if (livePrice > 0.0) Math.abs((plan.takeProfit1 - livePrice) / livePrice) * 100.0 * selectedLeverage else plan.expectedGainPercent
            val liveTp2Percent = if (livePrice > 0.0) Math.abs((plan.takeProfit2 - livePrice) / livePrice) * 100.0 * selectedLeverage else plan.expectedGainPercent * 1.8
            val liveSlPercent = if (livePrice > 0.0) Math.abs((livePrice - plan.stopLoss) / livePrice) * 100.0 * selectedLeverage else plan.maxLossPercent
            val liveLiqBuffer = if (livePrice > 0.0) Math.abs((livePrice - plan.estimatedLiquidationPrice) / livePrice) * 100.0 else (100.0 / selectedLeverage)

            // 4-Column Targets & Risk Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // TP1 Target
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .padding(8.dp)
                ) {
                    Column {
                        Text("TARGET TP1", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$${formatPrice(plan.takeProfit1)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BullishGreen)
                        Text("+${String.format(Locale.US, "%.1f", liveTp1Percent)}%", fontSize = 9.sp, color = BullishGreen)
                    }
                }

                // TP2 Target
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .padding(8.dp)
                ) {
                    Column {
                        Text("TARGET TP2", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$${formatPrice(plan.takeProfit2)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TechCyan)
                        Text("+${String.format(Locale.US, "%.1f", liveTp2Percent)}%", fontSize = 9.sp, color = TechCyan)
                    }
                }

                // Stop Loss
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .padding(8.dp)
                ) {
                    Column {
                        Text("STOP LOSS", fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$${formatPrice(plan.stopLoss)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BearishRed)
                        Text("-${String.format(Locale.US, "%.1f", liveSlPercent)}%", fontSize = 9.sp, color = BearishRed)
                    }
                }

                // Estimated Liquidation
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, BearishRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text("LIQUIDATION", fontSize = 8.sp, color = BearishRed, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$${formatPrice(plan.estimatedLiquidationPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BearishRed)
                        Text("${String.format(Locale.US, "%.1f", liveLiqBuffer)}% Safe", fontSize = 8.sp, color = TextMuted)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action: Arm Push Notification Alert at Entry Price
            Button(
                onClick = { onArmPriceAlert(plan.recommendedEntry) },
                colors = ButtonDefaults.buttonColors(containerColor = themeColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_arm_entry_alert")
            ) {
                Icon(
                    imageVector = Icons.Default.AddAlert,
                    contentDescription = null,
                    tint = if (isLong) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Arm Price Alert for Planned Entry @ $${formatPrice(plan.recommendedEntry)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (isLong) Color.Black else Color.White
                )
            }
        }
    }
}

private fun formatPrice(price: Double): String {
    return com.example.domain.util.PriceFormatter.formatPrice(price)
}
