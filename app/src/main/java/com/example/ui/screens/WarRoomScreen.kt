@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.BackgroundWorkloadBalancerMonitorCard
import com.example.ui.components.IngestionStreamsAccordionCard
import com.example.ui.components.ManagerDecisionCard
import com.example.ui.components.ResearcherTeamCard
import com.example.ui.components.RiskTeamCard
import com.example.ui.components.TraderProposalCard
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
fun WarRoomScreen(
    uiState: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var queryInput by remember { mutableStateOf("") }
    val synthesis = uiState.agentSynthesis
    val quote = uiState.currentQuote
    val isWatchlisted = uiState.watchlist.any { it.symbol == uiState.selectedSymbol }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AgentPurple.copy(alpha = 0.2f))
                        .border(1.dp, AgentPurple, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Unified War Room",
                        tint = AgentPurple,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Unified Multi-Node Consensus",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "All Agents & Fleet Nodes Scanned Into One Final Result",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            IconButton(
                onClick = { viewModel.toggleWatchlist(uiState.selectedSymbol) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = if (isWatchlisted) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (isWatchlisted) RiskAmber else TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // QUERY SCANNER SEARCH BAR
        // Scans the query through every agent or node available in this app and outputs the final result
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = queryInput,
                onValueChange = { queryInput = it },
                placeholder = { Text("Scan Query (e.g. NVDA, BTC, SOL, AAPL)", fontSize = 12.sp, color = TextMuted) },
                singleLine = true,
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Radar, contentDescription = null, tint = AgentPurple, modifier = Modifier.size(18.dp))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (queryInput.isNotBlank()) {
                            viewModel.scanQueryThroughAllAgentsAndNodes(queryInput)
                            queryInput = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AgentPurple,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("warroom_query_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (queryInput.isNotBlank()) {
                        viewModel.scanQueryThroughAllAgentsAndNodes(queryInput)
                        queryInput = ""
                    } else {
                        viewModel.runDeepAgentAnalysis()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AgentPurple),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("scan_all_nodes_button")
            ) {
                if (uiState.isRunningAgents) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan All Nodes", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SCANNED NODES & FLEET INDICATOR BAR
        // Shows all active nodes participating in the query scan
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(BullishGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ACTIVE PARTICIPATING NODES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TechCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Unified Scan Active",
                        fontSize = 10.sp,
                        color = BullishGreen,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val nodes = listOf(
                        Pair("TradingView Studies (34 Indicators)", Color(0xFF2962FF)),
                        Pair("NewsScannerAgent (Symbol-Scoped)", RiskAmber),
                        Pair("Fundamental Valuation", BullishGreen),
                        Pair("Technical Analysis & SMC", TechCyan),
                        Pair("Risk Management", BearishRed),
                        Pair("Chief Strategist", AgentPurple),
                        Pair("AI Fleet & APIs", BullishGreen)
                    )

                    nodes.forEach { (name, color) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(color.copy(alpha = 0.15f))
                                .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .widthIn(min = 40.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 10.sp,
                                color = color,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Live scanning phase indicator
        AnimatedVisibility(visible = uiState.isRunningAgents) {
            Column(modifier = Modifier.padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AgentPurple
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.agentRunningPhase.ifBlank { "Scanning query through all agents and fleet models simultaneously..." },
                        color = AgentPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // THE FINAL UNIFIED RESULT (Consolidated into one single authoritative result)
        // "give the final result not a seperate section for every agent or node"
        if (synthesis != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.5.dp, BullishGreen.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("unified_consensus_final_result_panel")
            ) {
                Column {
                    // Top Verdict Row
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = uiState.selectedSymbol,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(BullishGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .widthIn(min = 40.dp)
                                ) {
                                    Text(
                                        text = "FINAL CONSENSUS RESULT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BullishGreen,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Text(
                                text = quote?.name ?: "Financial Asset",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        // Final Action Badge
                        val actColor = if (synthesis.action.isPositive) BullishGreen else BearishRed
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(actColor.copy(alpha = 0.2f))
                                .border(1.5.dp, actColor, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .widthIn(min = 60.dp)
                        ) {
                            Text(
                                text = synthesis.action.label,
                                color = actColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Consensus Confidence & Price Impulse Gauge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Unified Multi-Node Convergence: ${synthesis.confidenceScore}%",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        val predSign = if (synthesis.predictedPriceChangePercent >= 0) "+" else ""
                        val predColor = if (synthesis.predictedPriceChangePercent >= 0) BullishGreen else BearishRed
                        Text(
                            text = "Projected Shift: $predSign${String.format(Locale.US, "%.1f", synthesis.predictedPriceChangePercent)}%",
                            color = predColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { synthesis.confidenceScore / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AgentPurple,
                        trackColor = DarkSurfaceElevated
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // DEFINITIVE PRICE DECISION GRID
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceElevated)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current Price", fontSize = 10.sp, color = TextMuted)
                            Text(com.example.domain.util.PriceFormatter.formatCurrency(quote?.currentPrice), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Target Price", fontSize = 10.sp, color = TextMuted)
                            Text(com.example.domain.util.PriceFormatter.formatCurrency(synthesis.targetPrice), color = BullishGreen, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Recommended Buy", fontSize = 10.sp, color = TextMuted)
                            Text(com.example.domain.util.PriceFormatter.formatCurrency(synthesis.recommendedBuyPrice), color = TechCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Stop Loss", fontSize = 10.sp, color = TextMuted)
                            Text(com.example.domain.util.PriceFormatter.formatCurrency(synthesis.stopLossPrice), color = BearishRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Column {
                            Text("Risk/Reward", fontSize = 10.sp, color = TextMuted)
                            Text(synthesis.riskRewardRatio.ifBlank { "-" }, color = RiskAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // OPTIMAL ENTRY & ACCUMULATION STRATEGY
                    if (synthesis.optimalBuyZoneLow > 0.0 && synthesis.optimalBuyZoneHigh > 0.0) {
                        val isBull = synthesis.action.isPositive
                        val zoneColor = if (isBull) BullishGreen else BearishRed
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, zoneColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(zoneColor.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "FORWARD-LOOKING SETUP (NOT A PAST TRADE)",
                                        color = zoneColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(zoneColor.copy(alpha = 0.15f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                            .widthIn(min = 60.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "PENDING EXECUTION",
                                            color = zoneColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isBull) "Optimal Long Entry Zone (Upcoming):" else "Optimal Short Entry Zone (Upcoming):",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = com.example.domain.util.PriceFormatter.formatZoneRange(synthesis.optimalBuyZoneLow, synthesis.optimalBuyZoneHigh),
                                        color = zoneColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (synthesis.entryStrategy.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = synthesis.entryStrategy, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // NEWSSCANNERAGENT LIVE HEADLINE & GEMINI SENTIMENT
                    val leadHeadline = synthesis.newsScannerResult?.primaryCatalyst
                        ?: synthesis.bloombergHeadlinesScanned.firstOrNull()
                        ?: "Institutional liquidity tape active for ${uiState.selectedSymbol}"
                    val catalystImpulse = synthesis.newsScannerResult?.priceImpactPercent
                        ?: synthesis.bloombergPriceImpactPercent

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, RiskAmber.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.ElectricBolt, contentDescription = null, tint = RiskAmber, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val newsSourceBadge = synthesis.agentReports.find { it.role.name == "NEWS_SCANNER" }?.sourceBadge ?: "Engine (lexicon)"
                                    Text("NewsScanner • $newsSourceBadge", color = RiskAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                val catSign = if (catalystImpulse >= 0) "+" else ""
                                Text(
                                    text = "$catSign${String.format(Locale.US, "%.1f", catalystImpulse)}% Catalyst Impact",
                                    color = RiskAmber,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "\"$leadHeadline\"",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // MASTER UNIFIED SYNTHESIS (One cohesive paragraph, not separated)
                    Text(
                        text = "Master Consensus Synthesis",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = synthesis.unifiedConsensusSummary.ifBlank { synthesis.executiveSummary },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // UNIFIED TRADE CONFLUENCES (Combined reasons, not divided by agent)
                    Text(
                        text = "Collective Committee Confluences",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val confluences = if (synthesis.unifiedConfluences.isNotEmpty()) {
                        synthesis.unifiedConfluences
                    } else {
                        synthesis.bullArguments.take(3) + synthesis.bearArguments.take(2)
                    }

                    confluences.forEach { point ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = BullishGreen,
                                modifier = Modifier.size(14.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = point,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (synthesis.agentReports.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Specialist Agent Directives",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        synthesis.agentReports.forEach { report ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(report.role.title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (report.sourceBadge.startsWith("AI")) AgentPurple.copy(alpha = 0.2f) else TechCyan.copy(alpha = 0.15f))
                                                .border(0.6.dp, if (report.sourceBadge.startsWith("AI")) AgentPurple else TechCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = report.sourceBadge,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (report.sourceBadge.startsWith("AI")) AgentPurple else TechCyan
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(report.headlineThesis, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Four Ingestion Streams (Market, Social, News, Fundamentals)
                    IngestionStreamsAccordionCard(synthesis = synthesis)

                    // 2. Researcher Team Debate & Evidence
                    synthesis.researcherDossier?.let { dossier ->
                        Spacer(modifier = Modifier.height(12.dp))
                        ResearcherTeamCard(dossier = dossier)
                    }

                    // 3. Trader Proposal (OpenAI o1 Deep Thinking)
                    synthesis.traderProposal?.let { proposal ->
                        Spacer(modifier = Modifier.height(12.dp))
                        TraderProposalCard(proposal = proposal)
                    }

                    // 4. Risk Management Team (Aggressive, Neutral, Conservative)
                    synthesis.riskTeamEvaluation?.let { riskTeam ->
                        Spacer(modifier = Modifier.height(12.dp))
                        RiskTeamCard(team = riskTeam)
                    }

                    // 5. Manager Decision (Authorization & Execution)
                    synthesis.managerDecision?.let { managerDecision ->
                        Spacer(modifier = Modifier.height(12.dp))
                        ManagerDecisionCard(decision = managerDecision)
                    }

                    // 6. Background Workload Balancer & Token Health
                    Spacer(modifier = Modifier.height(12.dp))
                    BackgroundWorkloadBalancerMonitorCard(uiState = uiState)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons (Save & Rescan)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.runDeepAgentAnalysis() },
                            colors = ButtonDefaults.buttonColors(containerColor = AgentPurple),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rescan All Nodes", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.saveCurrentReport() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Report", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatPrice(price: Double): String {
    return com.example.domain.util.PriceFormatter.formatPrice(price)
}
