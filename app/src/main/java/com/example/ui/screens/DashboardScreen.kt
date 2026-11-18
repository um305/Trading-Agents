package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import com.example.domain.model.AssetType
import com.example.domain.model.TradingMarketType
import com.example.domain.master.TradingHorizon
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.adaptive.AdaptiveLayoutUtils
import com.example.ui.components.AgentMatrixGrid
import com.example.ui.components.CandlestickChart
import com.example.ui.components.DayTraderAlertBanner
import com.example.ui.components.ExchangeSourceSelector
import com.example.ui.components.HorizonLadderTable
import com.example.ui.components.MasterVerdictCard
import com.example.ui.components.PlainTradeCard
import com.example.ui.theme.AgentPurple
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    uiState: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val quote = uiState.currentQuote
    val synthesis = uiState.agentSynthesis

    val isExpanded = AdaptiveLayoutUtils.isExpanded
    val isMedium = AdaptiveLayoutUtils.isMedium
    val isLandscape = AdaptiveLayoutUtils.isLandscape
    val isCompact = AdaptiveLayoutUtils.isCompact

    // Two-column layout on wide screens (tablets or medium-landscape)
    val useTwoColumns = isExpanded || (isMedium && isLandscape)

    val isCrypto = quote?.assetType == AssetType.CRYPTO ||
        (quote == null && (
            uiState.selectedMarketType == TradingMarketType.FUTURES ||
            uiState.selectedSymbol.endsWith("USDT") ||
            uiState.selectedSymbol.endsWith("USDC") ||
            uiState.selectedSymbol.startsWith("1000") ||
            uiState.selectedSymbol.uppercase() in listOf("BTC", "ETH", "SOL", "BNB", "XRP", "ADA", "DOGE", "AVAX", "DOT", "LINK", "SUI", "NEAR", "PEPE", "SHIB", "HYPE", "UNI", "SNDK", "SNDKB", "SEI", "TIA", "TAO", "RENDER")
        ))

    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val screenPadding = if (screenWidthDp <= 360) 8.dp else if (isCompact) 12.dp else 16.dp

    if (useTwoColumns) {
        // Wide Screen / Tablet Two-Column Adaptive Layout
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(screenPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Pane: Search, Controls, Live Quote, Chart
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                InstitutionalSearchSection(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onExecuteSearch = { query ->
                        viewModel.executeUnifiedQuery(query)
                        searchQuery = ""
                    },
                    selectedSymbol = uiState.selectedSymbol,
                    onSelectSuggestion = { sym, assetType ->
                        viewModel.selectSymbol(sym, assetType)
                        searchQuery = ""
                    },
                    currentQuote = quote,
                    cryptoScanList = uiState.cryptoScanList,
                    cachedCryptoPairs = uiState.cachedCryptoPairs
                )

                Spacer(modifier = Modifier.height(10.dp))

                InstitutionalControlBar(
                    selectedTimeframe = uiState.selectedTimeframe,
                    onSelectTimeframe = { viewModel.setTimeframe(it) },
                    selectedMarketType = uiState.selectedMarketType,
                    onSelectMarketType = { viewModel.setMarketType(it) },
                    selectedPositionSide = uiState.positionSetupSide,
                    onSelectPositionSide = { viewModel.setPositionSide(it) },
                    isCrypto = isCrypto
                )

                Spacer(modifier = Modifier.height(8.dp))

                DayTraderAlertBanner(
                    alerts = uiState.dayTraderAlerts,
                    onDismissAll = { viewModel.dismissAlertBanner() }
                )

                DayTraderScannerShortcutPill(
                    hitCount = uiState.scanHits.size,
                    isScanning = uiState.isRunningDayTraderScan,
                    onClick = { viewModel.setScreen(AppScreen.SCANNER) }
                )

                BackgroundScanStatusBar(
                    isRunning = uiState.isRunningAgents || uiState.isLoadingQuote,
                    phaseText = uiState.agentRunningPhase
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (quote != null) {
                    LiveMarketPriceCard(
                        quote = quote,
                        uiState = uiState,
                        onSwitchAssetType = { type ->
                            viewModel.selectSymbol(quote.symbol, type)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ChartContainer(
                        uiState = uiState,
                        quote = quote,
                        chartHeightDp = 290,
                        onSwitchToLiveSetup = { viewModel.switchToLiveSetup() }
                    )
                } else {
                    EmptyStateCard()
                }
            }

            // Right Pane: Multi-Exchange Feed & Executive Decision Result Card
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                if (quote != null) {
                    if (isCrypto && uiState.exchangeQuotes.isNotEmpty()) {
                        ExchangeSourceSelector(
                            selectedExchange = uiState.selectedExchange,
                            exchangeQuotes = uiState.exchangeQuotes,
                            onSelectExchange = { viewModel.setSelectedExchange(it) },
                            symbol = quote.symbol
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    uiState.masterVerdict?.let { mv ->
                        val isArmed = uiState.armedPlans.any {
                            it.symbol.equals(mv.symbol, true) &&
                                it.marketType == mv.marketType.name &&
                                it.horizon == uiState.selectedHorizon.name
                        }
                        MasterVerdictCard(
                            verdict = mv,
                            selectedHorizon = uiState.selectedHorizon,
                            isArmedForSelectedHorizon = isArmed,
                            futuresPerpAvailable = uiState.futuresPerpAvailableForSymbol,
                            isEngineOnly = uiState.isEngineOnly,
                            onSelectHorizon = { viewModel.selectHorizon(it) },
                            onArmAlert = { viewModel.armHorizonAlert(it) },
                            onPaperFollow = { viewModel.paperFollowHorizon(it) },
                            onSwitchToFuturesShort = { viewModel.toggleFuturesForShort() },
                            onShareFeedback = { viewModel.setFeedback(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizonLadderTable(
                            verdict = mv,
                            selectedHorizon = uiState.selectedHorizon,
                            onSelectHorizon = { viewModel.selectHorizon(it) },
                            onShareFeedback = { viewModel.setFeedback(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AgentMatrixGrid(matrix = mv.matrix)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    ExecutiveDecisionResultCard(
                        quote = quote,
                        synthesis = synthesis,
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    } else {
        // Phone Single-Column Layout (Adaptive width constraint and spacing)
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(screenPadding)
            ) {
                // 1. Search Bar with Suggestions below & Scan Button
                InstitutionalSearchSection(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onExecuteSearch = { query ->
                        viewModel.executeUnifiedQuery(query)
                        searchQuery = ""
                    },
                    selectedSymbol = uiState.selectedSymbol,
                    onSelectSuggestion = { sym, assetType ->
                        viewModel.selectSymbol(sym, assetType)
                        searchQuery = ""
                    },
                    currentQuote = quote,
                    cryptoScanList = uiState.cryptoScanList,
                    cachedCryptoPairs = uiState.cachedCryptoPairs
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Control Bar: Timeframe, Futures/Spot, Bullish/Bearish toggle
                InstitutionalControlBar(
                    selectedTimeframe = uiState.selectedTimeframe,
                    onSelectTimeframe = { viewModel.setTimeframe(it) },
                    selectedMarketType = uiState.selectedMarketType,
                    onSelectMarketType = { viewModel.setMarketType(it) },
                    selectedPositionSide = uiState.positionSetupSide,
                    onSelectPositionSide = { viewModel.setPositionSide(it) },
                    isCrypto = isCrypto
                )

                Spacer(modifier = Modifier.height(8.dp))

                DayTraderAlertBanner(
                    alerts = uiState.dayTraderAlerts,
                    onDismissAll = { viewModel.dismissAlertBanner() }
                )

                DayTraderScannerShortcutPill(
                    hitCount = uiState.scanHits.size,
                    isScanning = uiState.isRunningDayTraderScan,
                    onClick = { viewModel.setScreen(AppScreen.SCANNER) }
                )

                // 3. Background process status (discreet progress indicator)
                BackgroundScanStatusBar(
                    isRunning = uiState.isRunningAgents || uiState.isLoadingQuote,
                    phaseText = uiState.agentRunningPhase
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (quote != null) {
                    // 4. Live Market Price Card
                    LiveMarketPriceCard(
                        quote = quote,
                        uiState = uiState,
                        onSwitchAssetType = { type ->
                            viewModel.selectSymbol(quote.symbol, type)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. Result Chart (Candlestick Graph with Entry, TP1, TP2, TP3, and SL overlays)
                    val chartHeight = if (isLandscape) 190 else 240
                    ChartContainer(
                        uiState = uiState,
                        quote = quote,
                        chartHeightDp = chartHeight,
                        onSwitchToLiveSetup = { viewModel.switchToLiveSetup() },
                        onRecalculateSetup = { viewModel.recalibrateEntryToCurrentPrice() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5b. Plain-English "what to do now" card (beginner friendly)
                    uiState.masterVerdict?.let { mv ->
                        PlainTradeCard(
                            verdict = mv,
                            selectedHorizon = uiState.selectedHorizon,
                            isEngineOnly = uiState.isEngineOnly
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 6. Multi-Exchange Venue Comparison (Binance, Bybit, MEXC, OKX, Coinbase) - ONLY for Crypto
                    if (isCrypto && uiState.exchangeQuotes.isNotEmpty()) {
                        ExchangeSourceSelector(
                            selectedExchange = uiState.selectedExchange,
                            exchangeQuotes = uiState.exchangeQuotes,
                            onSelectExchange = { viewModel.setSelectedExchange(it) },
                            symbol = quote.symbol
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    uiState.masterVerdict?.let { mv ->
                        val isArmed = uiState.armedPlans.any {
                            it.symbol.equals(mv.symbol, true) &&
                                it.marketType == mv.marketType.name &&
                                it.horizon == uiState.selectedHorizon.name
                        }
                        MasterVerdictCard(
                            verdict = mv,
                            selectedHorizon = uiState.selectedHorizon,
                            isArmedForSelectedHorizon = isArmed,
                            futuresPerpAvailable = uiState.futuresPerpAvailableForSymbol,
                            isEngineOnly = uiState.isEngineOnly,
                            onSelectHorizon = { viewModel.selectHorizon(it) },
                            onArmAlert = { viewModel.armHorizonAlert(it) },
                            onPaperFollow = { viewModel.paperFollowHorizon(it) },
                            onSwitchToFuturesShort = { viewModel.toggleFuturesForShort() },
                            onShareFeedback = { viewModel.setFeedback(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizonLadderTable(
                            verdict = mv,
                            selectedHorizon = uiState.selectedHorizon,
                            onSelectHorizon = { viewModel.selectHorizon(it) },
                            onShareFeedback = { viewModel.setFeedback(it) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AgentMatrixGrid(matrix = mv.matrix)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 7. Executive Decision Result Card (Bullish/Bearish verdict, Entry, TP ladder, SL, R:R, SMC)
                    ExecutiveDecisionResultCard(
                        quote = quote,
                        synthesis = synthesis,
                        uiState = uiState,
                        viewModel = viewModel
                    )
                } else {
                    EmptyStateCard()
                }
            }
        }
    }
}

@Composable
private fun ChartContainer(
    uiState: UiState,
    quote: com.example.domain.model.MarketQuote,
    chartHeightDp: Int,
    onSwitchToLiveSetup: () -> Unit,
    onRecalculateSetup: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Column {
            // Header with Live Tape indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PRICE ACTION & VOLUME",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(BullishGreen)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (quote.assetType == com.example.domain.model.AssetType.CRYPTO) "LIVE TAPE (${uiState.selectedExchange.displayName})" else "STOCK FEED (Yahoo)",
                        color = BullishGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            // Saved Thesis Notice Banner
            if (uiState.isViewingSavedPlan && uiState.viewingSavedReport != null) {
                val rep = uiState.viewingSavedReport
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AgentPurple.copy(alpha = 0.15f))
                        .border(1.dp, AgentPurple.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
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
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(AgentPurple)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "VIEWING SAVED THESIS (${rep.symbol})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AgentPurple
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val formattedEntry = com.example.domain.util.PriceFormatter.formatCurrency(rep.price)
                            val formattedTarget = com.example.domain.util.PriceFormatter.formatCurrency(rep.targetPrice)
                            val formattedStop = com.example.domain.util.PriceFormatter.formatCurrency(rep.stopLossPrice)
                            Text(
                                text = "Saved Entry: $formattedEntry • Target: $formattedTarget • Stop: $formattedStop",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TechCyan.copy(alpha = 0.2f))
                                .border(1.dp, TechCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                .clickable { onSwitchToLiveSetup() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Switch to Live Setup",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TechCyan
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.isLoadingQuote) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeightDp.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = BullishGreen)
                }
            } else if (quote.candles.isNotEmpty()) {
                CandlestickChart(
                    candles = quote.candles,
                    modifier = Modifier.fillMaxWidth(),
                    showSma = true,
                    tradeSetupPlan = uiState.tradeSetupPlan,
                    currentPrice = quote.currentPrice,
                    masterVerdict = uiState.masterVerdict,
                    selectedHorizon = uiState.selectedHorizon,
                    onRecalculateSetup = onRecalculateSetup
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Search an asset to generate graph and execution targets",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Enter any crypto symbol (e.g. BTC, ETH, SOL, SNDK) or stock above to analyze",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DayTraderScannerShortcutPill(
    hitCount: Int,
    isScanning: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurface)
            .border(1.dp, TechCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isScanning) TechCyan else BullishGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Universal Day-Trader Scanner",
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(TechCyan.copy(alpha = 0.16f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isScanning) "Scanning..." else "$hitCount Rule Hits →",
                color = TechCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

