package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.master.AgentId
import com.example.domain.master.AgentMatrix
import com.example.domain.master.AgentMatrixCell
import com.example.domain.master.AgentStatus
import com.example.domain.master.HorizonPlan
import com.example.domain.master.MasterVerdict
import com.example.domain.master.OutlookSummary
import com.example.domain.master.SpotActionMode
import com.example.domain.master.TradingHorizon
import com.example.domain.model.PositionSide
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.TradingMarketType
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
import kotlinx.coroutines.delay
import java.util.Locale

private fun safeFmtPrice(v: Double?): String =
    v?.takeIf { it.isFinite() && it > 0.0 }?.let { PriceFormatter.formatCurrency(it) } ?: "-"

private fun safeFmtPct(v: Double?, signed: Boolean = true): String {
    if (v == null || !v.isFinite()) return "-"
    val sign = if (signed && v >= 0.0) "+" else ""
    return String.format(Locale.US, "%s%.1f%%", sign, v)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MasterVerdictCard(
    verdict: MasterVerdict,
    selectedHorizon: TradingHorizon,
    isArmedForSelectedHorizon: Boolean,
    futuresPerpAvailable: Boolean,
    isEngineOnly: Boolean,
    onSelectHorizon: (TradingHorizon) -> Unit,
    onArmAlert: (TradingHorizon) -> Unit,
    onPaperFollow: (TradingHorizon) -> Unit,
    onSwitchToFuturesShort: () -> Unit,
    onShareFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activePlan = verdict.horizonPlans[selectedHorizon] ?: verdict.bestPlan

    var nowMs by remember(verdict.computedAtMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(verdict.computedAtMs) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val accentColor = when (verdict.weightedBiasSide) {
        PositionSide.LONG -> BullishGreen
        PositionSide.SHORT -> BearishRed
        null -> RiskAmber
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.2.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("master_verdict_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = verdict.headline,
                    color = accentColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("master_verdict_headline")
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Scenario odds (not a guarantee) • ${verdict.plainBestPlanNumbers}",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                Text(
                    text = verdict.plainInvalidation,
                    color = TextMuted,
                    fontSize = 9.sp,
                    lineHeight = 13.sp
                )
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val grade = activePlan?.grade ?: SetupGrade.C_WATCH
                val gCol = when (grade) {
                    SetupGrade.A -> BullishGreen
                    SetupGrade.B -> TechCyan
                    SetupGrade.C_WATCH -> RiskAmber
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(gCol.copy(alpha = 0.18f))
                        .border(1.dp, gCol.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = grade.badgeText,
                        color = gCol,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkBorder, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isEngineOnly) "Engine-only" else "Engine + ${verdict.aiShiftModelName ?: "AI"}",
                        color = if (isEngineOnly) TextMuted else AgentPurple,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        verdict.standardModeNote?.takeIf { it.isNotBlank() }?.let { note ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(TechCyan.copy(alpha = 0.10f))
                    .border(1.dp, TechCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = TechCyan, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = note, color = TechCyan, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Clickable Horizon Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TradingHorizon.values().forEach { horizon ->
                val isSelected = horizon == selectedHorizon
                val hPlan = verdict.horizonPlans[horizon]
                val hColor = when {
                    horizon == TradingHorizon.OUTLOOK -> AgentPurple
                    hPlan?.state == SignalVerdictState.TRADE && hPlan.side == PositionSide.LONG -> BullishGreen
                    hPlan?.state == SignalVerdictState.TRADE && hPlan.side == PositionSide.SHORT -> BearishRed
                    hPlan?.state == SignalVerdictState.WAIT -> RiskAmber
                    else -> TextMuted
                }
                val setupLabel = hPlan?.setupTf?.label ?: horizon.defaultSetupTf.label
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) hColor.copy(alpha = 0.22f) else DarkSurfaceElevated)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) hColor else DarkBorder,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectHorizon(horizon) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("horizon_chip_${horizon.name.lowercase(Locale.US)}"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = horizon.label,
                            color = if (isSelected) hColor else TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = if (horizon == TradingHorizon.OUTLOOK) "1M/3M/1Y" else "$setupLabel • ${hPlan?.side?.name ?: "WAIT"}",
                            color = if (isSelected) hColor else TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (selectedHorizon == TradingHorizon.OUTLOOK) {
            OutlookCard(outlook = verdict.outlook)
        } else if (activePlan != null) {
            HorizonActivePlanPanel(
                verdict = verdict,
                plan = activePlan,
                nowMs = nowMs,
                futuresPerpAvailable = futuresPerpAvailable,
                onSwitchToFuturesShort = onSwitchToFuturesShort
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceElevated)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "What flips bias: ${verdict.whatFlipsBias}",
                color = TextSecondary,
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
            val ageSec = (nowMs - verdict.computedAtMs).coerceAtLeast(0L) / 1000L
            val ageLabel = when {
                ageSec < 60 -> "${ageSec}s ago"
                ageSec < 3600 -> "${ageSec / 60}m ago"
                else -> "${ageSec / 3600}h ago"
            }
            val dataAgeSec = ((activePlan?.dataAgeMs ?: 0L) / 1000L).coerceAtLeast(0L)
            val dataAgeLabel = if (dataAgeSec >= 3600) "${dataAgeSec / 3600}h" else if (dataAgeSec >= 60) "${dataAgeSec / 60}m" else "${dataAgeSec}s"
            val staleBadge = if (verdict.isStale || activePlan?.isStale == true) "STALE DATA" else "LIVE DATA"
            Text(
                text = "Computed $ageLabel • Candle age $dataAgeLabel • $staleBadge • Seed ${verdict.seed}",
                color = if (verdict.isStale) RiskAmber else TextMuted,
                fontSize = 8.sp
            )
        }

        if (selectedHorizon != TradingHorizon.OUTLOOK && activePlan != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onArmAlert(selectedHorizon) },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_master_arm_alert")
                ) {
                    Icon(
                        imageVector = if (isArmedForSelectedHorizon) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                        contentDescription = "Arm Alert",
                        tint = if (isArmedForSelectedHorizon) BullishGreen else RiskAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isArmedForSelectedHorizon) "Armed" else "Arm Alert",
                        color = if (isArmedForSelectedHorizon) BullishGreen else RiskAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = { onPaperFollow(selectedHorizon) },
                    enabled = activePlan.state != SignalVerdictState.NO_TRADE && (activePlan.limitPrice ?: 0.0) > 0.0,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_master_paper_follow")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddTask,
                        contentDescription = "Paper Follow",
                        tint = BullishGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Paper Follow",
                        color = BullishGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        val ok = ChartShareHelper.shareMasterVerdictImage(context, verdict)
                        onShareFeedback(if (ok) "Sharing ${verdict.symbol} Master Verdict card" else "Unable to share image")
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(0.8f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_master_share")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Verdict",
                        tint = TechCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Share",
                        color = TechCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizonActivePlanPanel(
    verdict: MasterVerdict,
    plan: HorizonPlan,
    nowMs: Long,
    futuresPerpAvailable: Boolean,
    onSwitchToFuturesShort: () -> Unit
) {
    val remainingMs = (plan.validUntilMs - nowMs).coerceAtLeast(0L)
    val countdownLabel = formatRemainingDuration(remainingMs)
    val pWinFormatted = plan.formatPWinWithWilson()
    val evFormatted = plan.evR?.takeIf { it.isFinite() }?.let { PriceFormatter.formatRMultiple(it) } ?: "-"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${plan.horizon.label.uppercase(Locale.US)} (${plan.biasTf.label}/${plan.setupTf.label}/${plan.triggerTf.label}) • ${plan.entryModel}",
                color = TechCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (remainingMs > 0L) "Valid $countdownLabel" else "Window Expired",
                color = if (remainingMs > 0L) TextMuted else RiskAmber,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }

        if (verdict.marketType == TradingMarketType.SPOT) {
            val spot = plan.spotView
            val spotColor = when (spot.mode) {
                SpotActionMode.BUY -> BullishGreen
                SpotActionMode.SELL_REDUCE_AND_WAIT_BUY -> RiskAmber
                SpotActionMode.NO_SPOT_PLAN -> BearishRed
            }
            Text(
                text = spot.headline,
                color = spotColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            )
            when (spot.mode) {
                SpotActionMode.BUY -> {
                    val buyZoneStr = if (spot.buyZoneMin != null && spot.buyZoneMax != null && spot.buyZoneMin > 0.0 && spot.buyZoneMax > 0.0) {
                        PriceFormatter.formatZoneRange(spot.buyZoneMin, spot.buyZoneMax)
                    } else "-"
                    val doNotBuyStr = safeFmtPrice(spot.doNotBuyAbove)
                    val invalidStr = safeFmtPrice(spot.invalidationPrice)
                    val targetsStr = spot.sellTargets.filter { it.isFinite() && it > 0.0 }
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(" / ") { PriceFormatter.formatCurrency(it) } ?: "-"
                    Text("BUY Zone: $buyZoneStr • Do not buy above $doNotBuyStr", color = TextPrimary, fontSize = 10.sp)
                    Text("SELL Targets: $targetsStr • Invalidation: $invalidStr", color = TextSecondary, fontSize = 9.sp)
                }
                SpotActionMode.SELL_REDUCE_AND_WAIT_BUY -> {
                    val redStr = if (spot.reduceZoneMin != null && spot.reduceZoneMax != null && spot.reduceZoneMin > 0.0 && spot.reduceZoneMax > 0.0) {
                        PriceFormatter.formatZoneRange(spot.reduceZoneMin, spot.reduceZoneMax)
                    } else "-"
                    val nextBuyStr = if (spot.buyZoneMin != null && spot.buyZoneMax != null && spot.buyZoneMin > 0.0 && spot.buyZoneMax > 0.0) {
                        PriceFormatter.formatZoneRange(spot.buyZoneMin, spot.buyZoneMax)
                    } else "-"
                    Text("Bearish in Spot -> SELL / REDUCE Zone: $redStr", color = RiskAmber, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text("Next BUY demand: $nextBuyStr • ${spot.waitBuyWhy ?: plan.needs}", color = TextSecondary, fontSize = 9.sp)
                    if (spot.futuresShortAvailable && futuresPerpAvailable) {
                        OutlinedButton(
                            onClick = onSwitchToFuturesShort,
                            shape = RoundedCornerShape(7.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_switch_futures_short")
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = TechCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SHORT available via Futures (Switch to Futures)",
                                color = TechCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                SpotActionMode.NO_SPOT_PLAN -> {
                    Text(spot.reasonIfNone ?: plan.needs, color = BearishRed, fontSize = 10.sp)
                }
            }
        } else {
            val fut = plan.futuresView
            if (fut != null && fut.limitPrice > 0.0 && fut.stopLoss > 0.0) {
                val sideCol = if (fut.side == PositionSide.LONG) BullishGreen else BearishRed
                val zoneText = if (fut.entryZoneMin > 0.0 && fut.entryZoneMax > 0.0) {
                    PriceFormatter.formatZoneRange(fut.entryZoneMin, fut.entryZoneMax)
                } else "-"
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${plan.state.label} ${fut.side.name} • Zone $zoneText",
                        color = sideCol,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${fut.recommendedLeverage}x (${fut.leverageSourceLabel})",
                        color = TechCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
                val liqStr = safeFmtPrice(fut.liquidationPrice)
                val tp1Lev = fut.tp1UnleveragedPct * fut.recommendedLeverage
                val tp2Lev = fut.tp2UnleveragedPct * fut.recommendedLeverage
                val tp3Lev = fut.tp3UnleveragedPct * fut.recommendedLeverage
                Text(
                    text = "Limit ${safeFmtPrice(fut.limitPrice)} • SL ${safeFmtPrice(fut.stopLoss)} • Liq $liqStr (>=3x SL buffer)",
                    color = TextSecondary,
                    fontSize = 9.sp
                )
                val tp1RrText = if (fut.tp1R.isFinite() && fut.tp1R > 0.0) String.format(Locale.US, "%.1fR", fut.tp1R) else "-"
                Text(
                    text = "TP1 ${safeFmtPrice(fut.tp1)} (${safeFmtPct(fut.tp1UnleveragedPct)} / ${safeFmtPct(tp1Lev)} @${fut.recommendedLeverage}x • $tp1RrText)",
                    color = BullishGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "TP2 ${safeFmtPrice(fut.tp2)} (${safeFmtPct(fut.tp2UnleveragedPct)} / ${safeFmtPct(tp2Lev)}) • TP3 ${safeFmtPrice(fut.tp3)} (${safeFmtPct(fut.tp3UnleveragedPct)} / ${safeFmtPct(tp3Lev)})",
                    color = TextMuted,
                    fontSize = 8.sp
                )
            } else {
                Text(
                    text = "${plan.state.label} • Needs: ${plan.needs}",
                    color = RiskAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "P(win): $pWinFormatted",
                color = TextPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "EV: $evFormatted",
                color = if ((plan.evR ?: 0.0) >= 0.0) BullishGreen else BearishRed,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Text(
            text = "${plan.replayHeadline} (${plan.replayConservativeMode}, n=${plan.replayResolvedN}, Seed ${plan.replaySeed})",
            color = TextMuted,
            fontSize = 8.sp
        )
        if (plan.state != SignalVerdictState.TRADE && plan.needs.isNotBlank()) {
            Text(
                text = "Trigger / Needs: ${plan.needs}",
                color = RiskAmber,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun HorizonLadderTable(
    verdict: MasterVerdict,
    selectedHorizon: TradingHorizon,
    onSelectHorizon: (TradingHorizon) -> Unit,
    onShareFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("horizon_ladder_table"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MULTI-HORIZON TRADE LADDER",
                    color = TechCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Tap a horizon to switch chart setup timeframe & levels",
                    color = TextMuted,
                    fontSize = 9.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                        .clickable {
                            val txt = ChartShareHelper.formatMasterVerdictAsText(verdict)
                            clipboard.setText(AnnotatedString(txt))
                            onShareFeedback("Copied ${verdict.symbol} Horizon Ladder to clipboard")
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("btn_copy_ladder_text"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy as text", tint = TechCyan, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", color = TechCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(TechCyan.copy(alpha = 0.16f))
                        .border(1.dp, TechCyan.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .clickable {
                            val ok = ChartShareHelper.shareMasterVerdictImage(context, verdict)
                            onShareFeedback(if (ok) "Sharing ${verdict.symbol} ladder PNG" else "Failed to share ladder PNG")
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("btn_share_ladder_png"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = "Share PNG", tint = TechCyan, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PNG", color = TechCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkBackground)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("HORIZON", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(84.dp))
                Text("VERDICT", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(98.dp))
                Text("ZONE", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(126.dp))
                Text("STOP", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(74.dp))
                Text("TP1 (R)", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
                Text("P(WIN) • EV", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(142.dp))
                Text("HOLD", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp))
            }

            TradingHorizon.ACTIONABLE.forEach { horizon ->
                val plan = verdict.horizonPlans[horizon]
                val isSelected = horizon == selectedHorizon
                val stCol = when (plan?.state) {
                    SignalVerdictState.TRADE -> if (plan.side == PositionSide.LONG) BullishGreen else BearishRed
                    SignalVerdictState.WAIT -> RiskAmber
                    else -> TextMuted
                }
                val zoneText = plan?.formatZoneOrDash() ?: "-"
                val stopText = plan?.formatStopOrDash() ?: "-"
                val tp1Text = if (plan?.tp1 != null && plan.tp1 > 0.0) {
                    val rr = plan.tp1Rr?.takeIf { it.isFinite() && it > 0.0 }?.let { " (${String.format(Locale.US, "%.1f", it)}R)" } ?: ""
                    "${PriceFormatter.formatCurrency(plan.tp1)}$rr"
                } else "-"
                val evText = plan?.evR?.takeIf { it.isFinite() }?.let { PriceFormatter.formatRMultiple(it) } ?: "-"
                val pWinText = plan?.let { "${it.formatPWinWithWilson()} • $evText" } ?: "-"

                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) TechCyan.copy(alpha = 0.14f) else DarkSurfaceElevated)
                        .border(1.dp, if (isSelected) TechCyan else DarkBorder, RoundedCornerShape(8.dp))
                        .clickable { onSelectHorizon(horizon) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("horizon_row_${horizon.name.lowercase(Locale.US)}"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.width(84.dp)) {
                        Text(horizon.label, color = if (isSelected) TechCyan else TextPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text(plan?.setupTf?.label ?: horizon.defaultSetupTf.label, color = TextMuted, fontSize = 8.sp)
                    }
                    Text(
                        text = if (plan != null) "${plan.state.label} ${plan.side?.name ?: "WAIT"} [${plan.grade.name.take(1)}]" else "-",
                        color = stCol,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp,
                        modifier = Modifier.width(98.dp)
                    )
                    Text(zoneText, color = TextPrimary, fontSize = 9.sp, modifier = Modifier.width(126.dp))
                    Text(stopText, color = BearishRed, fontSize = 9.sp, modifier = Modifier.width(74.dp))
                    Text(tp1Text, color = BullishGreen, fontSize = 9.sp, modifier = Modifier.width(96.dp))
                    Text(pWinText, color = TextSecondary, fontSize = 8.sp, modifier = Modifier.width(142.dp))
                    Text(horizon.holdLabel, color = TextMuted, fontSize = 8.sp, modifier = Modifier.width(110.dp))
                }
            }
        }
    }
}

@Composable
fun AgentMatrixGrid(
    matrix: AgentMatrix,
    modifier: Modifier = Modifier
) {
    var inspectedCell by remember { mutableStateOf<AgentMatrixCell?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("agent_matrix_grid"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "17-AGENT × 7-TIMEFRAME MATRIX",
                    color = AgentPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Tap any cell for numbers, Engine/AI tag, age, and failure details",
                    color = TextMuted,
                    fontSize = 9.sp
                )
            }
            if (matrix.failedCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(BearishRed.copy(alpha = 0.16f))
                        .border(1.dp, BearishRed.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${matrix.failedCount} FAILED",
                        color = BearishRed,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AGENT", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(104.dp))
                matrix.timeframes.forEach { tf ->
                    val con = matrix.consensusByTf[tf]
                    val cCol = when (con?.biasLabel) {
                        "BULL" -> BullishGreen
                        "BEAR" -> BearishRed
                        else -> RiskAmber
                    }
                    Column(
                        modifier = Modifier
                            .width(58.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(DarkSurfaceElevated)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = con?.tfDisplayLabel ?: tf.label,
                            color = TextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (con != null) "${con.biasLabel} ${con.agreementPercent}%" else "-",
                            color = cCol,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            AgentId.values().forEach { agent ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = agent.displayName,
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(104.dp)
                    )
                    matrix.timeframes.forEach { tf ->
                        val cell = matrix.cell(agent, tf)
                        val isFailed = cell == null || cell.status == AgentStatus.FAILED
                        val isUnavail = cell?.status == AgentStatus.UNAVAILABLE
                        val (bgCol, fgCol, sym) = when {
                            isFailed -> Triple(Color(0xFF1F242D), TextMuted, "ERR")
                            isUnavail -> Triple(Color(0xFF181C24), TextMuted, "N/A")
                            cell!!.stance >= 0.2 -> Triple(BullishGreen.copy(alpha = (0.16f + 0.35f * cell.strength.toFloat()).coerceIn(0.16f, 0.55f)), BullishGreen, "+${String.format(Locale.US, "%.1f", cell.stance)}")
                            cell.stance <= -0.2 -> Triple(BearishRed.copy(alpha = (0.16f + 0.35f * cell.strength.toFloat()).coerceIn(0.16f, 0.55f)), BearishRed, String.format(Locale.US, "%.1f", cell.stance))
                            else -> Triple(RiskAmber.copy(alpha = 0.14f), RiskAmber, "0.0")
                        }
                        Box(
                            modifier = Modifier
                                .width(58.dp)
                                .heightIn(min = 34.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(bgCol)
                                .border(0.8.dp, fgCol.copy(alpha = if (isFailed) 0.25f else 0.45f), RoundedCornerShape(5.dp))
                                .clickable(enabled = cell != null) { inspectedCell = cell }
                                .testTag("matrix_cell_${agent.name.lowercase(Locale.US)}_${tf.name.lowercase(Locale.US)}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sym,
                                color = fgCol,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (matrix.whatWouldChangeThis.isNotEmpty()) {
            Text(
                text = "What would change matrix: ${matrix.whatWouldChangeThis.joinToString(" • ")}",
                color = TextMuted,
                fontSize = 8.sp,
                lineHeight = 12.sp
            )
        }
    }

    inspectedCell?.let { cell ->
        AlertDialog(
            onDismissRequest = { inspectedCell = null },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${cell.agentId.displayName} (${cell.timeframe.label})",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AgentPurple.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = cell.sourceTag.label,
                            color = AgentPurple,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.testTag("agent_cell_detail_dialog"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val ageSec = (cell.freshnessMs / 1000L).coerceAtLeast(0L)
                    val ageText = if (ageSec >= 3600) "${ageSec / 3600}h" else if (ageSec >= 60) "${ageSec / 60}m" else "${ageSec}s"
                    val stanceDesc = if (cell.stance > 0.15) "BULLISH (+${String.format(Locale.US, "%.2f", cell.stance)})"
                    else if (cell.stance < -0.15) "BEARISH (${String.format(Locale.US, "%.2f", cell.stance)})"
                    else "NEUTRAL (${String.format(Locale.US, "%.2f", cell.stance)})"

                    Text(
                        text = "Status: ${cell.status.name} • Stance: $stanceDesc • Strength: ${(cell.strength * 100).toInt()}%",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Data Age: $ageText • Source: ${cell.sourceTag.label}",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    cell.errorMessage?.takeIf { it.isNotBlank() }?.let { err ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(BearishRed.copy(alpha = 0.14f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = BearishRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Error: $err", color = BearishRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (cell.vetoes.isNotEmpty()) {
                        Text("Vetoes:", color = BearishRed, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        cell.vetoes.forEach { v ->
                            Text("• $v", color = BearishRed, fontSize = 10.sp)
                        }
                    }
                    if (cell.reasons.isNotEmpty()) {
                        Text("Supporting Numbers & Reasons:", color = TechCyan, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        cell.reasons.forEach { r ->
                            Text("• $r", color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
                        }
                    }
                    if (matrix.whatWouldChangeThis.isNotEmpty()) {
                        Text(
                            text = "What would change this: ${matrix.whatWouldChangeThis.first()}",
                            color = RiskAmber,
                            fontSize = 9.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedCell = null }) {
                    Text("Close", color = TechCyan, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun OutlookCard(
    outlook: OutlookSummary,
    modifier: Modifier = Modifier
) {
    val biasColor = when (outlook.trendBias) {
        "BULL" -> BullishGreen
        "BEAR" -> BearishRed
        else -> RiskAmber
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, AgentPurple.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("outlook_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LONG-TERM OUTLOOK (${outlook.timeframesCovered.joinToString(" / ")})",
                color = AgentPurple,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "${outlook.trendBias} (${(outlook.biasStrength * 100).toInt()}%)",
                color = biasColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Text(text = outlook.summaryText, color = TextPrimary, fontSize = 10.sp, lineHeight = 14.sp)
        val supStr = outlook.keySupports.filter { it.isFinite() && it > 0.0 }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" • ") { PriceFormatter.formatCurrency(it) } ?: "-"
        val resStr = outlook.keyResistances.filter { it.isFinite() && it > 0.0 }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" • ") { PriceFormatter.formatCurrency(it) } ?: "-"
        Text("Key Supports: $supStr", color = BullishGreen, fontSize = 9.sp)
        Text("Key Resistances: $resStr", color = BearishRed, fontSize = 9.sp)

        if (outlook.accumulationZones.isNotEmpty()) {
            val accText = outlook.accumulationZones.filter { it.minPrice > 0.0 && it.maxPrice > 0.0 }
                .joinToString(", ") { PriceFormatter.formatZoneRange(it.minPrice, it.maxPrice) }
                .ifBlank { "-" }
            Text("Accumulation Zones: $accText", color = BullishGreen.copy(alpha = 0.9f), fontSize = 9.sp)
        }
        if (outlook.distributionZones.isNotEmpty()) {
            val distText = outlook.distributionZones.filter { it.minPrice > 0.0 && it.maxPrice > 0.0 }
                .joinToString(", ") { PriceFormatter.formatZoneRange(it.minPrice, it.maxPrice) }
                .ifBlank { "-" }
            Text("Distribution Zones: $distText", color = BearishRed.copy(alpha = 0.9f), fontSize = 9.sp)
        }
        Text(text = outlook.note, color = TextMuted, fontSize = 8.sp)
    }
}

private fun formatRemainingDuration(ms: Long): String {
    if (ms <= 0L) return "0m"
    val mins = ms / 60_000L
    val hours = mins / 60L
    val days = hours / 24L
    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${mins % 60}m"
        else -> "${mins}m"
    }
}
