package com.example.ui.components

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
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AgentRole
import com.example.domain.model.AgentStance
import com.example.domain.model.DebateMessage
import com.example.ui.theme.AgentPurple
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AgentDebateCard(
    message: DebateMessage,
    modifier: Modifier = Modifier
) {
    val (agentColor, agentIcon) = when (message.role) {
        AgentRole.FUNDAMENTAL -> Pair(BullishGreen, Icons.Default.QueryStats)
        AgentRole.TECHNICAL -> Pair(TechCyan, Icons.Default.TrendingUp)
        AgentRole.TRADINGVIEW_STUDIES -> Pair(Color(0xFF2962FF), Icons.Default.QueryStats)
        AgentRole.SENTIMENT -> Pair(RiskAmber, Icons.Default.Newspaper)
        AgentRole.NEWS_SCANNER -> Pair(RiskAmber, Icons.Default.Newspaper)
        AgentRole.RISK_MANAGEMENT -> Pair(BearishRed, Icons.Default.Shield)
        AgentRole.CHIEF_STRATEGIST -> Pair(AgentPurple, Icons.Default.Balance)
        AgentRole.MARKET_MANIPULATION -> Pair(RiskAmber, Icons.Default.Shield)
        else -> Pair(TechCyan, Icons.Default.QueryStats)
    }

    val stanceIcon = when (message.stance) {
        AgentStance.BULLISH -> Icons.Default.TrendingUp
        AgentStance.BEARISH -> Icons.Default.TrendingDown
        AgentStance.NEUTRAL -> Icons.Default.CrisisAlert
    }

    val stanceColor = when (message.stance) {
        AgentStance.BULLISH -> BullishGreen
        AgentStance.BEARISH -> BearishRed
        AgentStance.NEUTRAL -> TextSecondary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, DarkBorder.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column {
            // Header Row: Agent Icon + Name + Round badge + Stance badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(agentColor.copy(alpha = 0.15f))
                            .border(1.dp, agentColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = agentIcon,
                            contentDescription = message.agentName,
                            tint = agentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = message.agentName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Debate Round ${message.round}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // Stance Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(stanceColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = stanceIcon,
                            contentDescription = null,
                            tint = stanceColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = message.stance.name,
                            color = stanceColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Message Argument text
            Text(
                text = message.argument,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}
