package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.MultiAiDispatcher
import com.example.domain.model.FundamentalsStreamData
import com.example.domain.model.ManagerDecision
import com.example.domain.model.MarketStreamData
import com.example.domain.model.MultiAgentSynthesis
import com.example.domain.model.NewsStreamData
import com.example.domain.model.ResearcherDossier
import com.example.domain.model.RiskProfileEvaluation
import com.example.domain.model.RiskTeamEvaluation
import com.example.domain.model.SocialMediaStreamData
import com.example.domain.model.TraderProposal
import com.example.domain.model.TradingAction
import com.example.domain.util.PriceFormatter
import com.example.ui.UiState
import com.example.ui.theme.AgentPurple
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

/**
 * Renders the 4 Live Ingestion Data Streams from the Architecture Schema:
 * 1. Market Data (Yahoo! Finance, Candlestick Chart, Technical Oscillators)
 * 2. Social Media Data (X / Twitter, Reddit, EODHD APIs)
 * 3. News Data (Bloomberg, Reddit, Finnhub, Reuters)
 * 4. Fundamental Data (Company Profile, Financial History, Insider Transactions)
 */
@Composable
fun IngestionStreamsAccordionCard(
    synthesis: MultiAgentSynthesis,
    modifier: Modifier = Modifier
) {
    var expandedStream by remember { mutableStateOf<String?>("MARKET") }

    val market = synthesis.marketStream
    val social = synthesis.socialMediaStream
    val news = synthesis.newsStream
    val fundamentals = synthesis.fundamentalsStream

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, TechCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("ingestion_streams_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TechCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Data Streams",
                        tint = TechCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "LIVE DATA INGESTION STREAMS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TechCyan
                    )
                    Text(
                        text = "Market • Social Media • News • Fundamentals",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BullishGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "4 STREAMS ACTIVE",
                    color = BullishGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Stream Selectors
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StreamTabChip("MARKET", "Market", expandedStream == "MARKET") { expandedStream = if (expandedStream == "MARKET") null else "MARKET" }
            StreamTabChip("SOCIAL", "Social Media", expandedStream == "SOCIAL") { expandedStream = if (expandedStream == "SOCIAL") null else "SOCIAL" }
            StreamTabChip("NEWS", "News", expandedStream == "NEWS") { expandedStream = if (expandedStream == "NEWS") null else "NEWS" }
            StreamTabChip("FUNDAMENTALS", "Fundamentals", expandedStream == "FUNDAMENTALS") { expandedStream = if (expandedStream == "FUNDAMENTALS") null else "FUNDAMENTALS" }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stream Content
        when (expandedStream) {
            "MARKET" -> if (market != null) MarketStreamDetails(market) else Text("Loading market stream...", color = TextMuted, fontSize = 11.sp)
            "SOCIAL" -> if (social != null) SocialMediaStreamDetails(social) else Text("Loading social stream...", color = TextMuted, fontSize = 11.sp)
            "NEWS" -> if (news != null) NewsStreamDetails(news) else Text("Loading news stream...", color = TextMuted, fontSize = 11.sp)
            "FUNDAMENTALS" -> if (fundamentals != null) FundamentalsStreamDetails(fundamentals) else Text("Loading fundamentals...", color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun StreamTabChip(
    key: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) TechCyan.copy(alpha = 0.2f) else DarkSurfaceElevated)
            .border(1.dp, if (isSelected) TechCyan else DarkBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) TechCyan else TextSecondary
        )
    }
}

@Composable
private fun MarketStreamDetails(data: MarketStreamData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Source: ${data.sourceName}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            val pColor = if (data.dayChangePercent >= 0) BullishGreen else BearishRed
            Text(
                text = "${PriceFormatter.formatCurrency(data.realTimePrice)} (${PriceFormatter.formatSignedPercent(data.dayChangePercent, 2)})",
                color = pColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text("Oscillators: ${data.technicalOscillatorsVerdict}", color = TechCyan, fontSize = 11.sp)
        Text("Chart Pattern: ${data.chartPatternDetected}", color = TextSecondary, fontSize = 10.sp)
        Text("Moving Averages: ${data.movingAverageAlignment}", color = TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SocialMediaStreamDetails(data: SocialMediaStreamData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("X / Twitter Ingestion", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text(data.xTwitterVolume24h, color = TechCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text("Cashtag Velocity: ${data.xCashtagVelocity}", color = BullishGreen, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Reddit Discovery: ${data.redditTopSubreddits.joinToString(", ")}", color = TextSecondary, fontSize = 10.sp)
        Text("Mentions: ${data.redditCommentMentions} posts/comments", color = TextMuted, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("EODHD Buzz Index: ${data.eodhdSocialBuzz}", color = RiskAmber, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NewsStreamDetails(data: NewsStreamData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .padding(10.dp)
    ) {
        Text("Bloomberg & Reuters Wire Headlines", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        data.bloombergHeadlines.take(2).forEach { h ->
            Text("• Bloomberg: $h", color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
        }
        data.finnhubCatalysts.take(1).forEach { f ->
            Text("• Finnhub Catalyst: $f", color = RiskAmber, fontSize = 10.sp, lineHeight = 14.sp)
        }
        data.reutersWireUpdates.take(1).forEach { r ->
            Text("• Reuters Wire: $r", color = TechCyan, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun FundamentalsStreamDetails(data: FundamentalsStreamData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(data.companyProfile, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Text("Mkt Cap: ${data.marketCapFormatted}", color = TechCyan, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("Valuation: P/E ${data.peRatio} • Rev Growth: ${data.revenueGrowthYoY} • FCF: ${data.freeCashFlow}", color = TextSecondary, fontSize = 10.sp)
        Text("Balance Sheet: ${data.balanceSheetHealth}", color = BullishGreen, fontSize = 10.sp)
        Text("Insider Form 4: ${data.insiderTransactionsSummary}", color = RiskAmber, fontSize = 10.sp)
        Text("Institutional Whales: ${data.institutionalWhaleHoldings}", color = TextMuted, fontSize = 9.sp)
    }
}

/**
 * Researcher Team Deliberation:
 * Bullish Researcher vs Bearish Researcher discussion -> Buy Evidence vs Sell Evidence
 */
@Composable
fun ResearcherTeamCard(
    dossier: ResearcherDossier,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, AgentPurple.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("researcher_team_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AgentPurple.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Researcher Team",
                        tint = AgentPurple,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "RESEARCHER TEAM DEBATE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AgentPurple
                    )
                    Text(
                        text = "Bullish vs Bearish Deliberation • Buy & Sell Evidence",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Net Evidence Meter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Net Evidence Score", fontSize = 10.sp, color = TextSecondary)
            val scoreColor = if (dossier.netEvidenceScore >= 0) BullishGreen else BearishRed
            Text(
                text = "${if (dossier.netEvidenceScore >= 0) "+" else ""}${String.format(Locale.US, "%.1f", dossier.netEvidenceScore)} / 10",
                color = scoreColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        val normScore = ((dossier.netEvidenceScore + 10.0) / 20.0).toFloat().coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { normScore },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (dossier.netEvidenceScore >= 0) BullishGreen else BearishRed,
            trackColor = DarkSurfaceElevated
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = dossier.discussionSummary,
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 15.sp
        )

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                // Buy Evidence
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BullishGreen.copy(alpha = 0.1f))
                        .border(1.dp, BullishGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Bull Researcher Thesis: ${dossier.bullishResearcherThesis}", color = BullishGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        dossier.buyEvidence.forEach { e ->
                            Text("• $e", color = TextPrimary, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sell Evidence
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BearishRed.copy(alpha = 0.1f))
                        .border(1.dp, BearishRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Bear Researcher Thesis: ${dossier.bearishResearcherThesis}", color = BearishRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        dossier.sellEvidence.forEach { e ->
                            Text("• $e", color = TextPrimary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Trader Proposal (Powered by OpenAI o1 Deep Thinking Model)
 */
@Composable
fun TraderProposalCard(
    proposal: TraderProposal,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, RiskAmber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("trader_proposal_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RiskAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Trader Proposal",
                        tint = RiskAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "TRADER PROPOSAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RiskAmber
                    )
                    Text(
                        text = proposal.modelBadge,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (proposal.proposedAction.isPositive) BullishGreen.copy(alpha = 0.2f) else BearishRed.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = proposal.proposedAction.label,
                    color = if (proposal.proposedAction.isPositive) BullishGreen else BearishRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Strategy & Execution Specs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceElevated)
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Strategy", color = TextMuted, fontSize = 9.sp)
                Text(proposal.proposedStrategy, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Column {
                Text("Limit Entry", color = TextMuted, fontSize = 9.sp)
                Text(PriceFormatter.formatCurrency(proposal.recommendedLimitEntry), color = TechCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Column {
                Text("Stop Loss", color = TextMuted, fontSize = 9.sp)
                Text(PriceFormatter.formatCurrency(proposal.proposedStopLoss), color = BearishRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
            Column {
                Text("R:R Ratio", color = TextMuted, fontSize = 9.sp)
                Text(proposal.riskRewardRatio.ifBlank { "-" }, color = BullishGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("TP1: ${PriceFormatter.formatCurrency(proposal.proposedTakeProfit1)} • TP2: ${PriceFormatter.formatCurrency(proposal.proposedTakeProfit2)}", color = TextSecondary, fontSize = 10.sp)
            Text("Size: ${proposal.recommendedPositionSizePercent}% (${proposal.recommendedLeverage}x)", color = TextMuted, fontSize = 10.sp)
        }

        // Reasoning steps toggle
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isEngineOnly = proposal.modelBadge.contains("Engine", ignoreCase = true)
            val chainTitle = if (isEngineOnly) "Rule-Based Execution Logic (${proposal.reasoningSteps.size} Steps)" else "${proposal.modelBadge} Chain (${proposal.reasoningSteps.size} Steps)"
            Text(chainTitle, fontSize = 10.sp, color = TechCyan, fontWeight = FontWeight.SemiBold)
            Icon(imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = TechCyan, modifier = Modifier.size(16.dp))
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                proposal.reasoningSteps.forEachIndexed { i, step ->
                    Text("${i + 1}. $step", color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
                    Spacer(modifier = Modifier.height(3.dp))
                }
            }
        }
    }
}

/**
 * Risk Management Team Card (Aggressive, Neutral, Conservative)
 */
@Composable
fun RiskTeamCard(
    team: RiskTeamEvaluation,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, BearishRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("risk_team_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BearishRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Risk Team",
                        tint = BearishRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "RISK MANAGEMENT TEAM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BearishRed
                    )
                    Text(
                        text = "3-Profile Audit: Aggressive • Neutral • Conservative",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Consensus: ${team.consensusRiskLevel}",
                    color = TechCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Profile Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RiskProfileMiniBox("Aggressive", team.aggressiveProfile, Modifier.weight(1f))
            RiskProfileMiniBox("Neutral", team.neutralProfile, Modifier.weight(1f))
            RiskProfileMiniBox("Conservative", team.conservativeProfile, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RiskProfileMiniBox(title: String, p: RiskProfileEvaluation, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .padding(8.dp)
    ) {
        Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        Text(p.riskRating, color = TextMuted, fontSize = 8.sp, maxLines = 1)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Size: ${p.maxPositionSizePercent}%", color = TechCyan, fontSize = 9.sp)
        Text("Lev: ${p.suggestedLeverage}x", color = TextSecondary, fontSize = 9.sp)
        Text(p.evaluationVerdict, color = BullishGreen, fontSize = 8.sp, maxLines = 1)
    }
}

/**
 * Manager Decision Card (Authorization & Execution Dispatch)
 */
@Composable
fun ManagerDecisionCard(
    decision: ManagerDecision,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, BullishGreen.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("manager_decision_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BullishGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = "Manager Decision",
                        tint = BullishGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "EXECUTIVE MANAGER DECISION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BullishGreen
                    )
                    Text(
                        text = "Authorization & Venue Order Dispatch",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BullishGreen.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = decision.status,
                    color = BullishGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = decision.executiveRationale,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurfaceElevated)
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Venue: ${decision.executionVenue}", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text("Order: ${decision.orderType}", color = TechCyan, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(decision.executionReadiness, color = BullishGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Background Workload Balancer & Token Health Monitor
 */
@Composable
fun BackgroundWorkloadBalancerMonitorCard(
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    val metricsList: List<com.example.domain.model.ModelWorkloadMetrics> = remember(uiState.aiModels) {
        MultiAiDispatcher.getWorkloadMetrics(uiState.aiModels)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, TechCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("workload_balancer_monitor")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(TechCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Workload Balancer",
                        tint = TechCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "AI WORKLOAD & TOKEN BALANCER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TechCyan
                    )
                    Text(
                        text = "Active Mode: ${uiState.loadBalancingMode.label}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BullishGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "AUTO FAILOVER ON",
                    color = BullishGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mini metrics grid
        metricsList.take(4).forEach { m ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(m.availability.colorHex))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = m.modelName,
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${m.remainingTokens} tok rem",
                        color = TechCyan,
                        fontSize = 9.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${m.avgLatencyMs}ms",
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = m.availability.label + (if (m.cooldownRemainingSec > 0) " (${m.cooldownRemainingSec}s)" else ""),
                        color = Color(m.availability.colorHex),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
