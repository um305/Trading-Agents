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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.domain.model.AssetType
import com.example.ui.AppScreen
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
fun FavoritesScreen(
    uiState: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var symbolInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AssetType.STOCK) }
    var filterTab by remember { mutableStateOf("ALL") } // ALL, STOCKS, CRYPTO

    val favorites = uiState.watchlist
    val filteredList = when (filterTab) {
        "STOCKS" -> favorites.filter { it.assetType == AssetType.STOCK.name }
        "CRYPTO" -> favorites.filter { it.assetType == AssetType.CRYPTO.name }
        else -> favorites
    }

    val selectedQuote = uiState.favoriteQuote ?: uiState.currentQuote
    val selectedSynthesis = uiState.favoriteSynthesis ?: uiState.agentSynthesis
    val selectedSymbol = uiState.selectedFavoriteSymbol ?: uiState.selectedSymbol

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
                        .background(RiskAmber.copy(alpha = 0.2f))
                        .border(1.dp, RiskAmber, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorites",
                        tint = RiskAmber,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Favorite Stocks & Crypto",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Watchlist with Live Data & Multi-Agent Scanning",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${favorites.size} Saved",
                    color = TechCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ADD TO FAVORITES CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "Add Stock or Crypto to Favorites",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type selector pills (Stock / Crypto)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, DarkBorder, RoundedCornerShape(10.dp))
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedType == AssetType.STOCK) BullishGreen.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { selectedType = AssetType.STOCK }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Stock",
                                color = if (selectedType == AssetType.STOCK) BullishGreen else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selectedType == AssetType.STOCK) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedType == AssetType.CRYPTO) TechCyan.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { selectedType = AssetType.CRYPTO }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Crypto",
                                color = if (selectedType == AssetType.CRYPTO) TechCyan else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selectedType == AssetType.CRYPTO) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Text Input
                    OutlinedTextField(
                        value = symbolInput,
                        onValueChange = { symbolInput = it },
                        placeholder = { Text(if (selectedType == AssetType.STOCK) "Symbol (e.g. AAPL, NVDA)" else "Crypto (e.g. BTC, SOL)", fontSize = 12.sp, color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (symbolInput.isNotBlank()) {
                                    viewModel.addToFavorites(symbolInput, assetType = selectedType)
                                    viewModel.selectFavorite(symbolInput)
                                    symbolInput = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BullishGreen,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = DarkSurfaceElevated,
                            unfocusedContainerColor = DarkSurfaceElevated
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("favorite_symbol_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (symbolInput.isNotBlank()) {
                                viewModel.addToFavorites(symbolInput, assetType = selectedType)
                                viewModel.selectFavorite(symbolInput)
                                symbolInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RiskAmber),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("add_favorite_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick-Add Popular Assets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val popularList = listOf(
                        Pair("NVDA", AssetType.STOCK),
                        Pair("AAPL", AssetType.STOCK),
                        Pair("TSLA", AssetType.STOCK),
                        Pair("BTC", AssetType.CRYPTO),
                        Pair("ETH", AssetType.CRYPTO),
                        Pair("SOL", AssetType.CRYPTO),
                        Pair("MSFT", AssetType.STOCK),
                        Pair("XRP", AssetType.CRYPTO),
                        Pair("SPY", AssetType.STOCK),
                        Pair("DOGE", AssetType.CRYPTO)
                    )

                    popularList.forEach { (sym, type) ->
                        val isAdded = favorites.any { it.symbol == sym }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isAdded) RiskAmber.copy(alpha = 0.15f) else DarkSurfaceElevated)
                                .border(1.dp, if (isAdded) RiskAmber.copy(alpha = 0.4f) else DarkBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    if (!isAdded) {
                                        viewModel.addToFavorites(sym, assetType = type)
                                    }
                                    viewModel.selectFavorite(sym)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = sym,
                                    fontSize = 11.sp,
                                    color = if (isAdded) RiskAmber else TextSecondary,
                                    fontWeight = if (isAdded) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (isAdded) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    tint = if (isAdded) RiskAmber else TextMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val stockCount = favorites.count { it.assetType == AssetType.STOCK.name }
            val cryptoCount = favorites.count { it.assetType == AssetType.CRYPTO.name }

            val tabs = listOf(
                Pair("ALL", "All (${favorites.size})"),
                Pair("STOCKS", "Stocks ($stockCount)"),
                Pair("CRYPTO", "Crypto ($cryptoCount)")
            )

            tabs.forEach { (key, label) ->
                val isSelected = filterTab == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) BullishGreen.copy(alpha = 0.2f) else DarkSurface)
                        .border(1.dp, if (isSelected) BullishGreen else DarkBorder, RoundedCornerShape(16.dp))
                        .clickable { filterTab = key }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) BullishGreen else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // FAVORITES LIST (Horizontal / Vertical Cards)
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.StarBorder, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No favorites in this list yet", color = TextSecondary, fontSize = 13.sp)
                    Text("Add a stock or crypto above to monitor its price and agent decisions", color = TextMuted, fontSize = 11.sp)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredList.forEach { item ->
                    val isCurrent = selectedSymbol == item.symbol
                    val isCrypto = item.assetType == AssetType.CRYPTO.name

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) DarkSurfaceElevated else DarkSurface)
                            .border(
                                width = if (isCurrent) 1.5.dp else 1.dp,
                                color = if (isCurrent) BullishGreen else DarkBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.selectFavorite(item.symbol) }
                            .padding(14.dp)
                            .testTag("favorite_item_${item.symbol}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isCrypto) TechCyan.copy(alpha = 0.2f) else BullishGreen.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.symbol.take(2),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isCrypto) TechCyan else BullishGreen
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.symbol,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isCrypto) TechCyan.copy(alpha = 0.15f) else BullishGreen.copy(alpha = 0.15f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = item.assetType,
                                                fontSize = 9.sp,
                                                color = if (isCrypto) TechCyan else BullishGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.name,
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { viewModel.selectFavorite(item.symbol) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCurrent) BullishGreen else DarkSurfaceElevated
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text(
                                        text = if (isCurrent) "Viewing" else "See Data",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) Color.Black else TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                IconButton(
                                    onClick = { viewModel.removeFromFavorites(item.symbol) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Remove from Favorites",
                                        tint = RiskAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // LIVE DATA INSPECTION PANEL FOR SELECTED STOCK/CRYPTO
        // "see data after selecting a stock or crypto"
        if (uiState.isLoadingFavorite) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, BullishGreen.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BullishGreen, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Fetching market quotes & scanning agents for $selectedSymbol...", color = TextSecondary, fontSize = 12.sp)
                }
            }
        } else if (selectedQuote != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, TechCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .testTag("favorite_selected_data_panel")
            ) {
                Column {
                    // Header of Selected Favorite Data
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedQuote.symbol,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(TechCyan.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = selectedQuote.assetType.name,
                                        fontSize = 10.sp,
                                        color = TechCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = selectedQuote.name,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        // Price and Change
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = com.example.domain.util.PriceFormatter.formatCurrency(selectedQuote.currentPrice),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            val isPos = selectedQuote.changePercent >= 0
                            val changeColor = if (isPos) BullishGreen else BearishRed
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isPos) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = changeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${if (isPos) "+" else ""}${String.format(Locale.US, "%.2f", selectedQuote.changePercent)}%",
                                    color = changeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Metrics Grid (Day High, Day Low, Volume, 52W)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceElevated)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("24h High", fontSize = 10.sp, color = TextMuted)
                            Text(com.example.domain.util.PriceFormatter.formatCurrency(selectedQuote.dayHigh), fontSize = 12.sp, color = BullishGreen, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("24h Low", fontSize = 10.sp, color = TextMuted)
                            Text(com.example.domain.util.PriceFormatter.formatCurrency(selectedQuote.dayLow), fontSize = 12.sp, color = BearishRed, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("24h Volume", fontSize = 10.sp, color = TextMuted)
                            Text(formatVol(selectedQuote.volume), fontSize = 12.sp, color = TechCyan, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("52w Range", fontSize = 10.sp, color = TextMuted)
                            Text("${com.example.domain.util.PriceFormatter.formatCurrency(selectedQuote.fiftyTwoWeekLow)} - ${com.example.domain.util.PriceFormatter.formatCurrency(selectedQuote.fiftyTwoWeekHigh)}", fontSize = 11.sp, color = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // UNIFIED MULTI-AGENT SCANNER RESULT FOR THIS FAVORITE
                    if (selectedSynthesis != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, AgentPurple.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Psychology, contentDescription = null, tint = AgentPurple, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("UNIFIED AGENT CONSENSUS", color = AgentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    val actColor = if (selectedSynthesis.action.isPositive) BullishGreen else BearishRed
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(actColor.copy(alpha = 0.2f))
                                            .border(1.dp, actColor, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(text = selectedSynthesis.action.label, color = actColor, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Consensus Confidence: ${selectedSynthesis.confidenceScore}%", color = TextSecondary, fontSize = 11.sp)
                                    val sign = if (selectedSynthesis.predictedPriceChangePercent >= 0) "+" else ""
                                    Text("Decision: $sign${String.format(Locale.US, "%.1f", selectedSynthesis.predictedPriceChangePercent)}%", color = BullishGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { selectedSynthesis.confidenceScore / 100f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = AgentPurple,
                                    trackColor = DarkSurface
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Recommended Buy", fontSize = 10.sp, color = TextMuted)
                                        Text(com.example.domain.util.PriceFormatter.formatCurrency(selectedSynthesis.recommendedBuyPrice), color = BullishGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text("Target Price", fontSize = 10.sp, color = TextMuted)
                                        Text(com.example.domain.util.PriceFormatter.formatCurrency(selectedSynthesis.targetPrice), color = TechCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text("Stop Loss", fontSize = 10.sp, color = TextMuted)
                                        Text(com.example.domain.util.PriceFormatter.formatCurrency(selectedSynthesis.stopLossPrice), color = BearishRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text("Risk Profile", fontSize = 10.sp, color = TextMuted)
                                        Text(selectedSynthesis.riskLevel, color = RiskAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                // NewsScanner live highlight
                                val headline = selectedSynthesis.newsScannerResult?.primaryCatalyst ?: selectedSynthesis.bloombergHeadlinesScanned.firstOrNull()
                                if (!headline.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(RiskAmber.copy(alpha = 0.1f))
                                            .border(1.dp, RiskAmber.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(imageVector = Icons.Default.ElectricBolt, contentDescription = null, tint = RiskAmber, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val newsBadge = selectedSynthesis.agentReports.find { it.role.name == "NEWS_SCANNER" }?.sourceBadge ?: "Engine"
                                            Text(
                                                text = "NewsScanner ($newsBadge): \"${headline.take(80)}...\"",
                                                color = TextPrimary,
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Action Buttons: Open in OneQuery Hub or Save Report
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.executeUnifiedQuery(selectedQuote.symbol)
                                viewModel.setScreen(AppScreen.TERMINAL)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BullishGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.ElectricBolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open in OneQuery Hub", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.saveCurrentReport() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Save Report", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontSize = 12.sp)
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

private fun formatVol(vol: Double): String {
    return when {
        vol >= 1e9 -> String.format(Locale.US, "%.2fB", vol / 1e9)
        vol >= 1e6 -> String.format(Locale.US, "%.2fM", vol / 1e6)
        vol >= 1e3 -> String.format(Locale.US, "%.1fK", vol / 1e3)
        else -> String.format(Locale.US, "%.0f", vol)
    }
}
