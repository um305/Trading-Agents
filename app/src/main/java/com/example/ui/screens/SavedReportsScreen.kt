package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.TradeOutcomeEntity
import com.example.domain.daytrader.ledger.LedgerGroupStats
import com.example.domain.daytrader.ledger.PaperLedgerSummary
import com.example.domain.model.PositionSide
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.LogRealTradeDialog
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedReportsScreen(
    uiState: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val reports = uiState.savedReports
    val outcomes = uiState.tradeOutcomes
    var selectedTab by remember { mutableStateOf(0) } // 0 = Research Reports, 1 = Paper Ledger
    var showLogRealDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AgentPurple.copy(alpha = 0.2f))
                    .border(1.dp, AgentPurple, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selectedTab == 0) Icons.Default.Bookmark else Icons.Default.ReceiptLong,
                    contentDescription = "Saved Research & Paper Ledger",
                    tint = AgentPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (selectedTab == 0) "Saved Research & Theses" else "Paper & Real Trade Ledger",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = if (selectedTab == 0) "Persisted locally via Android Room (${reports.size} saved)" else "Virtual Paper Ledger & Self-Calibrating Expectancy (${outcomes.size} trades)",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Two-tab pill bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tab0Active = selectedTab == 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (tab0Active) AgentPurple.copy(alpha = 0.22f) else DarkSurface)
                    .clickable { selectedTab = 0 }
                    .padding(vertical = 8.dp)
                    .testTag("tab_research_reports"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Research Reports (${reports.size})",
                    color = if (tab0Active) AgentPurple else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            val tab1Active = selectedTab == 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (tab1Active) TechCyan.copy(alpha = 0.22f) else DarkSurface)
                    .clickable { selectedTab = 1 }
                    .padding(vertical = 8.dp)
                    .testTag("tab_paper_ledger"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Paper Ledger (${outcomes.size})",
                    color = if (tab1Active) TechCyan else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
            if (reports.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No saved research reports yet",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap 'Save Report' on any multi-agent audit in the Terminal",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(reports, key = { it.id }) { report ->
                        val isPositive = report.action.contains("BUY")
                        val actionColor = if (isPositive) BullishGreen else BearishRed
                        val dateFormatted = remember(report.timestamp) {
                            val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US)
                            sdf.format(Date(report.timestamp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(DarkSurface)
                                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.openSavedReport(report)
                                    viewModel.setScreen(AppScreen.TERMINAL)
                                }
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = report.symbol,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(actionColor.copy(alpha = 0.2f))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = report.action,
                                                color = actionColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteSavedReport(report.id) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("delete_report_${report.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete Report",
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = report.companyName,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Price: $${String.format(Locale.US, "%.2f", report.price)}",
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Target: $${String.format(Locale.US, "%.2f", report.targetPrice)}",
                                        color = BullishGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Stop: $${String.format(Locale.US, "%.2f", report.stopLossPrice)}",
                                        color = BearishRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Conf: ${report.confidenceScore}%",
                                        color = AgentPurple,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = report.executiveSummary,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Saved: $dateFormatted",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            PaperLedgerTabContent(
                summary = uiState.paperLedgerSummary,
                outcomes = outcomes,
                onLogRealTrade = { showLogRealDialog = true },
                onClearLedger = { viewModel.clearPaperLedger() }
            )
        }
    }

    if (showLogRealDialog) {
        val dt = uiState.dayTraderPlan
        val quote = uiState.currentQuote
        val defEntry = dt?.entryPrice ?: quote?.currentPrice ?: 100.0
        val defStop = dt?.stopPrice ?: (defEntry * 0.98)
        val defExit = quote?.currentPrice ?: dt?.targetPrice ?: (defEntry * 1.04)
        LogRealTradeDialog(
            defaultSymbol = dt?.symbol ?: uiState.selectedSymbol,
            defaultSide = dt?.side ?: PositionSide.LONG,
            defaultEntry = defEntry,
            defaultStop = defStop,
            defaultExit = defExit,
            defaultModel = uiState.tradeSetupPlan?.entryModelName ?: "A_TREND_PULLBACK",
            onDismiss = { showLogRealDialog = false },
            onConfirm = { sym, side, entry, sl, exit, model ->
                viewModel.logRealTrade(sym, side, entry, sl, exit, model)
                showLogRealDialog = false
            }
        )
    }
}

@Composable
private fun PaperLedgerTabContent(
    summary: PaperLedgerSummary?,
    outcomes: List<TradeOutcomeEntity>,
    onLogRealTrade: () -> Unit,
    onClearLedger: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, TechCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag("paper_ledger_summary_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LEDGER PERFORMANCE METRICS",
                        color = TechCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (summary != null) "${summary.realTradesCount} Real / ${summary.paperTradesCount} Paper" else "0 Trades",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val expR = summary?.overallExpectancyR ?: 0.0
                val totR = summary?.overallRealizedR ?: 0.0
                val winRate = summary?.overallWinRatePercent ?: 0.0
                val maxDd = summary?.maxDrawdownR ?: 0.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Win Rate", color = TextMuted, fontSize = 9.sp)
                        Text(
                            text = "${String.format(Locale.US, "%.1f", winRate)}%",
                            color = if (winRate >= 50.0) BullishGreen else TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Expectancy", color = TextMuted, fontSize = 9.sp)
                        Text(
                            text = "${if (expR >= 0) "+" else ""}${String.format(Locale.US, "%.2f", expR)}R",
                            color = if (expR >= 0) BullishGreen else BearishRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total R", color = TextMuted, fontSize = 9.sp)
                        Text(
                            text = "${if (totR >= 0) "+" else ""}${String.format(Locale.US, "%.2f", totR)}R",
                            color = if (totR >= 0) BullishGreen else BearishRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Max DD", color = TextMuted, fontSize = 9.sp)
                        Text(
                            text = "-${String.format(Locale.US, "%.2f", maxDd)}R",
                            color = BearishRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onLogRealTrade,
                        colors = ButtonDefaults.buttonColors(containerColor = BullishGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ledger_log_real_btn")
                    ) {
                        Icon(Icons.Default.AddTask, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("I Took This Trade", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onClearLedger,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BearishRed.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BearishRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("ledger_clear_btn")
                    ) {
                        Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (summary != null && summary.totalTrades > 0) {
            if (summary.statsByEntryModel.isNotEmpty()) {
                item {
                    LedgerBreakdownSection(title = "BY ENTRY MODEL", groups = summary.statsByEntryModel)
                }
            }
            if (summary.statsBySession.isNotEmpty()) {
                item {
                    LedgerBreakdownSection(title = "BY SESSION", groups = summary.statsBySession)
                }
            }
            if (summary.statsByAssetType.isNotEmpty()) {
                item {
                    LedgerBreakdownSection(title = "BY ASSET TYPE", groups = summary.statsByAssetType)
                }
            }
        }

        if (outcomes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No paper or real trades logged yet. Log a fill from the Terminal Execution card or tap 'I Took This Trade'.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            items(outcomes, key = { it.id }) { item ->
                val rColor = if (item.realizedR > 0.0) BullishGreen else if (item.realizedR < 0.0) BearishRed else RiskAmber
                val badgeColor = if (item.isRealTrade) BullishGreen else AgentPurple
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${item.symbol} • ${item.side}",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeColor.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (item.isRealTrade) "REAL" else "PAPER",
                                    color = badgeColor,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Text(
                            text = "${if (item.realizedR >= 0) "+" else ""}${String.format(Locale.US, "%.2f", item.realizedR)}R",
                            color = rColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Model: ${item.entryModel}  •  Session: ${item.sessionName}  •  ${item.exitReason.ifBlank { item.status }}",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerBreakdownSection(
    title: String,
    groups: List<LedgerGroupStats>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(text = title, color = AgentPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        groups.forEach { g ->
            val expCol = if (g.expectancyR >= 0.0) BullishGreen else BearishRed
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${g.groupLabel} (n=${g.totalTrades})",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Win ${String.format(Locale.US, "%.0f", g.winRatePercent)}% • ${if (g.expectancyR >= 0) "+" else ""}${String.format(Locale.US, "%.2f", g.expectancyR)}R",
                    color = expCol,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
