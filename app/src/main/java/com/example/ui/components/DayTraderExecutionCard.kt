package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.daytrader.alert.DayTraderAlertItem
import com.example.domain.daytrader.plan.DayTraderPlan
import com.example.domain.daytrader.risk.RiskEvaluationResult
import com.example.domain.daytrader.risk.RiskSettings
import com.example.domain.daytrader.timer.EntryTimerState
import com.example.domain.model.PositionSide
import com.example.domain.model.SignalVerdictState
import com.example.domain.util.PriceFormatter
import com.example.ui.MainViewModel
import com.example.ui.UiState
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

@Composable
fun DayTraderAlertBanner(
    alerts: List<DayTraderAlertItem>,
    onDismissAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latest = alerts.lastOrNull() ?: return
    val bannerColor = if (latest.isCritical) BearishRed else RiskAmber

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bannerColor.copy(alpha = 0.16f))
            .border(1.dp, bannerColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("day_trader_alert_banner")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Day-Trader Alert",
                    tint = bannerColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "[${latest.symbol}] ${latest.headline}",
                        color = bannerColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = latest.details,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(
                onClick = onDismissAll,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss alert", tint = TextMuted, modifier = Modifier.size(15.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayTraderExecutionSection(
    uiState: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val plan = uiState.dayTraderPlan
    val timer = uiState.entryTimerStatus
    val risk = uiState.riskEvaluation
    val eventRisk = uiState.eventRiskAssessment
    val verdict = uiState.verdictState?.verdict ?: uiState.tradeSetupPlan?.verdictState ?: SignalVerdictState.NO_TRADE
    val hasValidPlan = (uiState.verdictState?.hasValidPlan == true) && plan != null

    var showLogRealDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Entry Timer & Exit Planner Flat-By Status Card (hidden when plan == null or NO_TRADE)
        if (hasValidPlan && (plan != null || timer != null)) {
            val timerColor = when (timer?.state) {
                EntryTimerState.TRIGGERED -> BullishGreen
                EntryTimerState.EXPIRED -> BearishRed
                else -> RiskAmber
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, timerColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .testTag("entry_timer_card")
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AvTimer,
                            contentDescription = "Entry Timer",
                            tint = timerColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ENTRY TIMER & EXIT PLANNER",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(timerColor.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = timer?.state?.name ?: "WAITING",
                            color = timerColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                timer?.let { t ->
                    Text(
                        text = t.displayBanner,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    if (!t.isBarStillFormingNotice.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• ${t.isBarStillFormingNotice} (closed-bar rule enforced)",
                            color = TechCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                plan?.let { p ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Stop Rule: ${p.stopRule.label}",
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                        Text(
                            text = p.flatByDescription,
                            color = TechCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    p.validationError?.let { err ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(BearishRed.copy(alpha = 0.14f))
                                .border(1.dp, BearishRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = BearishRed, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = err,
                                color = BearishRed,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // 2. Symbol-Scoped News Event Risk & Tagged Headlines
        if (eventRisk != null || uiState.taggedHeadlines.isNotEmpty()) {
            val evColor = when {
                eventRisk?.hasNewsShock == true -> BearishRed
                eventRisk?.isEarningsWithin5Days == true -> RiskAmber
                else -> TechCyan
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, evColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .testTag("event_risk_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EVENT RISK & HEADLINE TAGGING",
                        color = evColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (eventRisk?.hasNewsShock == true) "NEWS SHOCK" else "NORMAL",
                        color = evColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                eventRisk?.let { ev ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ev.summaryText,
                        color = TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
                if (uiState.taggedHeadlines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.taggedHeadlines.take(5).forEach { item ->
                            val tagCol = Color(item.tag.badgeColorHex)
                            Box(
                                modifier = Modifier
                                    .width(210.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, tagCol.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(7.dp)
                            ) {
                                Column {
                                    Text(
                                        text = item.tag.label.uppercase(Locale.US),
                                        color = tagCol,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.headline,
                                        color = TextPrimary,
                                        fontSize = 9.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No relevant news for ${uiState.selectedSymbol}.",
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }
            }
        }

        // 3. Risk Budget, Sizer & Copyable Order Ticket (hidden when plan == null or NO_TRADE)
        if (hasValidPlan && risk != null) {
            DayTraderRiskSizerAndActionsCard(
                verdict = verdict,
                plan = plan,
                risk = risk,
                riskSettings = uiState.riskSettings,
                onSaveRiskSettings = { acct, pct -> viewModel.configureRiskSettings(acct, pct) },
                onAcceptHigherRisk = { viewModel.acceptHigherRiskForCurrentTrade() },
                onLogPaperFill = { viewModel.logPaperTradeFromPlan() },
                onTookThisTrade = { showLogRealDialog = true }
            )
        }

        // 3b. SEC EDGAR 8-Quarter Fundamentals (US Stocks & Equity Perps)
        uiState.stockFundamentals?.let { stockFund ->
            StockFundamentalsCard(stockFund)
        }

        // 4. Replay Agent ("Time Machine") Card (hidden when plan == null; hypothetical preview lives inside NoPlanCard)
        if (hasValidPlan) {
            if (uiState.isRunningReplay) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, TechCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TechCyan, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Time Machine: Running 10,000 seeded 5m bootstrap replays (Seed ${uiState.replaySeed})...",
                        color = TechCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (uiState.replayResult != null) {
                Column {
                    ReplayAgentCard(
                        replayResult = uiState.replayResult,
                        entryPrice = plan?.entryPrice ?: 0.0,
                        stopLossPrice = plan?.stopPrice ?: 0.0,
                        targetPrice = plan?.targetPrice ?: 0.0
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { viewModel.runReplayForCurrentPlan(uiState.replaySeed) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = TechCyan, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-run Replay (Seed ${uiState.replaySeed})", color = TechCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showLogRealDialog && plan != null && verdict == SignalVerdictState.TRADE && risk?.isAllowed == true) {
        LogRealTradeDialog(
            defaultSymbol = plan.symbol,
            defaultSide = plan.side,
            defaultEntry = plan.entryPrice,
            defaultStop = plan.stopPrice,
            defaultExit = uiState.currentQuote?.currentPrice ?: plan.targetPrice,
            defaultModel = uiState.tradeSetupPlan?.entryModelName ?: "A_TREND_PULLBACK",
            onDismiss = { showLogRealDialog = false },
            onConfirm = { sym, side, entry, sl, exit, model ->
                viewModel.logRealTrade(sym, side, entry, sl, exit, model)
                showLogRealDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayTraderRiskSizerAndActionsCard(
    verdict: SignalVerdictState,
    plan: DayTraderPlan?,
    risk: RiskEvaluationResult,
    riskSettings: RiskSettings,
    onSaveRiskSettings: (accountBalance: Double, riskPercent: Double) -> Unit,
    onLogPaperFill: () -> Unit,
    onTookThisTrade: () -> Unit,
    onAcceptHigherRisk: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    var showGatesDetail by remember { mutableStateOf(false) }
    var showBudgetEditor by remember(riskSettings.isConfigured) { mutableStateOf(!riskSettings.isConfigured) }
    var copiedFeedback by remember { mutableStateOf(false) }

    var accountInput by remember(riskSettings.accountBalance) {
        mutableStateOf(if (riskSettings.accountBalance > 0.0) riskSettings.accountBalance.toInt().toString() else "")
    }
    var riskPctInput by remember(riskSettings.riskPercent) {
        mutableStateOf(if (riskSettings.riskPercent > 0.0) String.format(Locale.US, "%.1f", riskSettings.riskPercent) else "1.0")
    }

    val riskColor = if (risk.isAllowed) BullishGreen else if (risk.isMinNotionalExceeded || risk.isAccountUnconfigured) RiskAmber else BearishRed
    val canExecuteTrade = verdict == SignalVerdictState.TRADE &&
        risk.isAllowed &&
        plan != null &&
        plan.entryPrice > 0.0 &&
        plan.stopPrice > 0.0
    val canLogPaperFill = verdict != SignalVerdictState.NO_TRADE &&
        plan != null &&
        plan.entryPrice > 0.0 &&
        plan.stopPrice > 0.0 &&
        risk.paperFollowAllowed

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, riskColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("risk_budget_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = "Risk Budget", tint = riskColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RISK SIZER & ORDER TICKET",
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (showBudgetEditor) "Hide Budget" else "Edit Budget",
                    color = TechCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { showBudgetEditor = !showBudgetEditor }
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(riskColor.copy(alpha = 0.18f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (risk.isAllowed) "${risk.checks.size} GATES PASS" else "BLOCKED",
                        color = riskColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        AnimatedVisibility(visible = showBudgetEditor) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(1.dp, TechCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
                    .testTag("risk_budget_setup_prompt"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (!riskSettings.isConfigured) {
                        "FIRST-RUN RISK SETUP REQUIRED — Enter your account size and risk % per trade:"
                    } else {
                        "Update Account Size & Risk % Per Trade:"
                    },
                    color = RiskAmber,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = accountInput,
                        onValueChange = { accountInput = it },
                        label = { Text("Account Size ($)", fontSize = 9.sp) },
                        placeholder = { Text("e.g. 5000", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("input_account_balance"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    OutlinedTextField(
                        value = riskPctInput,
                        onValueChange = { riskPctInput = it },
                        label = { Text("Risk %", fontSize = 9.sp) },
                        placeholder = { Text("1.0", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.7f)
                            .testTag("input_risk_percent"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                }
                Button(
                    onClick = {
                        val acct = accountInput.toDoubleOrNull() ?: 0.0
                        val pct = riskPctInput.toDoubleOrNull() ?: 0.0
                        if (acct > 0.0 && pct > 0.0) {
                            onSaveRiskSettings(acct, pct)
                            showBudgetEditor = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("btn_save_risk_budget")
                ) {
                    Text("Save Risk Budget", color = DarkBackground, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = risk.statusBanner,
            color = riskColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (risk.isMinNotionalExceeded && !risk.isAllowed) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RiskAmber.copy(alpha = 0.14f))
                    .border(1.dp, RiskAmber.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = risk.blockedReasons.firstOrNull { it.contains("Minimum order", true) } ?: risk.statusBanner,
                    color = RiskAmber,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 13.sp
                )
                OutlinedButton(
                    onClick = onAcceptHigherRisk,
                    border = androidx.compose.foundation.BorderStroke(1.dp, RiskAmber),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RiskAmber),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("btn_accept_higher_risk")
                ) {
                    Text("Accept higher risk (this trade only)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Sized Qty", color = TextMuted, fontSize = 9.sp)
                val qtyStr = PriceFormatter.formatQuantity(risk.sizedQuantity)
                Text(
                    text = if (qtyStr == "-") "-" else "$qtyStr ${risk.unitLabel}",
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Max Risk / Trade", color = TextMuted, fontSize = 9.sp)
                Text(
                    text = if (riskSettings.isConfigured) PriceFormatter.formatCash(riskSettings.maxRiskPerTradeDollars) else "Not Set",
                    color = TechCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Net R:R", color = TextMuted, fontSize = 9.sp)
                Text(
                    text = PriceFormatter.formatRatio(plan?.netRewardRisk),
                    color = if ((plan?.netRewardRisk ?: 0.0) >= riskSettings.minNetRewardRisk) BullishGreen else RiskAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Expandable Risk Gates Breakdown
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showGatesDetail = !showGatesDetail }
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Inspect ${risk.checks.size} Risk Budget Gates (${risk.checks.count { it.isPassed }} PASS / ${risk.checks.count { !it.isPassed }} BLOCK)",
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = if (showGatesDetail) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }

        AnimatedVisibility(visible = showGatesDetail) {
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                risk.checks.forEach { chk ->
                    val cCol = if (chk.isPassed) BullishGreen else BearishRed
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (chk.isPassed) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = cCol,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${chk.title}: ${chk.detail}",
                            color = if (chk.isPassed) TextSecondary else BearishRed,
                            fontSize = 9.sp,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }

        // Action Row: Copy Order Ticket + Paper Ledger / Real Trade logging (strictly gated)
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            risk.orderTicket?.let { ticket ->
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(ticket.formattedClipboardTicket))
                        copiedFeedback = true
                    },
                    enabled = canExecuteTrade,
                    colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("copy_order_ticket_btn")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (copiedFeedback) "Ticket Copied" else "Copy Order Ticket",
                        color = DarkBackground,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedButton(
                onClick = onLogPaperFill,
                enabled = canLogPaperFill,
                border = androidx.compose.foundation.BorderStroke(1.dp, (if (canLogPaperFill) AgentPurple else TextMuted).copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AgentPurple,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("btn_log_paper_fill")
            ) {
                Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Log Paper Fill", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onTookThisTrade,
                enabled = canExecuteTrade,
                border = androidx.compose.foundation.BorderStroke(1.dp, (if (canExecuteTrade) BullishGreen else TextMuted).copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = BullishGreen,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.testTag("btn_took_this_trade")
            ) {
                Text("I Took This Trade", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StockFundamentalsCard(
    result: com.example.domain.daytrader.fundamentals.StockFundamentalsResult,
    modifier: Modifier = Modifier
) {
    val facts = result.facts
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, TechCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("stock_fundamentals_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SEC EDGAR FUNDAMENTALS (8 QUARTERS)",
                color = TechCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            if (facts != null && facts.cik.isNotBlank()) {
                Text(
                    text = "CIK ${facts.cik}",
                    color = TextMuted,
                    fontSize = 9.sp
                )
            }
        }

        if (facts == null || facts.quarters.isEmpty()) {
            Text(
                text = result.unavailableReason ?: "N/A (Quarterly XBRL facts unavailable)",
                color = TextMuted,
                fontSize = 10.sp
            )
        } else {
            facts.whatChangedNumbersOnly.forEach { line ->
                Text(
                    text = "• $line",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                facts.quarters.take(8).forEach { q ->
                    Column(
                        modifier = Modifier
                            .width(126.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                            .padding(7.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(q.quarterLabel, color = TechCyan, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        val revStr = q.revenueUsd?.let { PriceFormatter.formatCompactUsd(it) } ?: "-"
                        val epsStr = q.epsUsd?.let { String.format(Locale.US, "$%.2f", it) } ?: "-"
                        val gmStr = q.grossMarginPercent?.let { PriceFormatter.formatPercent(it, 1) } ?: "-"
                        val yoyStr = q.yoyRevenueGrowthPercent?.let { PriceFormatter.formatSignedPercent(it, 1) } ?: "-"
                        Text("Rev: $revStr", color = TextPrimary, fontSize = 9.sp)
                        Text("EPS: $epsStr • GM: $gmStr", color = TextSecondary, fontSize = 8.sp)
                        Text(
                            text = "YoY: $yoyStr",
                            color = if ((q.yoyRevenueGrowthPercent ?: 0.0) >= 0.0) BullishGreen else BearishRed,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogRealTradeDialog(
    defaultSymbol: String,
    defaultSide: PositionSide,
    defaultEntry: Double,
    defaultStop: Double,
    defaultExit: Double,
    defaultModel: String,
    onDismiss: () -> Unit,
    onConfirm: (symbol: String, side: PositionSide, entry: Double, stop: Double, exit: Double, model: String) -> Unit
) {
    var symbolText by remember { mutableStateOf(defaultSymbol) }
    var side by remember { mutableStateOf(defaultSide) }
    var entryText by remember { mutableStateOf(if (defaultEntry > 0.0) PriceFormatter.formatPrice(defaultEntry).replace(",", "") else "") }
    var stopText by remember { mutableStateOf(if (defaultStop > 0.0) PriceFormatter.formatPrice(defaultStop).replace(",", "") else "") }
    var exitText by remember { mutableStateOf(if (defaultExit > 0.0) PriceFormatter.formatPrice(defaultExit).replace(",", "") else "") }
    var modelText by remember { mutableStateOf(defaultModel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("I Took This Trade (Log Real Outcome)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbolText,
                    onValueChange = { symbolText = it.uppercase() },
                    label = { Text("Symbol", fontSize = 10.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PositionSide.LONG, PositionSide.SHORT).forEach { s ->
                        val active = side == s
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) (if (s == PositionSide.LONG) BullishGreen else BearishRed).copy(alpha = 0.25f) else DarkSurfaceElevated)
                                .border(1.dp, if (active) TechCyan else DarkBorder, RoundedCornerShape(6.dp))
                                .clickable { side = s }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(s.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
                OutlinedTextField(
                    value = entryText,
                    onValueChange = { entryText = it },
                    label = { Text("Fill Price", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                OutlinedTextField(
                    value = stopText,
                    onValueChange = { stopText = it },
                    label = { Text("Stop Loss", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                OutlinedTextField(
                    value = exitText,
                    onValueChange = { exitText = it },
                    label = { Text("Exit Price", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = { Text("Entry Model", fontSize = 10.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val e = entryText.toDoubleOrNull() ?: defaultEntry
                    val s = stopText.toDoubleOrNull() ?: defaultStop
                    val x = exitText.toDoubleOrNull() ?: defaultExit
                    onConfirm(symbolText, side, e, s, x, modelText)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BullishGreen)
            ) {
                Text("Save to Ledger", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = DarkSurface
    )
}
