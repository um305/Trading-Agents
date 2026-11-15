package com.example.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.NotificationsActive
import com.example.data.db.TradingSignal
import com.example.domain.daytrader.scanner.ScanDiagnosticReport
import com.example.domain.daytrader.scanner.ScanHitItem
import com.example.domain.daytrader.scanner.ScanMarketSummary
import com.example.domain.daytrader.scanner.ScanSetupItem
import com.example.domain.model.AssetType
import com.example.domain.model.PositionSide
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingAction
import com.example.domain.model.TradingMarketType
import com.example.domain.util.PriceFormatter
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.UiState
import java.text.SimpleDateFormat
import java.util.Date
import com.example.ui.components.ExchangeDataSourceSwitcher
import com.example.ui.components.SwitcherInterfaceMode
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
fun CryptoScannerScreen(
    uiState: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var scannerTab by remember { mutableStateOf("SETUPS") }
    val isFutures = uiState.selectedMarketType == TradingMarketType.FUTURES
    val leverage = uiState.selectedLeverage

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Universal Day-Trader Scanner Section (Stocks + Crypto + Perps)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = { viewModel.setScreen(AppScreen.TERMINAL) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Terminal", tint = TextPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Universal Day-Trader Scanner",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Stocks + Crypto • Closed-Bar Rules Only • RVOL & Replay",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            IconButton(
                onClick = { viewModel.runDayTraderScan(uiState.selectedScannerUniverse) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TechCyan.copy(alpha = 0.16f))
                    .border(1.dp, TechCyan.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .testTag("day_trader_scan_refresh_button")
            ) {
                if (uiState.isRunningDayTraderScan) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TechCyan, strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Run Day-Trader Scan", tint = TechCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Universe Filter Chips (ALL, STOCKS, CRYPTO, PERPS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL", "STOCKS", "CRYPTO", "PERPS").forEach { universe ->
                val isSelected = uiState.selectedScannerUniverse.equals(universe, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) TechCyan.copy(alpha = 0.22f) else DarkSurface)
                        .border(1.dp, if (isSelected) TechCyan else DarkBorder, RoundedCornerShape(18.dp))
                        .clickable { viewModel.runDayTraderScan(universe) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("universe_chip_$universe")
                ) {
                    Text(
                        text = universe,
                        color = if (isSelected) TechCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ScanDiagnosticReport Card
        uiState.scanDiagnostics?.let { diag ->
            DayTraderScanDiagnosticsCard(diag = diag)
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Market Breadth & Regime Summary Card
        uiState.scanMarketSummary?.let { summary ->
            DayTraderMarketSummaryCard(
                summary = summary,
                onOpenSetup = { sym, aType ->
                    viewModel.selectSymbol(sym, aType)
                    viewModel.setScreen(AppScreen.TERMINAL)
                },
                onArmAlert = { setup ->
                    viewModel.armConditionalAlert(
                        symbol = setup.symbol,
                        triggerPrice = setup.triggerPrice,
                        side = setup.side,
                        condition = setup.needsCondition
                    )
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Setups (default, graded) vs Raw rule matches tabs
        val filteredSetups = uiState.scanSetups.filter { s ->
            searchQuery.isBlank() ||
                s.symbol.contains(searchQuery, ignoreCase = true) ||
                s.name.contains(searchQuery, ignoreCase = true) ||
                s.modelTitle.contains(searchQuery, ignoreCase = true) ||
                s.needsCondition.contains(searchQuery, ignoreCase = true)
        }
        val filteredRuleHits = uiState.scanHits.filter { hit ->
            searchQuery.isBlank() ||
                hit.symbol.contains(searchQuery, ignoreCase = true) ||
                hit.name.contains(searchQuery, ignoreCase = true) ||
                hit.ruleDescription.contains(searchQuery, ignoreCase = true)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "SETUPS" to "Setups (${filteredSetups.size})",
                "RAW" to "Raw rule matches (${filteredRuleHits.size})"
            ).forEach { (tabId, tabLabel) ->
                val isSelected = scannerTab == tabId
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isSelected) TechCyan.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { scannerTab = tabId }
                        .padding(vertical = 8.dp)
                        .testTag("scanner_tab_$tabId"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabLabel,
                        color = if (isSelected) TechCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (uiState.isRunningDayTraderScan && filteredSetups.isEmpty() && filteredRuleHits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = TechCyan, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Scanning closed bars across ${uiState.selectedScannerUniverse}...", color = TechCyan, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else if (scannerTab == "SETUPS") {
            if (filteredSetups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tap Scan to evaluate graded setups across ${uiState.selectedScannerUniverse}.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { viewModel.runDayTraderScan(uiState.selectedScannerUniverse) },
                            colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Run Scan", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                filteredSetups.forEach { setup ->
                    DayTraderScanSetupCard(
                        setup = setup,
                        onOpen = {
                            viewModel.selectSymbol(setup.symbol, setup.assetType)
                            viewModel.setScreen(AppScreen.TERMINAL)
                        },
                        onArmAlert = {
                            viewModel.armConditionalAlert(
                                symbol = setup.symbol,
                                triggerPrice = setup.triggerPrice,
                                side = setup.side,
                                condition = setup.needsCondition
                            )
                        },
                        onSaveWatch = {
                            viewModel.saveSymbolToWatchlist(setup.symbol, setup.name, setup.assetType)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        } else if (filteredRuleHits.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "No raw pattern rules matched across ${uiState.selectedScannerUniverse} on closed daily/5m bars.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            filteredRuleHits.forEach { hit ->
                DayTraderScanHitCard(
                    hit = hit,
                    onClick = {
                        viewModel.selectSymbol(hit.symbol, hit.assetType)
                        viewModel.setScreen(AppScreen.TERMINAL)
                    }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Legacy Crypto Scanner Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(TechCyan.copy(alpha = 0.2f))
                        .border(1.dp, TechCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = "Crypto Scanner",
                        tint = TechCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Crypto Market Scanner",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Real-time Spot & Perpetual Futures Screen",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            IconButton(
                onClick = { viewModel.scanCrypto(uiState.selectedMarketType) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                    .testTag("scan_crypto_refresh_button")
            ) {
                if (uiState.isScanningCrypto) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TechCyan, strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Scanner", tint = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // THE SINGLE UNIFIED SECTION FOR FUTURE AND SPOT
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isFutures) TechCyan.copy(alpha = 0.25f) else Color.Transparent)
                        .clickable {
                            viewModel.setMarketType(TradingMarketType.FUTURES)
                            viewModel.scanCrypto(TradingMarketType.FUTURES)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = if (isFutures) TechCyan else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Perpetual Futures",
                            color = if (isFutures) TechCyan else TextSecondary,
                            fontWeight = if (isFutures) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (!isFutures) BullishGreen.copy(alpha = 0.25f) else Color.Transparent)
                        .clickable {
                            viewModel.setMarketType(TradingMarketType.SPOT)
                            viewModel.scanCrypto(TradingMarketType.SPOT)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Paid,
                            contentDescription = null,
                            tint = if (!isFutures) BullishGreen else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Spot Trading",
                            color = if (!isFutures) BullishGreen else TextSecondary,
                            fontWeight = if (!isFutures) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Market Status Descriptor Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFutures) TechCyan.copy(alpha = 0.1f) else BullishGreen.copy(alpha = 0.1f))
                .border(
                    1.dp,
                    if (isFutures) TechCyan.copy(alpha = 0.25f) else BullishGreen.copy(alpha = 0.25f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isFutures) "USDT-M Perpetual Swaps • Real Mark Prices & Funding Rates" else "Spot Cash Markets • Zero Liquidation Risk • Real-time Ticker",
                    fontSize = 11.sp,
                    color = if (isFutures) TechCyan else BullishGreen,
                    fontWeight = FontWeight.SemiBold
                )
                if (uiState.cachedTradingSignals.isNotEmpty()) {
                    Text(
                        text = "${uiState.cachedTradingSignals.size} Signals Ready",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Spacer(modifier = Modifier.height(10.dp))

        // Data Source Switcher: Binance, MEXC, Bybit (Tabs / Dropdown)
        ExchangeDataSourceSwitcher(
            selectedExchange = uiState.selectedExchange,
            exchangeQuotes = uiState.exchangeQuotes,
            onSelectExchange = {
                viewModel.setSelectedExchange(it)
                viewModel.scanCrypto(uiState.selectedMarketType)
            },
            symbol = "CRYPTO",
            initialMode = SwitcherInterfaceMode.TABS
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Scanning Duration Timeframe Selector (1m, 5m, 15m, 30m, 1h, 4h, 1D)
        Column {
            Text(
                text = "Scanning Candle Timeframe",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val scanTimeframes = listOf(
                    Timeframe.ONE_MIN,
                    Timeframe.FIVE_MIN,
                    Timeframe.FIFTEEN_MIN,
                    Timeframe.THIRTY_MIN,
                    Timeframe.ONE_HOUR,
                    Timeframe.FOUR_HOURS,
                    Timeframe.ONE_DAY
                )
                scanTimeframes.forEach { tf ->
                    val isTfSelected = tf == uiState.scanTimeframe
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isTfSelected) TechCyan.copy(alpha = 0.25f) else DarkSurface)
                            .border(
                                1.dp,
                                if (isTfSelected) TechCyan else DarkBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.setScanTimeframe(tf) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tf.label,
                            color = if (isTfSelected) TechCyan else TextSecondary,
                            fontWeight = if (isTfSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Futures Leverage & Liquidation Calculator Box (when Futures is active)
        AnimatedVisibility(visible = isFutures) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, TechCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = null,
                                    tint = TechCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Futures Leverage Risk Calculator",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${leverage}x Leverage",
                                color = TechCyan,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Leverage Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(2, 5, 10, 20, 50).forEach { lev ->
                                val selected = lev == leverage
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) TechCyan else DarkSurface)
                                        .clickable { viewModel.setLeverage(lev) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${lev}x",
                                        color = if (selected) Color.Black else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Estimated liquidation buffer: ${(100.0 / leverage).toInt()}% price adverse movement. Funding rates settle every 8 hours across perpetual contracts.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar for filtering tracked crypto pairs
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search crypto pair (e.g. BTC, ETH, SOL, DOGE)...", fontSize = 12.sp, color = TextMuted) },
            singleLine = true,
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TechCyan, modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TechCyan,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "ALL" to "All Assets",
                "FUNDING" to "High Funding Rate",
                "OVERSOLD" to "Oversold (RSI < 40)",
                "BULLISH" to "Bullish Signals"
            )

            filters.forEach { (key, label) ->
                val isSelected = selectedFilter == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) TechCyan.copy(alpha = 0.2f) else DarkSurface)
                        .border(1.dp, if (isSelected) TechCyan else DarkBorder, RoundedCornerShape(20.dp))
                        .clickable { selectedFilter = key }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) TechCyan else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Scanned Items List
        val filteredList = uiState.cryptoScanList.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                item.symbol.contains(searchQuery, ignoreCase = true) ||
                item.name.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                "FUNDING" -> (item.fundingRate ?: 0.0) >= 0.0100
                "OVERSOLD" -> item.rsi < 42.0
                "BULLISH" -> item.agentVerdict == TradingAction.BUY || item.agentVerdict == TradingAction.STRONG_BUY
                else -> true
            }
            matchesSearch && matchesFilter
        }

        if (uiState.isScanningCrypto && filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TechCyan)
            }
        } else {
            filteredList.forEach { item ->
                val isPos = item.change24h >= 0
                val changeColor = if (isPos) BullishGreen else BearishRed
                val rawSymbol = item.symbol.replace("-PERP", "").replace("/USDT", "")

                // Liquidation calculations for this coin
                val liqLong = item.price * (1.0 - (1.0 / leverage) + 0.005)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                        .clickable {
                            viewModel.selectSymbol(rawSymbol)
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
                                    text = item.symbol,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isFutures) TechCyan.copy(alpha = 0.15f) else BullishGreen.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .widthIn(min = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isFutures) "PERP" else "SPOT",
                                        color = if (isFutures) TechCyan else BullishGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // Verdict badge
                            val vColor = if (item.agentVerdict.isPositive) BullishGreen else BearishRed
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(vColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .widthIn(min = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.agentVerdict.label,
                                    color = vColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(text = item.name, color = TextMuted, fontSize = 11.sp)

                        Spacer(modifier = Modifier.height(10.dp))

                        // Price and 24h Change
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = com.example.domain.util.PriceFormatter.formatCurrency(item.price),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isPos) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = changeColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${if (isPos) "+" else ""}${String.format(Locale.US, "%.2f", item.change24h)}%",
                                        color = changeColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "RSI: ${String.format(Locale.US, "%.1f", item.rsi)}",
                                    color = if (item.rsi < 35) BullishGreen else if (item.rsi > 70) BearishRed else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Vol: ${formatCompactVol(item.volume24hUsd)}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Futures-specific details
                        if (isFutures) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Funding Rate", color = TextMuted, fontSize = 10.sp)
                                    val fRate = item.fundingRate ?: 0.0100
                                    Text(
                                        text = "${if (fRate >= 0) "+" else ""}${String.format(Locale.US, "%.4f", fRate)}%",
                                        color = if (fRate > 0.012) BearishRed else BullishGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column {
                                    Text("Open Interest", color = TextMuted, fontSize = 10.sp)
                                    Text(
                                        text = formatCompactVol(item.openInterestUsd ?: 1e7),
                                        color = TechCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${leverage}x Long Liq.", color = TextMuted, fontSize = 10.sp)
                                    Text(
                                        text = com.example.domain.util.PriceFormatter.formatCurrency(liqLong),
                                        color = BearishRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Explicit Buy Price, Target Sell Price & Stop Loss Guidance
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, BullishGreen.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("RECOMMENDED BUY", color = BullishGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    val buyP = if (item.recommendedBuyPrice > 0.0) item.recommendedBuyPrice else item.price
                                    Text(
                                        text = com.example.domain.util.PriceFormatter.formatCurrency(buyP),
                                        color = BullishGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Column {
                                    Text("TARGET SELL", color = TextMuted, fontSize = 9.sp)
                                    val tgtP = if (item.targetSellPrice > 0.0) item.targetSellPrice else (item.price * 1.08)
                                    Text(
                                        text = com.example.domain.util.PriceFormatter.formatCurrency(tgtP),
                                        color = TechCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("STOP LOSS", color = TextMuted, fontSize = 9.sp)
                                    val slP = if (item.stopLossPrice > 0.0) item.stopLossPrice else (item.price * 0.96)
                                    Text(
                                        text = com.example.domain.util.PriceFormatter.formatCurrency(slP),
                                        color = BearishRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Trend Signal and Quick Analyze Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "• ${item.trendSignal}",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = {
                                        val buyP = if (item.recommendedBuyPrice > 0.0) item.recommendedBuyPrice else item.price
                                        viewModel.setCustomPriceAlert(buyP)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BullishGreen),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BullishGreen.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ElectricBolt,
                                        contentDescription = null,
                                        tint = BullishGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Alert", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Button(
                                    onClick = {
                                        viewModel.selectSymbol(rawSymbol)
                                        viewModel.setScreen(AppScreen.TERMINAL)
                                        viewModel.runDeepAgentAnalysis()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AgentPurple),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Audit", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPrice(num: Double): String {
    return com.example.domain.util.PriceFormatter.formatPrice(num)
}

private fun formatCompactVol(vol: Double): String {
    return when {
        vol >= 1e9 -> String.format(Locale.US, "%.1fB", vol / 1e9)
        vol >= 1e6 -> String.format(Locale.US, "%.1fM", vol / 1e6)
        vol >= 1e3 -> String.format(Locale.US, "%.1fK", vol / 1e3)
        else -> String.format(Locale.US, "%.0f", vol)
    }
}

@Composable
private fun DayTraderScanDiagnosticsCard(diag: ScanDiagnosticReport) {
    val timeStr = remember(diag.dataTimestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(diag.dataTimestamp))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("scan_diagnostic_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "UNIVERSE: ${diag.universeLabel}",
                color = TechCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${diag.executionTimeMs}ms • @$timeStr",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Symbols: ${diag.totalSymbolsScanned}  |  Charts Returned: ${diag.chartsReturnedCount}  |  Usable Closed Bars: ${diag.usableChartsCount}",
            color = TextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun DayTraderScanHitCard(
    hit: ScanHitItem,
    onClick: () -> Unit
) {
    val sideColor = if (hit.side == PositionSide.LONG) BullishGreen else BearishRed
    val assetColor = if (hit.assetType == AssetType.STOCK) AgentPurple else TechCyan

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, sideColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp)
            .testTag("scan_hit_card_${hit.symbol}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hit.symbol,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(assetColor.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${hit.assetType.name} • ${hit.sessionBadge}",
                        color = assetColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(sideColor.copy(alpha = 0.18f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = hit.side.name,
                    color = sideColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = hit.ruleDescription,
            color = TechCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = PriceFormatter.formatCurrency(hit.currentPrice),
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "RVOL: ${String.format(Locale.US, "%.2f", hit.rvol)}x",
                color = if (hit.rvol >= 1.2) BullishGreen else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Families: ${hit.familiesAligned}/7",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Text(
                text = hit.marketRegime,
                color = RiskAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        hit.replayChipLabel?.let { chip ->
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AgentPurple.copy(alpha = 0.16f))
                    .border(1.dp, AgentPurple.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = chip,
                    color = AgentPurple,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayTraderMarketSummaryCard(
    summary: ScanMarketSummary,
    onOpenSetup: (String, AssetType) -> Unit,
    onArmAlert: (ScanSetupItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, TechCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("scanner_market_summary_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MARKET BREADTH & SETUP COUNTS",
                color = TechCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "BTC: ${summary.btcRegime}",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "< EMA20: ${PriceFormatter.formatPercent(summary.pctBelowEma20, 0)}",
                color = if (summary.pctBelowEma20 >= 65.0) BearishRed else TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "< EMA50: ${PriceFormatter.formatPercent(summary.pctBelowEma50, 0)}",
                color = if (summary.pctBelowEma50 >= 65.0) BearishRed else TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "A:${summary.gradeACount}  B:${summary.gradeBCount}  WATCH:${summary.watchCount}",
                color = BullishGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        if (summary.rankedBlockers.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                summary.rankedBlockers.forEach { blocker ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(DarkSurface)
                            .border(1.dp, RiskAmber.copy(alpha = 0.4f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${blocker.reason} (${blocker.count})",
                            color = RiskAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (summary.gradeACount == 0 && summary.gradeBCount == 0 && summary.closestWatchSetups.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "0 A/B setups right now — Closest WATCH triggers:",
                    color = RiskAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                summary.closestWatchSetups.forEach { watch ->
                    val distStr = watch.distanceToTriggerPercent?.let { PriceFormatter.formatPercent(it, 2) } ?: "-"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurface)
                            .clickable { onOpenSetup(watch.symbol, watch.assetType) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${watch.symbol} ${watch.side.name} • ${watch.modelTitle} ($distStr away)",
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Trigger ${PriceFormatter.formatCurrency(watch.triggerPrice)} • R:R ${PriceFormatter.formatRatio(watch.netRrTp1)}",
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                        }
                        Text(
                            text = "Arm",
                            color = TechCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .clickable { onArmAlert(watch) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayTraderScanSetupCard(
    setup: ScanSetupItem,
    onOpen: () -> Unit,
    onArmAlert: () -> Unit,
    onSaveWatch: () -> Unit
) {
    val gradeColor = when (setup.grade) {
        SetupGrade.A -> BullishGreen
        SetupGrade.B -> TechCyan
        SetupGrade.C_WATCH -> RiskAmber
        null -> BearishRed
    }
    val sideColor = if (setup.side == PositionSide.LONG) BullishGreen else BearishRed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, gradeColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable { onOpen() }
            .padding(12.dp)
            .testTag("scan_setup_card_${setup.symbol}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = setup.symbol,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(sideColor.copy(alpha = 0.16f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${setup.side.name} • ${setup.timeframe.label}",
                        color = sideColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(gradeColor.copy(alpha = 0.18f))
                    .border(1.dp, gradeColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = setup.grade?.badgeText ?: setup.verdict.label,
                    color = gradeColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${setup.modelTitle} • ${setup.familiesAligned}/7 families • ${setup.marketRegime}",
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Price ${PriceFormatter.formatCurrency(setup.currentPrice)}", color = TextSecondary, fontSize = 10.sp)
            Text(
                text = "Trig ${PriceFormatter.formatCurrency(setup.triggerPrice)}",
                color = TechCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "R:R ${PriceFormatter.formatRatio(setup.netRrTp1)}",
                color = BullishGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Dist ${setup.distanceToTriggerPercent?.let { PriceFormatter.formatPercent(it, 2) } ?: "-"}",
                color = TextMuted,
                fontSize = 10.sp
            )
        }

        if (setup.softFlags.isNotEmpty() || setup.hardVetoes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                setup.hardVetoes.forEach { v ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BearishRed.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(v.substringBefore(":"), color = BearishRed, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
                setup.softFlags.forEach { f ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(RiskAmber.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(f.label, color = RiskAmber, fontSize = 8.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (setup.needsCondition.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = setup.needsCondition,
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = onOpen,
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Text("Open", color = TechCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onArmAlert,
                enabled = setup.triggerPrice > 0.0,
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = RiskAmber, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Arm alert", color = RiskAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onSaveWatch,
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = BullishGreen, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Watch", color = BullishGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

