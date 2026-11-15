package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.AgentConsensusMode
import com.example.domain.model.AgentRole
import com.example.domain.model.BacktestResult
import com.example.domain.model.BacktestStrategyConfig
import com.example.domain.model.BacktestTrade
import com.example.domain.model.EquityPoint
import com.example.domain.model.PositionSide
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingAction
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BacktestScreen(
    uiState: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedSymbol by remember { mutableStateOf(uiState.selectedSymbol) }
    var selectedTimeframe by remember { mutableStateOf(uiState.selectedTimeframe) }
    var config by remember { mutableStateOf(uiState.backtestConfig) }
    var showConfigPanel by remember { mutableStateOf(true) }

    // Run backtest automatically if no results exist yet
    LaunchedEffect(Unit) {
        if (uiState.backtestResult == null && !uiState.isRunningBacktest) {
            viewModel.runStrategyBacktest(selectedSymbol, selectedTimeframe, config)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("backtest_screen")
    ) {
        // Screen Header
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
                        .background(TechCyan.copy(alpha = 0.15f))
                        .border(1.dp, TechCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = TechCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "STRATEGY BACKTEST",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AgentPurple.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "SIMULTANEOUS AI",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = AgentPurple
                            )
                        }
                    }
                    Text(
                        text = "Historical Multi-Agent Committee Simulation",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .clickable { showConfigPanel = !showConfigPanel }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = TechCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showConfigPanel) "Hide Config" else "Edit Strategy",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TechCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Strategy Configuration Card
        AnimatedVisibility(visible = showConfigPanel) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("strategy_config_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "1. TARGET ASSET & TIMEFRAME",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Asset Selector Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("NVDA", "BTC", "ETH", "SOL", "TSLA", "AAPL", "MSFT", "AMZN").forEach { sym ->
                            val isSelected = selectedSymbol.equals(sym, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) TechCyan.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                    .border(1.dp, if (isSelected) TechCyan else DarkBorder, RoundedCornerShape(8.dp))
                                    .clickable { selectedSymbol = sym }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = sym,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (isSelected) TechCyan else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Timeframe Selector Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Timeframe.ONE_HOUR,
                            Timeframe.FOUR_HOURS,
                            Timeframe.ONE_DAY,
                            Timeframe.ONE_MONTH,
                            Timeframe.THREE_MONTHS,
                            Timeframe.ONE_YEAR
                        ).forEach { tf ->
                            val isSelected = selectedTimeframe == tf
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) BullishGreen.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                    .border(1.dp, if (isSelected) BullishGreen else DarkBorder, RoundedCornerShape(6.dp))
                                    .clickable { selectedTimeframe = tf }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = tf.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) BullishGreen else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Multi-Agent Consensus Mode
                    Text(
                        text = "2. MULTI-AGENT CONSENSUS MODE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    AgentConsensusMode.values().forEach { mode ->
                        val isSelected = config.consensusMode == mode
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AgentPurple.copy(alpha = 0.15f) else DarkSurfaceElevated)
                                .border(1.dp, if (isSelected) AgentPurple else DarkBorder, RoundedCornerShape(8.dp))
                                .clickable { config = config.copy(consensusMode = mode) }
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) AgentPurple else DarkBorder)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = mode.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) AgentPurple else TextPrimary
                                    )
                                    Text(
                                        text = mode.description,
                                        fontSize = 9.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Execution Parameters
                    Text(
                        text = "3. CAPITAL ALLOCATION & LEVERAGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Initial Capital:", fontSize = 11.sp, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(5_000.0, 10_000.0, 25_000.0, 50_000.0).forEach { cap ->
                                val isSelected = config.initialCapital == cap
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) TechCyan.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                        .border(1.dp, if (isSelected) TechCyan else DarkBorder, RoundedCornerShape(6.dp))
                                        .clickable { config = config.copy(initialCapital = cap) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "$${(cap / 1000).toInt()}k",
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TechCyan else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Simulated Leverage:", fontSize = 11.sp, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(1, 3, 5, 10, 20).forEach { lev ->
                                val isSelected = config.leverage == lev
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) TechCyan.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                        .border(1.dp, if (isSelected) TechCyan else DarkBorder, RoundedCornerShape(6.dp))
                                        .clickable { config = config.copy(leverage = lev) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${lev}x",
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TechCyan else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Execute Button
                    Button(
                        onClick = {
                            viewModel.runStrategyBacktest(selectedSymbol, selectedTimeframe, config)
                            showConfigPanel = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("run_backtest_button"),
                        enabled = !uiState.isRunningBacktest,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechCyan,
                            contentColor = DarkBackground
                        )
                    ) {
                        if (uiState.isRunningBacktest) {
                            CircularProgressIndicator(
                                color = DarkBackground,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simulating Multi-Agent Strategy...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RUN MULTI-AGENT BACKTEST", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Backtest Results Dashboard
        val result = uiState.backtestResult
        if (result != null) {
            val isProfit = result.totalReturnPercent >= 0.0

            // 1. Performance Overview Scorecard
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backtest_scorecard"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isProfit) BullishGreen.copy(alpha = 0.5f) else BearishRed.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "BACKTEST PERFORMANCE: ${result.symbol}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${if (isProfit) "+" else ""}${String.format(Locale.US, "%.2f", result.totalReturnPercent)}%",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isProfit) BullishGreen else BearishRed
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "(${if (result.totalReturnUsd >= 0) "+" else ""}$${String.format(Locale.US, "%,.2f", result.totalReturnUsd)})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isProfit) BullishGreen else BearishRed
                                )
                            }
                        }

                        // Alpha vs Benchmark Pill
                        val isAlphaPositive = result.alphaPercent >= 0
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAlphaPositive) BullishGreen.copy(alpha = 0.15f) else BearishRed.copy(alpha = 0.15f))
                                .border(1.dp, if (isAlphaPositive) BullishGreen.copy(alpha = 0.4f) else BearishRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "ALPHA vs HODL",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${if (isAlphaPositive) "+" else ""}${String.format(Locale.US, "%.1f", result.alphaPercent)}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isAlphaPositive) BullishGreen else BearishRed
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 6-Tile Key Metrics Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            title = "WIN RATE",
                            value = "${String.format(Locale.US, "%.1f", result.winRatePercent)}%",
                            subtitle = "${result.winningTrades}W / ${result.losingTrades}L",
                            valueColor = if (result.winRatePercent >= 50) BullishGreen else RiskAmber,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "PROFIT FACTOR",
                            value = String.format(Locale.US, "%.2f", result.profitFactor),
                            subtitle = if (result.profitFactor > 1.5) "Robust Edge" else "Modest Edge",
                            valueColor = if (result.profitFactor >= 1.5) BullishGreen else TechCyan,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "MAX DRAWDOWN",
                            value = "-${String.format(Locale.US, "%.1f", result.maxDrawdownPercent)}%",
                            subtitle = "Peak-to-Trough",
                            valueColor = if (result.maxDrawdownPercent < 15.0) BullishGreen else BearishRed,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            title = "SHARPE RATIO",
                            value = String.format(Locale.US, "%.2f", result.sharpeRatio),
                            subtitle = if (result.sharpeRatio >= 2.0) "Institutional Grade" else "Standard Risk",
                            valueColor = if (result.sharpeRatio >= 1.5) TechCyan else TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "TOTAL TRADES",
                            value = "${result.totalTrades}",
                            subtitle = "Multi-Agent Signals",
                            valueColor = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            title = "AVG TRADE",
                            value = "${if (result.averageTradeReturnPercent >= 0) "+" else ""}${String.format(Locale.US, "%.2f", result.averageTradeReturnPercent)}%",
                            subtitle = "Per Position",
                            valueColor = if (result.averageTradeReturnPercent >= 0) BullishGreen else BearishRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Interactive Strategy Equity Curve vs Buy & Hold Benchmark
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QueryStats,
                                contentDescription = null,
                                tint = TechCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EQUITY CURVE vs BENCHMARK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp, 3.dp).background(TechCyan))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Strategy", fontSize = 9.sp, color = TechCyan, fontWeight = FontWeight.Bold)

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(modifier = Modifier.size(8.dp, 2.dp).background(TextSecondary))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Buy & Hold", fontSize = 9.sp, color = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Equity Curve Canvas Chart
                    EquityCurveChart(
                        equityPoints = result.equityCurve,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Multi-Agent Attribution Breakdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, AgentPurple.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = AgentPurple,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MULTI-AGENT PROFIT ATTRIBUTION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "Realized edge generated simultaneously across specialized intelligence nodes",
                        fontSize = 9.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val totalAttribution = result.agentContributionStats.values.sum().coerceAtLeast(1.0)
                    result.agentContributionStats.forEach { (role, pnl) ->
                        val percentOfTotal = (pnl / totalAttribution) * 100.0
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = when (role) {
                                        AgentRole.TECHNICAL -> "Technical Studies Node"
                                        AgentRole.SENTIMENT -> "Sentiment & News Catalyst Node"
                                        AgentRole.FUNDAMENTAL -> "Fundamental Valuation Node"
                                        AgentRole.RISK_MANAGEMENT -> "Risk Officer Loss Minimization"
                                        else -> role.title
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "+$${String.format(Locale.US, "%,.2f", pnl)} (${String.format(Locale.US, "%.1f", percentOfTotal)}%)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BullishGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(DarkSurfaceElevated)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = (percentOfTotal / 100.0).toFloat().coerceIn(0.05f, 1f))
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            when (role) {
                                                AgentRole.TECHNICAL -> TechCyan
                                                AgentRole.SENTIMENT -> AgentPurple
                                                AgentRole.FUNDAMENTAL -> BullishGreen
                                                AgentRole.RISK_MANAGEMENT -> RiskAmber
                                                else -> TechCyan
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Simulated Trade History Log
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = TechCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SIMULATED TRADES JOURNAL (${result.trades.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Simulated in ${result.executionTimeMs}ms",
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (result.trades.isEmpty()) {
                        Text(
                            text = "No trades met the minimum AI confidence threshold during this window. Adjust threshold or consensus mode to loosen filters.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        result.trades.forEach { trade ->
                            TradeHistoryItem(trade = trade)
                            HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .padding(8.dp)
    ) {
        Column {
            Text(text = title, fontSize = 8.sp, color = TextMuted, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
            Spacer(modifier = Modifier.height(1.dp))
            Text(text = subtitle, fontSize = 8.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun TradeHistoryItem(trade: BacktestTrade) {
    val isWin = trade.isWin
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val entryDate = remember(trade.entryTimestamp) { sdf.format(Date(trade.entryTimestamp)) }
    val exitDate = remember(trade.exitTimestamp) { sdf.format(Date(trade.exitTimestamp)) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isWin) BullishGreen.copy(alpha = 0.2f) else BearishRed.copy(alpha = 0.2f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${trade.side.name} • ${trade.id}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isWin) BullishGreen else BearishRed
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$entryDate → $exitDate",
                    fontSize = 9.sp,
                    color = TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${if (trade.returnPercent >= 0) "+" else ""}${String.format(Locale.US, "%.2f", trade.returnPercent)}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isWin) BullishGreen else BearishRed
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(${if (trade.returnUsd >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", trade.returnUsd)})",
                    fontSize = 9.sp,
                    color = if (isWin) BullishGreen else BearishRed
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "In: $${String.format(Locale.US, "%.2f", trade.entryPrice)} | Out: $${String.format(Locale.US, "%.2f", trade.exitPrice)}",
                fontSize = 9.sp,
                color = TextSecondary
            )
            Text(
                text = trade.exitReason,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (trade.exitReason.contains("Take Profit")) BullishGreen else if (trade.exitReason.contains("Stop Loss")) BearishRed else TechCyan
            )
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "AI Thesis: ${trade.consensusThesis}",
            fontSize = 8.sp,
            color = TextMuted
        )
    }
}

@Composable
private fun EquityCurveChart(
    equityPoints: List<EquityPoint>,
    modifier: Modifier = Modifier
) {
    if (equityPoints.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Insufficient bars to plot equity curve", color = TextSecondary, fontSize = 11.sp)
        }
        return
    }

    var selectedIdx by remember { mutableStateOf<Int?>(null) }

    val allVals = equityPoints.flatMap { listOf(it.equity, it.benchmarkEquity) }
    val minVal = allVals.minOrNull() ?: 0.0
    val maxVal = allVals.maxOrNull() ?: 1.0
    val valRange = (maxVal - minVal).coerceAtLeast(1.0)

    val activePoint = selectedIdx?.let { equityPoints.getOrNull(it) } ?: equityPoints.last()
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
    val activeDate = remember(activePoint.timestamp) { sdf.format(Date(activePoint.timestamp)) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$activeDate | Strategy: $${String.format(Locale.US, "%,.0f", activePoint.equity)} | HODL: $${String.format(Locale.US, "%,.0f", activePoint.benchmarkEquity)}",
                fontSize = 10.sp,
                color = TextSecondary
            )
            Text(
                text = "Drawdown: -${String.format(Locale.US, "%.1f", activePoint.drawdownPercent)}%",
                fontSize = 10.sp,
                color = if (activePoint.drawdownPercent > 10) BearishRed else TechCyan
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(equityPoints) {
                    detectTapGestures(
                        onPress = { offset ->
                            val step = size.width / (equityPoints.size - 1)
                            val idx = (offset.x / step).toInt().coerceIn(0, equityPoints.size - 1)
                            selectedIdx = idx
                            tryAwaitRelease()
                            selectedIdx = null
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val step = w / (equityPoints.size - 1)

            // Grid lines
            for (i in 0..3) {
                val y = (h / 3) * i
                drawLine(
                    color = DarkBorder.copy(alpha = 0.35f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }

            // Benchmark Line (Dashed)
            val benchmarkPath = Path()
            equityPoints.forEachIndexed { i, pt ->
                val x = i * step
                val y = h - ((pt.benchmarkEquity - minVal) / valRange * h).toFloat()
                if (i == 0) benchmarkPath.moveTo(x, y) else benchmarkPath.lineTo(x, y)
            }
            drawPath(
                path = benchmarkPath,
                color = TextSecondary.copy(alpha = 0.6f),
                style = Stroke(width = 1.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f))
            )

            // Strategy Equity Line & Gradient Area
            val strategyPath = Path()
            val areaPath = Path()
            equityPoints.forEachIndexed { i, pt ->
                val x = i * step
                val y = h - ((pt.equity - minVal) / valRange * h).toFloat()
                if (i == 0) {
                    strategyPath.moveTo(x, y)
                    areaPath.moveTo(x, h)
                    areaPath.lineTo(x, y)
                } else {
                    strategyPath.lineTo(x, y)
                    areaPath.lineTo(x, y)
                }
            }
            areaPath.lineTo((equityPoints.size - 1) * step, h)
            areaPath.close()

            drawPath(
                path = areaPath,
                color = TechCyan.copy(alpha = 0.10f)
            )

            drawPath(
                path = strategyPath,
                color = TechCyan,
                style = Stroke(width = 2.5f)
            )

            // Scrubber line if touched
            selectedIdx?.let { idx ->
                val x = idx * step
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }
        }
    }
}
