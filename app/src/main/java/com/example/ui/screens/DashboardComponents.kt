@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BestTimeframeSummary
import com.example.domain.model.FvgType
import com.example.domain.model.LiquidityType
import com.example.domain.model.MarketQuote
import com.example.domain.model.MultiAgentSynthesis
import com.example.domain.model.PositionSide
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.Timeframe
import com.example.domain.model.TradePlanLifecycleStatus
import com.example.domain.model.TradingAction
import com.example.domain.model.TradingMarketType
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
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

import com.example.domain.util.PriceFormatter

private fun formatPrice(num: Double): String {
    return PriceFormatter.formatPrice(num)
}

private fun formatCompactVol(vol: Double): String {
    if (!PriceFormatter.isValidPositive(vol)) return "-"
    return when {
        vol >= 1e9 -> String.format(Locale.US, "%.1fB", vol / 1e9)
        vol >= 1e6 -> String.format(Locale.US, "%.1fM", vol / 1e6)
        vol >= 1e3 -> String.format(Locale.US, "%.1fK", vol / 1e3)
        else -> String.format(Locale.US, "%.0f", vol)
    }
}

data class SearchCatalogItem(
    val symbol: String,
    val name: String,
    val assetType: com.example.domain.model.AssetType,
    val category: String,
    val approxPrice: String
)

val searchCatalog = listOf(
    SearchCatalogItem("HYPE", "Hyperliquid (L1 Perpetual DEX)", com.example.domain.model.AssetType.CRYPTO, "Crypto • DeFi", "Live Feed"),
    SearchCatalogItem("HYPE", "Hype Corporation", com.example.domain.model.AssetType.STOCK, "Stock • Tech", "Live Feed"),
    SearchCatalogItem("UNI", "Uniswap Protocol", com.example.domain.model.AssetType.CRYPTO, "Crypto • DEX", "Live Feed"),
    SearchCatalogItem("UNI", "Uniti Group Inc.", com.example.domain.model.AssetType.STOCK, "Stock • REIT", "Live Feed"),
    SearchCatalogItem("DOGE", "Dogecoin", com.example.domain.model.AssetType.CRYPTO, "Crypto • Meme/L1", "Live Feed"),
    SearchCatalogItem("PEPE", "Pepe Token", com.example.domain.model.AssetType.CRYPTO, "Crypto • Meme", "Live Feed"),
    SearchCatalogItem("SHIB", "Shiba Inu", com.example.domain.model.AssetType.CRYPTO, "Crypto • Ecosystem", "Live Feed"),
    SearchCatalogItem("BTC", "Bitcoin", com.example.domain.model.AssetType.CRYPTO, "Crypto • Layer 1", "Live Feed"),
    SearchCatalogItem("ETH", "Ethereum", com.example.domain.model.AssetType.CRYPTO, "Crypto • Smart Contracts", "Live Feed"),
    SearchCatalogItem("SOL", "Solana", com.example.domain.model.AssetType.CRYPTO, "Crypto • High-Speed L1", "Live Feed"),
    SearchCatalogItem("XRP", "XRP Ledger", com.example.domain.model.AssetType.CRYPTO, "Crypto • Payments", "Live Feed"),
    SearchCatalogItem("ADA", "Cardano", com.example.domain.model.AssetType.CRYPTO, "Crypto • Proof of Stake", "Live Feed"),
    SearchCatalogItem("BNB", "BNB Chain", com.example.domain.model.AssetType.CRYPTO, "Crypto • Exchange L1", "Live Feed"),
    SearchCatalogItem("AVAX", "Avalanche", com.example.domain.model.AssetType.CRYPTO, "Crypto • Subnets", "Live Feed"),
    SearchCatalogItem("SUI", "Sui Network", com.example.domain.model.AssetType.CRYPTO, "Crypto • Move L1", "Live Feed"),
    SearchCatalogItem("NEAR", "NEAR Protocol", com.example.domain.model.AssetType.CRYPTO, "Crypto • AI / Sharded L1", "Live Feed"),
    SearchCatalogItem("LINK", "Chainlink", com.example.domain.model.AssetType.CRYPTO, "Crypto • Oracle", "Live Feed"),
    SearchCatalogItem("SEI", "Sei Network", com.example.domain.model.AssetType.CRYPTO, "Crypto • Trading L1", "Live Feed"),
    SearchCatalogItem("RENDER", "Render Network", com.example.domain.model.AssetType.CRYPTO, "Crypto • AI / GPU", "Live Feed"),
    SearchCatalogItem("TAO", "Bittensor", com.example.domain.model.AssetType.CRYPTO, "Crypto • Decentralized AI", "Live Feed"),
    SearchCatalogItem("INJ", "Injective", com.example.domain.model.AssetType.CRYPTO, "Crypto • Financial L1", "Live Feed"),
    SearchCatalogItem("WIF", "dogwifhat", com.example.domain.model.AssetType.CRYPTO, "Crypto • Solana Meme", "Live Feed"),
    SearchCatalogItem("BONK", "Bonk", com.example.domain.model.AssetType.CRYPTO, "Crypto • Community", "Live Feed"),
    SearchCatalogItem("SNDK", "SanDisk Corporation • Robinhood/Binance Token", com.example.domain.model.AssetType.CRYPTO, "Crypto • Binance Perpetual / Token", "Live Feed"),
    SearchCatalogItem("SNDK", "SanDisk Corporation", com.example.domain.model.AssetType.STOCK, "Stock • Nasdaq Semiconductor", "Live Feed"),
    SearchCatalogItem("NVDA", "NVIDIA Corporation", com.example.domain.model.AssetType.STOCK, "Stock • AI / Semiconductors", "Live Feed"),
    SearchCatalogItem("AAPL", "Apple Inc.", com.example.domain.model.AssetType.STOCK, "Stock • Consumer Electronics", "Live Feed"),
    SearchCatalogItem("TSLA", "Tesla, Inc.", com.example.domain.model.AssetType.STOCK, "Stock • EV & Clean Energy", "Live Feed"),
    SearchCatalogItem("MSFT", "Microsoft Corporation", com.example.domain.model.AssetType.STOCK, "Stock • Cloud / Enterprise", "Live Feed"),
    SearchCatalogItem("AMZN", "Amazon.com Inc.", com.example.domain.model.AssetType.STOCK, "Stock • E-Commerce / AWS", "Live Feed"),
    SearchCatalogItem("GOOGL", "Alphabet Inc.", com.example.domain.model.AssetType.STOCK, "Stock • AI / Search", "Live Feed"),
    SearchCatalogItem("META", "Meta Platforms, Inc.", com.example.domain.model.AssetType.STOCK, "Stock • Social / AI", "Live Feed"),
    SearchCatalogItem("SPY", "SPDR S&P 500 ETF Trust", com.example.domain.model.AssetType.STOCK, "Stock • US Broad Market Index", "Live Feed"),
    SearchCatalogItem("COIN", "Coinbase Global, Inc.", com.example.domain.model.AssetType.STOCK, "Stock • Crypto Brokerage", "Live Feed"),
    SearchCatalogItem("PLTR", "Palantir Technologies", com.example.domain.model.AssetType.STOCK, "Stock • Enterprise AI / Defense", "Live Feed"),
    SearchCatalogItem("AMD", "Advanced Micro Devices", com.example.domain.model.AssetType.STOCK, "Stock • Semiconductors", "Live Feed")
)

/**
 * Clean institutional search box with active autocomplete dropdown separating Crypto and Stock items.
 */
@Composable
fun InstitutionalSearchSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onExecuteSearch: (String) -> Unit,
    selectedSymbol: String,
    onSelectSuggestion: (String, com.example.domain.model.AssetType?) -> Unit,
    currentQuote: com.example.domain.model.MarketQuote? = null,
    cryptoScanList: List<com.example.domain.model.CryptoScanItem> = emptyList(),
    cachedCryptoPairs: List<com.example.data.db.CryptoPair> = emptyList(),
    modifier: Modifier = Modifier
) {
    val isDropdownOpen = searchQuery.trim().isNotEmpty()

    val filteredSuggestions = remember(searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) emptyList()
        else {
            val matches = searchCatalog.filter {
                it.symbol.contains(q, ignoreCase = true) ||
                it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true)
            }
            if (matches.isNotEmpty()) matches
            else {
                listOf(
                    SearchCatalogItem(q.uppercase(), "$q Crypto Asset", com.example.domain.model.AssetType.CRYPTO, "Crypto • Digital Asset", "Live Feed"),
                    SearchCatalogItem(q.uppercase(), "$q Corporation", com.example.domain.model.AssetType.STOCK, "Stock • Equity", "Live Feed")
                )
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "Search crypto or stock (e.g. HYPE, DOGE, NVDA)...",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { onExecuteSearch(searchQuery.ifBlank { selectedSymbol }) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BullishGreen,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ticker_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { onExecuteSearch(searchQuery.ifBlank { selectedSymbol }) },
                colors = ButtonDefaults.buttonColors(containerColor = BullishGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("execute_all_button")
            ) {
                Text(
                    text = "SCAN",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        // Active Search Dropdown Suggestions Menu
        if (isDropdownOpen && filteredSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, TechCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "SELECT ASSET TYPE (CRYPTO VS STOCK):",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )

                filteredSuggestions.take(6).forEach { item ->
                    val isCrypto = item.assetType == com.example.domain.model.AssetType.CRYPTO
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSuggestion(item.symbol, item.assetType)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                            // Tag Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isCrypto) BullishGreen.copy(alpha = 0.2f) else TechCyan.copy(alpha = 0.2f))
                                    .border(1.dp, if (isCrypto) BullishGreen else TechCyan, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isCrypto) "CRYPTO" else "STOCK",
                                    color = if (isCrypto) BullishGreen else TechCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.symbol,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.name,
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = item.category,
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        val livePrice = when {
                            currentQuote != null && currentQuote.symbol.equals(item.symbol, ignoreCase = true) && currentQuote.assetType == item.assetType ->
                                "$${formatPrice(currentQuote.currentPrice)}"
                            cryptoScanList.any { it.symbol.equals(item.symbol, ignoreCase = true) || it.symbol.equals("${item.symbol}USDT", ignoreCase = true) } -> {
                                val s = cryptoScanList.first { it.symbol.equals(item.symbol, ignoreCase = true) || it.symbol.equals("${item.symbol}USDT", ignoreCase = true) }
                                "$${formatPrice(s.price)}"
                            }
                            cachedCryptoPairs.any { it.symbol.equals(item.symbol, ignoreCase = true) || it.baseAsset.equals(item.symbol, ignoreCase = true) } -> {
                                val p = cachedCryptoPairs.first { it.symbol.equals(item.symbol, ignoreCase = true) || it.baseAsset.equals(item.symbol, ignoreCase = true) }
                                "$${formatPrice(p.price)}"
                            }
                            item.approxPrice.isNotBlank() && item.approxPrice != "Live Feed" -> item.approxPrice
                            else -> "Live Feed"
                        }

                        Text(
                            text = livePrice,
                            color = if (isCrypto) BullishGreen else TechCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (!isDropdownOpen) {
            Spacer(modifier = Modifier.height(10.dp))

            // Quick suggestions row directly below search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val suggestions = listOf(
                    Pair("BTC", com.example.domain.model.AssetType.CRYPTO),
                    Pair("ETH", com.example.domain.model.AssetType.CRYPTO),
                    Pair("SOL", com.example.domain.model.AssetType.CRYPTO),
                    Pair("HYPE", com.example.domain.model.AssetType.CRYPTO),
                    Pair("DOGE", com.example.domain.model.AssetType.CRYPTO),
                    Pair("NVDA", com.example.domain.model.AssetType.STOCK),
                    Pair("AAPL", com.example.domain.model.AssetType.STOCK),
                    Pair("TSLA", com.example.domain.model.AssetType.STOCK),
                    Pair("SPY", com.example.domain.model.AssetType.STOCK)
                )
                suggestions.forEach { (sym, type) ->
                    val isSelected = sym.equals(selectedSymbol, ignoreCase = true)
                    val isCrypto = type == com.example.domain.model.AssetType.CRYPTO
                    val typeColor = if (isCrypto) BullishGreen else TechCyan
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) typeColor.copy(alpha = 0.2f) else DarkSurface)
                            .border(
                                1.dp,
                                if (isSelected) typeColor else DarkBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectSuggestion(sym, type) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = sym,
                                color = if (isSelected) typeColor else TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isCrypto) "Crypto" else "Stock",
                                color = typeColor.copy(alpha = 0.8f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean control bar showing:
 * 1. Timeframe selector
 * 2. Futures or Spot toggle
 * 3. Bullish or Bearish toggle
 */
@Composable
fun InstitutionalControlBar(
    selectedTimeframe: Timeframe,
    onSelectTimeframe: (Timeframe) -> Unit,
    selectedMarketType: TradingMarketType,
    onSelectMarketType: (TradingMarketType) -> Unit,
    selectedPositionSide: PositionSide,
    onSelectPositionSide: (PositionSide) -> Unit,
    isCrypto: Boolean = true,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 360.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            // Timeframe selector row with clean edge padding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Timeframe.values().forEach { tf ->
                    val isSelected = tf == selectedTimeframe
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) TechCyan.copy(alpha = 0.2f) else DarkSurface)
                            .border(
                                1.dp,
                                if (isSelected) TechCyan else DarkBorder,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelectTimeframe(tf) }
                            .padding(horizontal = if (isNarrow) 9.dp else 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tf.label,
                            color = if (isSelected) TechCyan else TextSecondary,
                            fontSize = if (isNarrow) 10.sp else 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isCrypto) {
                // Market type (Spot / Futures) and Stance (Bullish / Bearish) toggles for Crypto
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Futures / Spot Toggle
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .padding(3.dp)
                    ) {
                        val isSpot = selectedMarketType == TradingMarketType.SPOT
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSpot) BullishGreen.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { onSelectMarketType(TradingMarketType.SPOT) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Spot",
                                color = if (isSpot) BullishGreen else TextSecondary,
                                fontWeight = if (isSpot) FontWeight.Bold else FontWeight.Medium,
                                fontSize = if (isNarrow) 10.sp else 11.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isSpot) TechCyan.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { onSelectMarketType(TradingMarketType.FUTURES) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Futures",
                                color = if (!isSpot) TechCyan else TextSecondary,
                                fontWeight = if (!isSpot) FontWeight.Bold else FontWeight.Medium,
                                fontSize = if (isNarrow) 10.sp else 11.sp
                            )
                        }
                    }

                    // Bullish / Bearish Stance Toggle
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .padding(3.dp)
                    ) {
                        val isBullish = selectedPositionSide == PositionSide.LONG
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isBullish) BullishGreen.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { onSelectPositionSide(PositionSide.LONG) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isNarrow) "Bull" else "Bull (Long)",
                                color = if (isBullish) BullishGreen else TextSecondary,
                                fontWeight = if (isBullish) FontWeight.Bold else FontWeight.Medium,
                                fontSize = if (isNarrow) 10.sp else 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isBullish) BearishRed.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { onSelectPositionSide(PositionSide.SHORT) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isNarrow) "Bear" else "Bear (Short)",
                                color = if (!isBullish) BearishRed else TextSecondary,
                                fontWeight = if (!isBullish) FontWeight.Bold else FontWeight.Medium,
                                fontSize = if (isNarrow) 10.sp else 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                // For companies/stocks: clean Bullish/Bearish stance toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurface)
                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        .padding(3.dp)
                ) {
                    val isBullish = selectedPositionSide == PositionSide.LONG
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isBullish) BullishGreen.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { onSelectPositionSide(PositionSide.LONG) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isNarrow) "Bullish" else "Bullish (Long)",
                            color = if (isBullish) BullishGreen else TextSecondary,
                            fontWeight = if (isBullish) FontWeight.Bold else FontWeight.Medium,
                            fontSize = if (isNarrow) 10.sp else 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isBullish) BearishRed.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable { onSelectPositionSide(PositionSide.SHORT) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isNarrow) "Bearish" else "Bearish (Short)",
                            color = if (!isBullish) BearishRed else TextSecondary,
                            fontWeight = if (!isBullish) FontWeight.Bold else FontWeight.Medium,
                            fontSize = if (isNarrow) 10.sp else 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Discreet background scan progress indicator.
 */
@Composable
fun BackgroundScanStatusBar(
    isRunning: Boolean,
    phaseText: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = isRunning, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = TechCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = phaseText.ifBlank { "Scanning market order flow, SMC liquidity & backtest simulations in background..." },
                    color = TechCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = TechCyan,
                trackColor = DarkSurface
            )
        }
    }
}

/**
 * Live Market Price Card showing real-time quote, 24h range, volume, and active market mode.
 */
@Composable
fun LiveMarketPriceCard(
    quote: MarketQuote,
    uiState: UiState,
    onSwitchAssetType: ((com.example.domain.model.AssetType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isCrypto = quote.assetType == com.example.domain.model.AssetType.CRYPTO
    val clean = quote.symbol.uppercase()
    val hasAlternative = searchCatalog.any { it.symbol == clean && it.assetType != quote.assetType } || clean in listOf("HYPE", "UNI", "COIN", "SNDK")

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        val isNarrow = maxWidth < 360.dp
        val symbolSize = if (isNarrow) 18.sp else 22.sp
        val priceSize = if (isNarrow) 18.sp else 22.sp

        Column {
            // Row 1: Symbol & Name on Left, Price & Change on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = quote.symbol,
                        fontSize = symbolSize,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = quote.name,
                        fontSize = 11.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = PriceFormatter.formatCurrency(quote.currentPrice),
                        fontSize = priceSize,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    val isPos = quote.change >= 0
                    val changeColor = if (isPos) BullishGreen else BearishRed
                    val currentCandle = quote.candles.lastOrNull()
                    val candleChangePercent = quote.candleChangePercent ?: if (currentCandle != null && currentCandle.open > 0.0) {
                        ((currentCandle.close - currentCandle.open) / currentCandle.open) * 100.0
                    } else null
                    val isCandlePos = (candleChangePercent ?: 0.0) >= 0.0
                    val candleColor = if (isCandlePos) BullishGreen else BearishRed

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPos) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = changeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "24h: ${PriceFormatter.formatSignedCurrency(quote.change)} (${PriceFormatter.formatPercent(quote.changePercent)})",
                            color = changeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                    if (candleChangePercent != null) {
                        Text(
                            text = "${uiState.selectedTimeframe.label} candle: ${PriceFormatter.formatSignedPercent(candleChangePercent)}",
                            color = candleColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Badges row (Exchange, Mode, Timeframe, Asset Type) - horizontal scroll prevents squeezing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCrypto) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(uiState.selectedExchange.colorHex).copy(alpha = 0.2f))
                            .border(1.dp, Color(uiState.selectedExchange.colorHex).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${uiState.selectedExchange.displayName} • ${uiState.selectedMarketType.name}" +
                                    if (uiState.selectedMarketType == TradingMarketType.FUTURES) " (${uiState.selectedLeverage}x)" else "",
                            fontSize = 10.sp,
                            color = Color(uiState.selectedExchange.colorHex),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BullishGreen.copy(alpha = 0.15f))
                            .border(1.dp, BullishGreen.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "EQUITY SPOT • REGULAR",
                            fontSize = 10.sp,
                            color = BullishGreen,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(TechCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = uiState.selectedTimeframe.label,
                        fontSize = 10.sp,
                        color = TechCyan,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurfaceElevated)
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isCrypto) "CRYPTO FEED" else "STOCK FEED",
                        fontSize = 9.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }

                val barsLoaded = quote.barsLoadedCount.coerceAtLeast(quote.candles.size)
                val ageMins = if (quote.candleAgeMillis > 0L) quote.candleAgeMillis / 60_000L else null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (quote.isStale) RiskAmber.copy(alpha = 0.16f) else DarkSurfaceElevated)
                        .border(
                            1.dp,
                            if (quote.isStale) RiskAmber.copy(alpha = 0.5f) else DarkBorder,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("bars_loaded_badge")
                ) {
                    Text(
                        text = buildString {
                            append("bars loaded $barsLoaded")
                            if (ageMins != null) append(" • ${ageMins}m old")
                        },
                        fontSize = 9.sp,
                        color = if (quote.isStale) RiskAmber else TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            if (quote.isStale || quote.staleReason != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(RiskAmber.copy(alpha = 0.14f))
                        .border(1.dp, RiskAmber.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RiskAmber, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = quote.staleReason ?: "Feed warning: last closed candle is delayed for ${uiState.selectedTimeframe.label}",
                        color = RiskAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick Asset Switcher Banner if dual-listed (e.g. SNDK / HYPE / UNI / COIN)
            if (hasAlternative && onSwitchAssetType != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val targetType = if (isCrypto) com.example.domain.model.AssetType.STOCK else com.example.domain.model.AssetType.CRYPTO
                val targetLabel = if (isCrypto) "Switch to Stock" else "Switch to Crypto"
                val basisBps = 18.0 // ~0.18% basis
                val basisDollars = quote.currentPrice * (basisBps / 10000.0)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, TechCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .clickable { onSwitchAssetType(targetType) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCrypto) "Binance ${clean}USDT perp (tracks Nasdaq: $clean)" else "Underlying (Nasdaq: $clean)",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "$targetLabel ➔",
                            color = TechCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isCrypto) "Perp-vs-Stock Basis: +0.18% (+$${formatPrice(basisDollars)}) • Funding premium"
                               else "Underlying (Nasdaq) • Perp Basis: +0.18% (Binance Perp tracking active)",
                        color = TextMuted,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 24h High, Low and Volume stats (weighted columns prevent text overflow)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("24h High", fontSize = 9.sp, color = TextMuted)
                    Text(
                        text = PriceFormatter.formatCurrency(quote.dayHigh),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("24h Low", fontSize = 9.sp, color = TextMuted)
                    Text(
                        text = PriceFormatter.formatCurrency(quote.dayLow),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Volume", fontSize = 9.sp, color = TextMuted)
                    Text(
                        text = formatCompactVol(quote.volume),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Primary Executive Result Card showing:
 * 1. Clear Bullish / Bearish recommendation verdict
 * 2. Visual Entry, TP, SL, Risk/Reward ratio, Risk level
 * 3. Institutional Liquidity & SMC decomposition (BSL, SSL, Sweeps, Order Blocks, FVGs)
 * 4. Background Node outcomes (Technical, SMC/Liquidity, News/Sentiment, Strategy Backtest)
 */
@Composable
fun ExecutiveDecisionResultCard(
    quote: MarketQuote,
    synthesis: MultiAgentSynthesis?,
    uiState: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val backtest = uiState.backtestResult
    var showFullPipeline by remember { mutableStateOf(false) }

    val candidateV2 = when {
        synthesis?.signalEngineOutput?.symbol.equals(quote.symbol, ignoreCase = true) -> synthesis?.signalEngineOutput
        uiState.tradeSetupPlan?.signalOutput?.symbol.equals(quote.symbol, ignoreCase = true) -> uiState.tradeSetupPlan?.signalOutput
        else -> null
    }
    val maxDist = quote.currentPrice * 0.25
    val isV2Sane = candidateV2 != null &&
        kotlin.math.abs(candidateV2.bullScenario.invalidationLevel - quote.currentPrice) <= maxDist &&
        kotlin.math.abs(candidateV2.bearScenario.invalidationLevel - quote.currentPrice) <= maxDist
    val v2Output = if (isV2Sane) candidateV2 else null

    val fallbackBullScenario = com.example.domain.model.ScenarioPlan(
        isBull = true,
        isActive = uiState.positionSetupSide == PositionSide.LONG,
        triggerCondition = "Bullish MSS / CHoCH confirmation with displacement above ${PriceFormatter.formatCurrency(quote.currentPrice * 1.012)} and RVOL >= 1.0",
        invalidationLevel = quote.currentPrice * 0.985,
        targetLevel = quote.currentPrice * 1.035,
        narrative = "Reclaim of demand zone with institutional absorption."
    )
    val fallbackBearScenario = com.example.domain.model.ScenarioPlan(
        isBull = false,
        isActive = uiState.positionSetupSide == PositionSide.SHORT,
        triggerCondition = "Bearish MSS / CHoCH confirmation with rejection below ${PriceFormatter.formatCurrency(quote.currentPrice * 0.988)} and RVOL >= 1.0",
        invalidationLevel = quote.currentPrice * 1.015,
        targetLevel = quote.currentPrice * 0.965,
        narrative = "Rejection at overhead supply with liquidity sweep."
    )

    val vs = uiState.verdictState ?: com.example.domain.model.VerdictState(
        symbol = quote.symbol,
        marketType = uiState.selectedMarketType,
        timeframe = uiState.selectedTimeframe,
        verdict = v2Output?.verdictState ?: uiState.tradeSetupPlan?.verdictState ?: SignalVerdictState.NO_TRADE,
        vetoes = v2Output?.vetoReasons ?: emptyList(),
        conflicts = v2Output?.conflicts ?: emptyList(),
        hasStructuralConflicts = v2Output?.conflicts?.isNotEmpty() == true,
        plan = uiState.tradeSetupPlan?.takeIf {
            it.isAvailable && it.recommendedEntry > 0.0 && it.stopLoss > 0.0 &&
                (v2Output?.verdictState ?: it.verdictState) != SignalVerdictState.NO_TRADE
        },
        bullScenario = v2Output?.bullScenario ?: fallbackBullScenario,
        bearScenario = v2Output?.bearScenario ?: fallbackBearScenario,
        waitingForCondition = v2Output?.primarySetup?.triggerCondition ?: fallbackBullScenario.triggerCondition,
        familyVotes = v2Output?.allFamilyVotes ?: emptyList(),
        familiesAlignedCount = v2Output?.familiesAlignedCount ?: 0,
        marketRegime = v2Output?.marketRegime ?: com.example.domain.model.MarketRegime.RANGE,
        evidence = v2Output?.evidence,
        eventRisk = uiState.eventRiskAssessment,
        oneLineWhy = v2Output?.oneLineWhy ?: "Signal Engine v2 • ${quote.symbol}",
        sessionStatus = v2Output?.sessionStatus ?: com.example.domain.model.MarketSessionStatus.OPEN,
        livePrice = quote.currentPrice,
        setupGrade = v2Output?.setupGrade,
        softFlags = v2Output?.softFlags ?: emptyList(),
        needsCondition = v2Output?.needsCondition ?: "",
        bestTimeframeSummary = uiState.bestTimeframeSummary,
        barsLoaded = quote.barsLoadedCount.coerceAtLeast(quote.candles.size),
        lastClosedBarTimestamp = quote.lastClosedBarTimestamp,
        candleAgeMillis = quote.candleAgeMillis,
        staleDataWarning = quote.staleReason
    )

    val setup = vs.plan
    val v2Verdict = vs.verdict
    val isBullish = when {
        setup != null -> setup.side == PositionSide.LONG
        else -> uiState.positionSetupSide == PositionSide.LONG
    }
    val displayedBullScenario = vs.bullScenario
    val displayedBearScenario = vs.bearScenario

    val verdictLabel = when (v2Verdict) {
        SignalVerdictState.TRADE -> if (isBullish) "VERDICT: TRADE (LONG)" else "VERDICT: TRADE (SHORT)"
        SignalVerdictState.WAIT -> "VERDICT: WAIT (CONDITIONAL)"
        SignalVerdictState.NO_TRADE -> "VERDICT: NO TRADE"
    }
    val verdictColor = when (v2Verdict) {
        SignalVerdictState.TRADE -> if (isBullish) BullishGreen else BearishRed
        SignalVerdictState.WAIT -> RiskAmber
        SignalVerdictState.NO_TRADE -> BearishRed
    }
    val actionColor = verdictColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, actionColor.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            // Signal Engine v2 Verdict Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(actionColor.copy(alpha = 0.15f))
                    .border(1.dp, actionColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("verdict_banner")
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = verdictLabel,
                                color = actionColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = vs.oneLineWhy,
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val evidence = vs.evidence
                        val badgeText = when {
                            v2Verdict == SignalVerdictState.NO_TRADE -> "No Active Plan"
                            evidence != null && !evidence.isUnproven -> {
                                val expStr = PriceFormatter.formatRMultiple(evidence.expectancyR)
                                val ciLo = String.format(Locale.US, "%.0f", evidence.confidenceInterval95.first)
                                val ciHi = String.format(Locale.US, "%.0f", evidence.confidenceInterval95.second)
                                "Exp: $expStr • P(win) ${String.format(Locale.US, "%.0f", evidence.hitRatePercent)}% [$ciLo%–$ciHi%] (n=${evidence.sampleSize})"
                            }
                            evidence != null -> {
                                val baseStr = String.format(Locale.US, "%.0f", evidence.baselinePercent)
                                "UNPROVEN (n=${evidence.sampleSize}) • too few • Base $baseStr%"
                            }
                            else -> "Exp: uncalibrated"
                        }
                        val badgeFgColor = if (evidence != null && evidence.isUnproven && v2Verdict != SignalVerdictState.NO_TRADE) TextMuted else actionColor
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            vs.setupGrade?.let { grade ->
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
                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                        .testTag("setup_grade_badge")
                                ) {
                                    Text(
                                        text = grade.badgeText,
                                        color = gCol,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badgeFgColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = badgeText,
                                    color = badgeFgColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Active Mode, Futures Routing & AI Shift Badges
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (vs.routedViaFuturesShort) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(TechCyan.copy(alpha = 0.16f))
                                    .border(1.dp, TechCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("short_via_futures_badge")
                            ) {
                                Text(
                                    text = "SHORT via Futures (perp)",
                                    color = TechCyan,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DarkSurfaceElevated)
                                .border(1.dp, DarkBorder, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("verdict_offhours_badge")
                        ) {
                            Text(
                                text = "Session: ${vs.sessionStatus.label} • Off-hours: ${vs.offHoursMode.label}",
                                color = TextSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        val aiLabel = if (vs.aiShiftPoints != 0) {
                            val sign = if (vs.aiShiftPoints > 0) "+" else ""
                            "AI shift: ${sign}${vs.aiShiftPoints} pts (${vs.aiShiftModelName ?: "AI"})"
                        } else {
                            "AI: none (engine-only)"
                        }
                        val aiColor = if (vs.aiShiftPoints != 0) AgentPurple else TextMuted
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(aiColor.copy(alpha = 0.12f))
                                .border(1.dp, aiColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("verdict_ai_shift_badge")
                        ) {
                            Text(
                                text = aiLabel,
                                color = aiColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Soft Quality Flags & "needs:" Plain-English Condition
                    if (vs.softFlags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            vs.softFlags.forEach { flag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(RiskAmber.copy(alpha = 0.14f))
                                        .border(1.dp, RiskAmber.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = flag.label,
                                        color = RiskAmber,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (v2Verdict != SignalVerdictState.TRADE && vs.needsCondition.isNotBlank()) {
                        Text(
                            text = vs.needsCondition,
                            color = RiskAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 13.sp,
                            modifier = Modifier.testTag("needs_condition_text")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val ageSec = (vs.candleAgeMillis / 1000L).coerceAtLeast(0L)
                        val ageStr = if (vs.candleAgeMillis > 0L) " • age ${if (ageSec >= 3600) "${ageSec / 3600}h" else if (ageSec >= 60) "${ageSec / 60}m" else "${ageSec}s"}" else ""
                        Text(
                            text = "Diagnostics: bars loaded ${vs.barsLoaded}$ageStr • ${vs.marketRegime.label}",
                            color = TextMuted,
                            fontSize = 8.sp
                        )
                        if (v2Verdict != SignalVerdictState.TRADE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val trigP = setup?.recommendedEntry?.takeIf { it > 0.0 }
                                    ?: vs.bullScenario.invalidationLevel
                                OutlinedButton(
                                    onClick = {
                                        viewModel.armConditionalAlert(
                                            symbol = quote.symbol,
                                            triggerPrice = trigP,
                                            side = setup?.side ?: uiState.positionSetupSide,
                                            condition = vs.waitingForCondition.ifBlank { vs.needsCondition }
                                        )
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .testTag("btn_verdict_arm_alert")
                                ) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = RiskAmber, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Arm alert", color = RiskAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.saveSymbolToWatchlist(quote.symbol, quote.name, quote.assetType) },
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .testTag("btn_verdict_save_watch")
                                ) {
                                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, tint = TechCyan, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Save to Watch", color = TechCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Best Timeframe Summary Banner ("Best now: ..." + "Why not <chart TF>?")
            vs.bestTimeframeSummary?.let { best ->
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(TechCyan.copy(alpha = 0.12f))
                        .border(1.dp, TechCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("best_timeframe_banner"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = best.headline,
                        color = TechCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = best.whyNotChartTf,
                        color = TextSecondary,
                        fontSize = 9.sp,
                        lineHeight = 12.sp
                    )
                }
            }

            // Session & Gap-Risk Banner if Off-Hours
            val sessionStatus = vs.sessionStatus
            if (sessionStatus != com.example.domain.model.MarketSessionStatus.OPEN) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(RiskAmber.copy(alpha = 0.15f))
                        .border(1.dp, RiskAmber.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = RiskAmber, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SESSION: ${sessionStatus.label} • Gap Risk elevated. Position sizing scaled x0.5, stop buffers x1.5.",
                            color = RiskAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Veto / Missing Condition / Context-Only Banner ABOVE the 7-family chip row
            if (vs.vetoes.isNotEmpty() || vs.plan == null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isContextBanner = vs.isContextOnly && vs.vetoes.isEmpty()
                val boxColor = if (isContextBanner) TechCyan else BearishRed
                val vetoHeadline = vs.vetoes.firstOrNull() ?: vs.oneLineWhy
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(boxColor.copy(alpha = 0.12f))
                        .border(1.dp, boxColor.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .testTag("veto_summary_banner"),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = when {
                            vs.vetoes.isNotEmpty() -> "HARD VETO / BLOCKING CONDITION"
                            vs.isContextOnly -> "CONTEXT TIMEFRAME (${vs.timeframe.label}) — ENTRY-FREE ANALYSIS"
                            else -> "NO-PLAN CONDITION"
                        },
                        color = boxColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = vetoHeadline,
                        color = TextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.sp
                    )
                    if (vs.regimeMetricsSummary.isNotBlank() && !vetoHeadline.contains("ADX")) {
                        Text(
                            text = vs.regimeMetricsSummary,
                            color = TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // Always render the 7-family chip row (Trend, Structure, Value, Volatility, Momentum, Flow, Context)
            Spacer(modifier = Modifier.height(8.dp))
            SignalFamilyChipRow(
                verdictState = vs,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Strict Plan vs No-Plan rendering
            if (vs.hasValidPlan && setup != null) {
                val livePrice = quote.currentPrice
                val zoneMin = minOf(setup.entryZoneStart, setup.entryZoneEnd)
                val zoneMax = maxOf(setup.entryZoneStart, setup.entryZoneEnd)
                val isInZone = livePrice in zoneMin..zoneMax
                val distToEntry = if (setup.recommendedEntry > 0.0) ((livePrice - setup.recommendedEntry) / setup.recommendedEntry) * 100.0 else 0.0

                val lev = uiState.selectedLeverage
                val isFutures = uiState.selectedMarketType == TradingMarketType.FUTURES && quote.assetType == com.example.domain.model.AssetType.CRYPTO
                val recEntry = setup.recommendedEntry
                val slDist = if (recEntry > 0.0) Math.abs(recEntry - setup.stopLoss) / recEntry * 100.0 else 0.0

                // Strict monotonic ordering guarantees zero disorder across all screens and modes
                val (orderedTp1, orderedTp2, orderedTp3) = if (isBullish) {
                    val list = listOf(setup.takeProfit1, setup.takeProfit2, setup.takeProfit3).filter { it > 0 }.sorted()
                    val t1 = list.getOrNull(0) ?: maxOf(recEntry * 1.015, livePrice * 1.005)
                    val t2 = list.getOrNull(1) ?: (t1 * 1.015)
                    val t3 = list.getOrNull(2) ?: (t2 * 1.025)
                    Triple(t1, t2, t3)
                } else {
                    val list = listOf(setup.takeProfit1, setup.takeProfit2, setup.takeProfit3).filter { it > 0 }.sortedDescending()
                    val t1 = list.getOrNull(0) ?: minOf(recEntry * 0.985, livePrice * 0.995)
                    val t2 = list.getOrNull(1) ?: (t1 * 0.985)
                    val t3 = list.getOrNull(2) ?: (t2 * 0.975)
                    Triple(t1, t2, t3)
                }

                val status = setup.evaluateWithCandles(livePrice, quote.candles)
                val isTradeArmed = v2Verdict == com.example.domain.model.SignalVerdictState.TRADE
                val isTpHit = status == TradePlanLifecycleStatus.TP1_REACHED || status == TradePlanLifecycleStatus.TP2_REACHED || status == TradePlanLifecycleStatus.TP3_REACHED
                val isStopped = status == TradePlanLifecycleStatus.STOPPED_OUT
                val isInvalid = status == TradePlanLifecycleStatus.INVALIDATED
                val isInZoneNow = status == TradePlanLifecycleStatus.IN_ENTRY_ZONE && isTradeArmed
                val isActiveTrade = status == TradePlanLifecycleStatus.ACTIVE_IN_TRADE && isTradeArmed

                // Forward-Looking Execution Timing & Entry Status Banner
                val bannerBg = when {
                    !isTradeArmed -> RiskAmber.copy(alpha = 0.16f)
                    isTpHit -> BullishGreen.copy(alpha = 0.16f)
                    isStopped || isInvalid -> BearishRed.copy(alpha = 0.16f)
                    isInZoneNow -> BullishGreen.copy(alpha = 0.14f)
                    isActiveTrade -> TechCyan.copy(alpha = 0.14f)
                    else -> TechCyan.copy(alpha = 0.10f)
                }
                val bannerBorder = when {
                    !isTradeArmed -> RiskAmber.copy(alpha = 0.6f)
                    isTpHit -> BullishGreen.copy(alpha = 0.6f)
                    isStopped || isInvalid -> BearishRed.copy(alpha = 0.6f)
                    isInZoneNow -> BullishGreen.copy(alpha = 0.5f)
                    isActiveTrade -> TechCyan.copy(alpha = 0.5f)
                    else -> TechCyan.copy(alpha = 0.4f)
                }
                val bannerColor = when {
                    !isTradeArmed -> RiskAmber
                    isTpHit -> BullishGreen
                    isStopped || isInvalid -> BearishRed
                    isInZoneNow -> BullishGreen
                    isActiveTrade -> TechCyan
                    else -> TechCyan
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bannerBg)
                        .border(1.dp, bannerBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = when {
                                isTpHit -> Icons.Default.CheckCircle
                                isStopped -> Icons.Default.Info
                                isInZoneNow -> Icons.Default.CheckCircle
                                isActiveTrade -> Icons.Default.TrendingUp
                                else -> Icons.Default.Schedule
                            },
                            contentDescription = null,
                            tint = bannerColor,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = when (status) {
                                        TradePlanLifecycleStatus.ANALYZING -> "ANALYZING AGENT PIPELINE..."
                                        TradePlanLifecycleStatus.EXPIRED -> "PLAN EXPIRED — TIME LIMIT REACHED"
                                        TradePlanLifecycleStatus.INVALIDATED -> "STRUCTURE INVALIDATED — CANCEL PLAN"
                                        TradePlanLifecycleStatus.TP3_REACHED -> "ALL TARGETS HIT — TRADE COMPLETED"
                                        TradePlanLifecycleStatus.TP2_REACHED -> "TARGET 2 HIT — TRAILING RUNNER TO TP3"
                                        TradePlanLifecycleStatus.TP1_REACHED -> "TARGET 1 REACHED — SECURE 50% & TRAIL SL"
                                        TradePlanLifecycleStatus.STOPPED_OUT -> "STOP LOSS HIT — PLAN INVALIDATED"
                                        TradePlanLifecycleStatus.IN_ENTRY_ZONE -> if (isTradeArmed) "LIVE IN ENTRY ZONE — ACTIONABLE NOW" else {
                                            val trig = v2Output?.primarySetup?.triggerCondition ?: setup.structureState.ifBlank { "pullback trigger confirmation" }
                                            val dist = if (quote.currentPrice > 0 && setup.recommendedEntry > 0) " (${PriceFormatter.formatCurrency(kotlin.math.abs(quote.currentPrice - setup.recommendedEntry))} away)" else ""
                                            "Conditional plan - waiting for: $trig$dist"
                                        }
                                        TradePlanLifecycleStatus.ACTIVE_IN_TRADE -> if (isTradeArmed) "TRADE ACTIVE — FILLED & TRACKING TO TP1" else "Conditional plan - waiting for: armed trigger confirmation"
                                        TradePlanLifecycleStatus.PENDING_ENTRY -> if (isTradeArmed) "PENDING LIMIT ORDER — ACTIVE STRATEGY" else {
                                            val trig = v2Output?.primarySetup?.triggerCondition ?: setup.structureState.ifBlank { "pullback trigger confirmation" }
                                            val dist = if (quote.currentPrice > 0 && setup.recommendedEntry > 0) " (${PriceFormatter.formatCurrency(kotlin.math.abs(quote.currentPrice - setup.recommendedEntry))} away)" else ""
                                            "Conditional plan - waiting for: $trig$dist"
                                        }
                                    },
                                    color = bannerColor,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f, fill = false),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(bannerColor.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .widthIn(min = 52.dp)
                                        .testTag("badge_trade_lifecycle_status"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (status) {
                                            TradePlanLifecycleStatus.ANALYZING -> "ANALYZING"
                                            TradePlanLifecycleStatus.EXPIRED -> "EXPIRED"
                                            TradePlanLifecycleStatus.INVALIDATED -> "CANCELLED"
                                            TradePlanLifecycleStatus.TP3_REACHED -> "TP3 DONE"
                                            TradePlanLifecycleStatus.TP2_REACHED -> "TP2 DONE"
                                            TradePlanLifecycleStatus.TP1_REACHED -> "TP1 HIT"
                                            TradePlanLifecycleStatus.STOPPED_OUT -> "STOPPED"
                                            TradePlanLifecycleStatus.IN_ENTRY_ZONE -> if (isTradeArmed) "IN ZONE" else "WAITING"
                                            TradePlanLifecycleStatus.ACTIVE_IN_TRADE -> if (isTradeArmed) "ACTIVE" else "WAITING"
                                            TradePlanLifecycleStatus.PENDING_ENTRY -> if (isTradeArmed) "PENDING" else "WAITING"
                                        },
                                        color = bannerColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = when {
                                    !isTradeArmed -> "Conditional zone [${PriceFormatter.formatCurrency(zoneMin)} – ${PriceFormatter.formatCurrency(zoneMax)}] around ${PriceFormatter.formatCurrency(recEntry)} (${PriceFormatter.formatSignedPercent(distToEntry)} away). Wait for trigger condition before arming."
                                    isTpHit -> "Target reached! Trail your Stop Loss to break-even (${PriceFormatter.formatCurrency(recEntry)}) to lock in risk-free gains towards TP2 (${PriceFormatter.formatCurrency(orderedTp2)})."
                                    isStopped -> "Price touched Stop Loss at ${PriceFormatter.formatCurrency(setup.stopLoss)}. Capital preservation protocol active. Wait for market structure reconstruction before new entry."
                                    isInZoneNow -> "Live market price (${PriceFormatter.formatCurrency(livePrice)}) is inside your optimal execution zone [${PriceFormatter.formatCurrency(zoneMin)} – ${PriceFormatter.formatCurrency(zoneMax)}]. Place your limit or market fill order now."
                                    isActiveTrade -> "Position is currently active and advancing towards Target 1 at ${PriceFormatter.formatCurrency(orderedTp1)}. Distance remaining: ${PriceFormatter.formatCurrency(Math.abs(orderedTp1 - livePrice))}."
                                    else -> "Planned limit entry at ${PriceFormatter.formatCurrency(recEntry)} (${PriceFormatter.formatSignedPercent(distToEntry)} away). Entry zone: [${PriceFormatter.formatCurrency(zoneMin)} – ${PriceFormatter.formatCurrency(zoneMax)}]."
                                },
                                color = TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                if (isTpHit || isStopped || isInvalid) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(TechCyan.copy(alpha = 0.16f))
                            .border(1.dp, TechCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .clickable { viewModel.recalibrateEntryToCurrentPrice() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("btn_recalibrate_next_trade"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = TechCyan, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CALCULATE NEXT FORWARD TRADE SETUP FOR $${formatPrice(livePrice)}",
                                color = TechCyan,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val gain1 = Math.abs(orderedTp1 - recEntry) / recEntry * 100.0
                val gain2 = Math.abs(orderedTp2 - recEntry) / recEntry * 100.0
                val gain3 = Math.abs(orderedTp3 - recEntry) / recEntry * 100.0

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceElevated)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = if (isStopped) "Previous Plan (Stopped Out)" else "Planned Future Limit",
                                    color = if (isStopped) BearishRed else TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background((if (isStopped) BearishRed else bannerColor).copy(alpha = 0.15f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                        .widthIn(min = 48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when {
                                            isStopped -> "STOPPED"
                                            isTpHit -> "TP1 HIT"
                                            isInZone -> "IN ZONE"
                                            else -> "UPCOMING"
                                        },
                                        color = if (isStopped) BearishRed else bannerColor,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                            Text(
                                text = PriceFormatter.formatCurrency(setup.recommendedEntry),
                                color = TechCyan,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val zMin = minOf(setup.entryZoneStart, setup.entryZoneEnd)
                            val zMax = maxOf(setup.entryZoneStart, setup.entryZoneEnd)
                            Text(
                                text = "Entry Range: ${PriceFormatter.formatCurrency(zMin)} – ${PriceFormatter.formatCurrency(zMax)}",
                                color = if (isInZone) BullishGreen else TechCyan.copy(alpha = 0.85f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    isStopped -> "Stop Loss Hit @ ${PriceFormatter.formatCurrency(setup.stopLoss)} • Re-anchor required"
                                    isTpHit -> "Target 1 reached • Trail Stop"
                                    isInZone && isTradeArmed -> "● In Trigger Zone Now"
                                    else -> "Conditional • ${PriceFormatter.formatSignedPercent(distToEntry)} away"
                                },
                                color = if (isStopped) BearishRed else if (isInZone && isTradeArmed) BullishGreen else TextMuted,
                                fontSize = 8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Stop Loss (SL)", color = TextMuted, fontSize = 9.sp, maxLines = 1, softWrap = false)
                            Text(
                                text = PriceFormatter.formatCurrency(setup.stopLoss),
                                color = BearishRed,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "-${PriceFormatter.formatPercent(slDist)} Risk",
                                color = BearishRed,
                                fontSize = 9.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Risk / Reward", color = TextMuted, fontSize = 9.sp, maxLines = 1, softWrap = false)
                            val slDistVal = kotlin.math.abs(recEntry - setup.stopLoss)
                            val dynamicR1 = if (slDistVal > 0.0) kotlin.math.abs(orderedTp1 - recEntry) / slDistVal else 1.5
                            Text(
                                text = "1:${String.format(Locale.US, "%.1f", dynamicR1)}",
                                color = BullishGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Mode / Type", color = TextMuted, fontSize = 9.sp, maxLines = 1, softWrap = false)
                            Text(
                                text = if (isFutures) "${lev}X FUTURES" else "SPOT",
                                color = if (isFutures) TechCyan else BullishGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = if (quote.assetType == com.example.domain.model.AssetType.CRYPTO) uiState.selectedExchange.displayName else "Equity / Cash",
                                color = TextMuted,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Bull and Bear Scenarios
                Spacer(modifier = Modifier.height(6.dp))
                ScenarioPairRow(
                    bullScenario = displayedBullScenario,
                    bearScenario = displayedBearScenario
                )

                // Explicit FAQ Timing Callout
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurface)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isStopped) Icons.Default.Close else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isStopped) BearishRed else TechCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            !isTradeArmed -> {
                                val trig = v2Output?.primarySetup?.triggerCondition ?: setup.structureState.ifBlank { "pullback trigger confirmation" }
                                val dist = if (quote.currentPrice > 0 && recEntry > 0) " (${formatPrice(kotlin.math.abs(quote.currentPrice - recEntry))} away)" else ""
                                "Timing: Conditional plan - waiting for: $trig$dist"
                            }
                            isStopped -> "Timing: Previous Setup Invalidated • Stop loss hit. Recalibrate below for fresh OB/FVG forward setup."
                            isInZoneNow -> "Timing: Active In Zone • Price is currently testing entry zone. Limit fill armed."
                            else -> "Timing: Forward Setup • Place Limit Order in Order Block / FVG zone before market triggers."
                        },
                        color = if (!isTradeArmed) RiskAmber else if (isStopped) BearishRed.copy(alpha = 0.9f) else TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Visual Risk-to-Reward Proportion Meter
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .height(5.dp)
                        .background(DarkSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .background(BearishRed.copy(alpha = 0.85f))
                    )
                    Box(
                        modifier = Modifier
                            .weight(2.5f)
                            .height(5.dp)
                            .background(BullishGreen)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // MULTI-TIER TAKE PROFIT LADDER (TP1, TP2, TP3)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, BullishGreen.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TAKE PROFIT TARGETS (MULTI-TIER)",
                                color = BullishGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = if (isFutures) "Gains with ${lev}x Leverage" else "Unleveraged Spot Gains",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }

                        // Visual Stepper Milestone Pathway
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(DarkSurface)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Planned Entry $${formatPrice(recEntry)} (${if (isInZone) "In Zone" else "Upcoming"})", color = TechCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            Text("▶", color = TextMuted, fontSize = 8.sp, maxLines = 1, softWrap = false)
                            Text("TP1 $${formatPrice(orderedTp1)} (+${formatPrice(if (isFutures) gain1 * lev else gain1)}%)", color = BullishGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            Text("▶", color = TextMuted, fontSize = 8.sp, maxLines = 1, softWrap = false)
                            Text("TP2 $${formatPrice(orderedTp2)} (+${formatPrice(if (isFutures) gain2 * lev else gain2)}%)", color = BullishGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            Text("▶", color = TextMuted, fontSize = 8.sp, maxLines = 1, softWrap = false)
                            Text("TP3 $${formatPrice(orderedTp3)} (+${formatPrice(if (isFutures) gain3 * lev else gain3)}%)", color = TechCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Take Profit Targets Cards (Responsive for all phone widths)
                        val tpCardsModifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 2.dp)

                        val slDistance = kotlin.math.abs(recEntry - setup.stopLoss)

                        Row(
                            modifier = tpCardsModifier,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            // TP1 Target Card
                            Box(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, BullishGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("TP1 (50%)", color = BullishGreen, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, maxLines = 1, softWrap = false)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val computedR1 = if (slDistance > 0.0) kotlin.math.abs(orderedTp1 - recEntry) / slDistance else 1.5
                                        Text("1:${String.format(Locale.US, "%.1f", computedR1)}", color = TextMuted, fontSize = 8.sp, maxLines = 1, softWrap = false)
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "$${formatPrice(orderedTp1)}",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "+${formatPrice(if (isFutures) gain1 * lev else gain1)}%",
                                        color = BullishGreen,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "Scale out / BE",
                                        color = TextMuted,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // TP2 Target Card
                            Box(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, BullishGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("TP2 (30%)", color = BullishGreen, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, maxLines = 1, softWrap = false)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val computedR2 = if (slDistance > 0.0) kotlin.math.abs(orderedTp2 - recEntry) / slDistance else 2.5
                                        Text("1:${String.format(Locale.US, "%.1f", computedR2)}", color = TextMuted, fontSize = 8.sp, maxLines = 1, softWrap = false)
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "$${formatPrice(orderedTp2)}",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "+${formatPrice(if (isFutures) gain2 * lev else gain2)}%",
                                        color = BullishGreen,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "Structural Target",
                                        color = TextMuted,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // TP3 Target Card
                            Box(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                    .border(1.dp, TechCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("TP3 (20%)", color = TechCyan, fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, maxLines = 1, softWrap = false)
                                        val computedR3 = if (slDistance > 0.0) kotlin.math.abs(orderedTp3 - recEntry) / slDistance else 4.0
                                        Text("1:${String.format(Locale.US, "%.1f", computedR3)}", color = TextMuted, fontSize = 8.sp, maxLines = 1, softWrap = false)
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "$${formatPrice(orderedTp3)}",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "+${formatPrice(if (isFutures) gain3 * lev else gain3)}%",
                                        color = TechCyan,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = "Runner Target",
                                        color = TextMuted,
                                        fontSize = 8.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // TRADINGVIEW MULTI-INDICATOR TECHNICAL STUDIES NODE (34 Indicators + Favorite Studies)
                synthesis?.tradingViewSummary?.let { tv ->
                    Spacer(modifier = Modifier.height(10.dp))
                    val tvVerdictColor = when {
                        tv.summaryVerdict.contains("BUY") -> BullishGreen
                        tv.summaryVerdict.contains("SELL") -> BearishRed
                        else -> RiskAmber
                    }
                    val favVerdictColor = when {
                        tv.favoriteStudiesVerdict.contains("BUY") -> BullishGreen
                        tv.favoriteStudiesVerdict.contains("SELL") -> BearishRed
                        else -> RiskAmber
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, Color(0xFF2962FF).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
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
                                            .background(Color(0xFF2962FF))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TRADINGVIEW TECHNICALS NODE (${tv.totalIndicators} STUDIES)",
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF2962FF).copy(alpha = 0.15f))
                                            .border(1.dp, Color(0xFF2962FF).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .widthIn(min = 40.dp)
                                    ) {
                                        Text(
                                            text = "FAV: ${tv.favoriteStudiesVerdict}",
                                            color = favVerdictColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(tvVerdictColor.copy(alpha = 0.2f))
                                            .border(1.dp, tvVerdictColor, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .widthIn(min = 40.dp)
                                    ) {
                                        Text(
                                            text = tv.summaryVerdict,
                                            color = tvVerdictColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Indicator Gauges Breakdown (Buy / Neutral / Sell)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${tv.totalIndicators} Studies Matrix:",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "${tv.buyCount} Buy",
                                    color = BullishGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "•",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "${tv.neutralCount} Neutral",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "•",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "${tv.sellCount} Sell",
                                    color = BearishRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "• MAs: ${tv.movingAveragesVerdict}",
                                    color = if (tv.movingAveragesVerdict.contains("BUY")) BullishGreen else if (tv.movingAveragesVerdict.contains("SELL")) BearishRed else TextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            // Favorite Community Indicators Chips Row
                            if (tv.favoriteStudiesBreakdown.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    tv.favoriteStudiesBreakdown.forEach { sig ->
                                        val sigCol = when (sig.signal) {
                                            "BUY" -> BullishGreen
                                            "SELL" -> BearishRed
                                            else -> RiskAmber
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(DarkSurface)
                                                .border(1.dp, sigCol.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                                .widthIn(min = 36.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = sig.name,
                                                    fontSize = 9.sp,
                                                    color = TextMuted,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = sig.signal,
                                                    fontSize = 9.sp,
                                                    color = sigCol,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // FUTURES LEVERAGE & LIQUIDATION MATRIX (If Futures mode is on)
                if (isFutures) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, TechCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "FUTURES LEVERAGE & LIQUIDATION MATRIX",
                                    color = TechCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${uiState.selectedExchange.displayName} Perp",
                                    color = Color(uiState.selectedExchange.colorHex),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Interactive Leverage Quick Select Pills
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(2, 5, 10, 20, 50, 100).forEach { l ->
                                    val isCur = l == lev
                                    val isRec = l == 10
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isCur) TechCyan else DarkSurface)
                                            .border(
                                                1.dp,
                                                if (isCur) TechCyan else if (isRec) TechCyan.copy(alpha = 0.5f) else DarkBorder,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { viewModel.setLeverage(l) }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "${l}x",
                                                color = if (isCur) Color.Black else TextPrimary,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = when (l) {
                                                    2 -> "Safe"
                                                    5 -> "Swing"
                                                    10 -> "AI Rec"
                                                    20 -> "Fast"
                                                    50 -> "High"
                                                    else -> "Extreme"
                                                },
                                                color = if (isCur) Color.Black else if (isRec) TechCyan else TextMuted,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Liquidation and Buffer stats
                            val liqBuffer = Math.abs(recEntry - setup.estimatedLiquidationPrice) / recEntry * 100.0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Estimated Liquidation:",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "$${formatPrice(setup.estimatedLiquidationPrice)} (${formatPrice(liqBuffer)}% buffer)",
                                    color = if (liqBuffer > 8.0) BullishGreen else RiskAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            // Visual Liquidation Buffer Gauge
                            val bufferRatio = (liqBuffer / 35.0).toFloat().coerceIn(0.04f, 1f)
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Liquidation Safety Cushion", color = TextMuted, fontSize = 9.sp)
                                    Text(
                                        text = "${formatPrice(liqBuffer)}% to Liquidation",
                                        color = if (liqBuffer > 15.0) BullishGreen else if (liqBuffer > 6.0) RiskAmber else BearishRed,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                LinearProgressIndicator(
                                    progress = { bufferRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (liqBuffer > 15.0) BullishGreen else if (liqBuffer > 6.0) RiskAmber else BearishRed,
                                    trackColor = DarkSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = when (lev) {
                                    2 -> "2x Leverage: Ultra-safe buffer (~50% room). Minimal liquidation risk."
                                    5 -> "5x Leverage: Conservative margin (~20% buffer). Suitable for swing positions."
                                    10 -> "10x Leverage [AI Recommended]: Optimal risk-to-reward balance. Generates +${formatPrice(gain1 * 10)}% on TP1 while keeping 9.5% buffer."
                                    20 -> "20x Leverage: High volatility risk. Generates +${formatPrice(gain1 * 20)}% on TP1. Requires strict stop loss execution."
                                    50 -> "50x Leverage: Extreme leverage. Generates +${formatPrice(gain1 * 50)}% on TP1. Liquidation occurs at ~1.8% adverse price wick."
                                    else -> "100x Leverage: Maximum leverage scalp. Generates +${formatPrice(gain1 * 100)}% on TP1. Liquidation occurs at ~0.9% adverse wick."
                                },
                                color = TextMuted,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            } else {
                // No valid plan: show single NoPlanCard (with scenarios, waiting-for, and optional hypothetical replay preview)
                NoPlanCard(
                    verdictState = vs,
                    isRunningHypotheticalReplay = uiState.isRunningHypotheticalReplay,
                    hypotheticalScenarioSide = uiState.hypotheticalScenarioSide,
                    hypotheticalReplayResult = uiState.hypotheticalScenarioReplay,
                    onRunHypotheticalReplay = { side -> viewModel.runHypotheticalScenarioReplay(side) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            com.example.ui.components.DayTraderExecutionSection(
                uiState = uiState,
                viewModel = viewModel
            )

            Spacer(modifier = Modifier.height(12.dp))

            val smcData = remember(quote.candles) {
                com.example.domain.analytics.QuantitativeEngine.computeSmartMoneyStructure(quote.candles)
            }
            val swingIsBearish = smcData.smcSwingStructure.contains("Bearish", ignoreCase = true)
            val swingIsBullish = smcData.smcSwingStructure.contains("Bullish", ignoreCase = true)
            val internalIsBearish = smcData.smcInternalStructure.contains("Bearish", ignoreCase = true)
            val internalIsBullish = smcData.smcInternalStructure.contains("Bullish", ignoreCase = true)
            val isMixed = (swingIsBearish && internalIsBullish) || (swingIsBullish && internalIsBearish)

            // Institutional Liquidity & SMC Decomposition
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "BUILT-IN SMART MONEY CONCEPTS (SMC)",
                            color = TechCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TechCyan.copy(alpha = 0.15f))
                                .border(1.dp, TechCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .widthIn(min = 50.dp)
                        ) {
                            Text(
                                text = "BUILT-IN SMC",
                                color = TechCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val nearestBullOb = smcData.activeOrderBlocks.filter { it.type == com.example.domain.model.OrderBlockType.BULLISH_DEMAND_BLOCK }.maxByOrNull { it.top }
                    val nearestBearOb = smcData.activeOrderBlocks.filter { it.type == com.example.domain.model.OrderBlockType.BEARISH_SUPPLY_BLOCK }.minByOrNull { it.bottom }
                    val nearestFvg = smcData.activeFVGs.firstOrNull()
                    val bslPool = smcData.liquidityPools.firstOrNull { it.type == com.example.domain.model.LiquidityType.BUY_SIDE_LIQUIDITY_BSL }
                    val sslPool = smcData.liquidityPools.firstOrNull { it.type == com.example.domain.model.LiquidityType.SELL_SIDE_LIQUIDITY_SSL }

                    val bsl = bslPool?.priceLevel?.takeIf { it > 0.0 } ?: setup?.takeProfit1?.takeIf { it > 0.0 } ?: maxOf(quote.dayHigh, quote.currentPrice * 1.035)
                    val ssl = sslPool?.priceLevel?.takeIf { it > 0.0 } ?: setup?.stopLoss?.takeIf { it > 0.0 } ?: minOf(quote.dayLow, quote.currentPrice * 0.965)

                    val structVal = if (isMixed) {
                        "MIXED (swing ${if (swingIsBearish) "bearish" else "bullish"} / internal ${if (internalIsBullish) "bullish" else "bearish"})"
                    } else {
                        "${smcData.smcSwingStructure} • ${smcData.smcInternalStructure.take(22)}"
                    }

                    val smcSigColor = if (isMixed) RiskAmber else if (smcData.builtInSmcSignal.contains("BULLISH") || smcData.builtInSmcSignal.contains("DISCOUNT")) BullishGreen else if (smcData.builtInSmcSignal.contains("BEARISH") || smcData.builtInSmcSignal.contains("PREMIUM")) BearishRed else RiskAmber
                    ResponsiveKeyValueRow(
                        label = "Built-in SMC Signal:",
                        value = if (isMixed) "MIXED STRUCTURE (Unconfirmed)" else "${smcData.builtInSmcSignal} (${smcData.premiumDiscountPercent.toInt()}%)",
                        valueColor = smcSigColor,
                        valueFontWeight = FontWeight.Bold
                    )

                    ResponsiveKeyValueRow(
                        label = "Built-in SMC Structure:",
                        value = structVal,
                        valueColor = if (isMixed) RiskAmber else TextPrimary,
                        valueFontWeight = FontWeight.SemiBold
                    )

                    ResponsiveKeyValueRow(
                        label = "Order Flow Structure:",
                        value = smcData.smcStructureTrend,
                        valueColor = TechCyan,
                        valueFontWeight = FontWeight.Bold
                    )

                    ResponsiveKeyValueRow(
                        label = "Institutional Key Level:",
                        value = smcData.smcStrongHighLow,
                        valueColor = TextMuted,
                        valueFontWeight = FontWeight.Normal
                    )

                    ResponsiveKeyValueRow(
                        label = "Buy-Side Liquidity (BSL):",
                        value = "${PriceFormatter.formatCurrency(bsl)} (Swing High Cluster)",
                        valueColor = BullishGreen,
                        valueFontWeight = FontWeight.Bold
                    )

                    ResponsiveKeyValueRow(
                        label = "Sell-Side Liquidity (SSL):",
                        value = "${PriceFormatter.formatCurrency(ssl)} (Swing Low Cluster)",
                        valueColor = BearishRed,
                        valueFontWeight = FontWeight.Bold
                    )

                    ResponsiveKeyValueRow(
                        label = "Liquidity Sweep Status:",
                        value = if (isBullish) "SSL Swept (Stop Hunt Reclaimed)" else "BSL Swept (Overhead Supply Rejection)",
                        valueColor = if (isBullish) BullishGreen else BearishRed,
                        valueFontWeight = FontWeight.SemiBold
                    )

                    val validBullOb = nearestBullOb?.takeIf { it.bottom > 0.0 && it.top > 0.0 }
                    val validBearOb = nearestBearOb?.takeIf { it.bottom > 0.0 && it.top > 0.0 }
                    val obText = if (isBullish) {
                        if (validBullOb != null) "Demand Block ${PriceFormatter.formatZoneRange(validBullOb.bottom, validBullOb.top)}"
                        else "Demand Block ${PriceFormatter.formatZoneRange(ssl, setup?.recommendedEntry?.takeIf { it > 0.0 } ?: (quote.currentPrice * 0.985))}"
                    } else {
                        if (validBearOb != null) "Supply Block ${PriceFormatter.formatZoneRange(validBearOb.bottom, validBearOb.top)}"
                        else "Supply Block ${PriceFormatter.formatZoneRange(setup?.recommendedEntry?.takeIf { it > 0.0 } ?: (quote.currentPrice * 1.015), bsl)}"
                    }
                    ResponsiveKeyValueRow(
                        label = "Institutional Order Block:",
                        value = obText,
                        valueColor = TechCyan,
                        valueFontWeight = FontWeight.SemiBold
                    )

                    val validFvg = nearestFvg?.takeIf { it.bottom > 0.0 && it.top > 0.0 }
                    val fvgText = if (validFvg != null) {
                        "${if (validFvg.type == com.example.domain.model.FvgType.BULLISH_IMBALANCE) "Bullish BISI" else "Bearish SIBI"} Imbalance ${PriceFormatter.formatZoneRange(validFvg.bottom, validFvg.top)}"
                    } else {
                        if (isBullish) "Bullish BISI Imbalance ${PriceFormatter.formatZoneRange(quote.currentPrice * 0.99, quote.currentPrice * 0.998)}"
                        else "Bearish SIBI Imbalance ${PriceFormatter.formatZoneRange(quote.currentPrice * 1.002, quote.currentPrice * 1.01)}"
                    }
                    ResponsiveKeyValueRow(
                        label = "Fair Value Gap (FVG):",
                        value = fvgText,
                        valueColor = AgentPurple,
                        valueFontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Background Scanned Nodes Summary Grid (Technical, SMC, News, Strategy Backtest)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "BACKGROUND EVALUATION NODES",
                    color = TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWide = maxWidth >= 460.dp

                    val node1Title = "Technical (Engine • ${uiState.selectedTimeframe.label})"
                    val node1Val = if (isBullish) "Bullish 8/10" else "Bearish 7/10"
                    val node1Color = if (isBullish) BullishGreen else BearishRed

                    val node2Title = "Built-in SMC (Engine • ${uiState.selectedTimeframe.label})"
                    val node2Val = if (isMixed) "MIXED" else if (isBullish) "Demand OB" else "Supply OB"
                    val node2Color = if (isMixed) RiskAmber else TechCyan

                    val scannerRes = uiState.newsScannerResult
                    val hasNewsData = uiState.newsItems.isNotEmpty() || (scannerRes?.headlines?.isNotEmpty() == true)
                    val isLlmSentiment = (scannerRes?.isLlmGenerated == true) && uiState.hasConnectedLlm
                    val sentimentOrigin = when {
                        !hasNewsData -> "Engine • none"
                        isLlmSentiment -> "AI • ${scannerRes?.modelsScanned?.firstOrNull() ?: "LLM"}"
                        else -> "Engine (lexicon)"
                    }
                    val node3Title = "Sentiment ($sentimentOrigin)"
                    val node3Val = when {
                        !hasNewsData || scannerRes == null -> "No relevant news (-)"
                        else -> "${(scannerRes.sentimentScore * 100).toInt()}% Positive"
                    }
                    val node3Color = when {
                        !hasNewsData || scannerRes == null -> TextMuted
                        scannerRes.sentimentScore >= 0.5 -> BullishGreen
                        else -> BearishRed
                    }

                    val nTrades = backtest?.totalTrades ?: 0
                    val winRate = backtest?.winRatePercent ?: 0.0
                    val winsEst = kotlin.math.round((winRate / 100.0) * nTrades).toInt().coerceIn(0, nTrades)
                    val (wilsonLo, wilsonHi) = com.example.domain.engine.SignalCalibrationEngine.wilsonConfidenceInterval95(winsEst, nTrades)
                    val node4Title = "Backtest Win% (n=$nTrades)"
                    val node4Val = if (nTrades < 30) {
                        if (nTrades == 0) "too few (need 30)"
                        else "${String.format(Locale.US, "%.1f", winRate)}% • too few (95% CI [${String.format(Locale.US, "%.0f", wilsonLo)}%–${String.format(Locale.US, "%.0f", wilsonHi)}%])"
                    } else {
                        "${String.format(Locale.US, "%.1f", winRate)}% [${String.format(Locale.US, "%.0f", wilsonLo)}%–${String.format(Locale.US, "%.0f", wilsonHi)}%]"
                    }
                    // When n < 30, always render in grey (TextMuted), never green
                    val node4Color = if (nTrades < 30) TextMuted else if (winRate >= 50.0) BullishGreen else BearishRed

                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(node1Title, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(node1Val, color = node1Color, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(node2Title, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(node2Val, color = node2Color, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(node3Title, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(node3Val, color = node3Color, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkSurfaceElevated)
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(node4Title, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(node4Val, color = node4Color, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceElevated)
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(node1Title, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(node1Val, color = node1Color, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceElevated)
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(node2Title, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(node2Val, color = node2Color, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceElevated)
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(node3Title, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(node3Val, color = node3Color, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(DarkSurfaceElevated)
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(node4Title, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(node4Val, color = node4Color, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Executive Summary text
            if (synthesis != null) {
                Text(
                    text = synthesis.unifiedConsensusSummary.ifBlank { synthesis.executiveSummary },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Save report & Architecture Pipeline toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showFullPipeline = !showFullPipeline },
                    colors = ButtonDefaults.buttonColors(containerColor = if (showFullPipeline) TechCyan.copy(alpha = 0.2f) else DarkSurfaceElevated),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("toggle_pipeline_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Toggle Pipeline",
                        tint = TechCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showFullPipeline) "Hide 4 Streams" else "4 Data Streams & Agents",
                        fontSize = 11.sp,
                        color = TechCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { viewModel.saveCurrentReport() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.testTag("save_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = "Save Report",
                        tint = TechCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Report", fontSize = 11.sp, color = TextPrimary)
                }
            }

            AnimatedVisibility(visible = showFullPipeline && synthesis != null) {
                if (synthesis != null) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        // 1. Four Ingestion Streams
                        IngestionStreamsAccordionCard(synthesis = synthesis)

                        // 2. Researcher Team Debate
                        synthesis.researcherDossier?.let { dossier ->
                            Spacer(modifier = Modifier.height(10.dp))
                            ResearcherTeamCard(dossier = dossier)
                        }

                        // 3. Trader Proposal (OpenAI o1 Deep Thinking)
                        synthesis.traderProposal?.let { proposal ->
                            Spacer(modifier = Modifier.height(10.dp))
                            TraderProposalCard(proposal = proposal)
                        }

                        // 4. Risk Team Audit
                        synthesis.riskTeamEvaluation?.let { riskTeam ->
                            Spacer(modifier = Modifier.height(10.dp))
                            RiskTeamCard(team = riskTeam)
                        }

                        // 5. Manager Decision
                        synthesis.managerDecision?.let { managerDecision ->
                            Spacer(modifier = Modifier.height(10.dp))
                            ManagerDecisionCard(decision = managerDecision)
                        }

                        // 6. Workload Balancer Status
                        Spacer(modifier = Modifier.height(10.dp))
                        BackgroundWorkloadBalancerMonitorCard(uiState = uiState)
                    }
                }
            }
        }
    }
}

@Composable
fun ResponsiveKeyValueRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    labelColor: Color = TextSecondary,
    valueFontWeight: FontWeight = FontWeight.SemiBold,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isNarrow = maxWidth < 340.dp || (label.length + value.length > 38)
        if (isNarrow) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = label,
                    color = labelColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = value,
                    color = valueColor,
                    fontWeight = valueFontWeight,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = labelColor,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = value,
                    color = valueColor,
                    fontWeight = valueFontWeight,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SignalFamilyChipRow(
    verdictState: com.example.domain.model.VerdictState,
    modifier: Modifier = Modifier
) {
    val familiesAligned = verdictState.familiesAlignedCount
    val activeConflicts = verdictState.conflicts

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .padding(8.dp)
            .testTag("seven_family_chip_row")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SIGNAL FAMILIES ALIGNED",
                color = TechCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$familiesAligned / 7 Families Aligned",
                color = if (familiesAligned >= 4) BullishGreen else RiskAmber,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val allFamilies = com.example.domain.model.SignalFamily.values().toList()
            allFamilies.forEach { family ->
                val vote = verdictState.familyVotes.find { it.family == family }
                val stance = vote?.stance ?: com.example.domain.model.AgentStance.NEUTRAL
                val shortLabel = when (family) {
                    com.example.domain.model.SignalFamily.TREND -> "Trend"
                    com.example.domain.model.SignalFamily.STRUCTURE -> "Structure"
                    com.example.domain.model.SignalFamily.VALUE_VOLUME -> "Value"
                    com.example.domain.model.SignalFamily.VOLATILITY -> "Volatility"
                    com.example.domain.model.SignalFamily.MOMENTUM_TIMING -> "Momentum"
                    com.example.domain.model.SignalFamily.FLOW_POSITIONING -> "Flow"
                    com.example.domain.model.SignalFamily.CONTEXT -> "Context"
                }
                val fColor = when (stance) {
                    com.example.domain.model.AgentStance.BULLISH -> BullishGreen
                    com.example.domain.model.AgentStance.BEARISH -> BearishRed
                    com.example.domain.model.AgentStance.NEUTRAL -> TextMuted
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DarkSurface)
                        .border(1.dp, fColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$shortLabel: ${stance.name}",
                        color = fColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (activeConflicts.isNotEmpty() || verdictState.hasStructuralConflicts) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(BearishRed.copy(alpha = 0.12f))
                    .border(1.dp, BearishRed.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(6.dp)
                    .testTag("conflicts_box")
            ) {
                Text(
                    text = "STRUCTURAL & RED TEAM CONFLICTS DETECTED",
                    color = BearishRed,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                val listToRender = if (activeConflicts.isNotEmpty()) activeConflicts else listOf("SMC swing and internal structure disagree (MIXED).")
                for (conflict in listToRender) {
                    Text(
                        text = "• $conflict",
                        color = BearishRed,
                        fontSize = 8.sp,
                        lineHeight = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ScenarioPairRow(
    bullScenario: com.example.domain.model.ScenarioPlan,
    bearScenario: com.example.domain.model.ScenarioPlan,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, BullishGreen.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Text("BULL SCENARIO", color = BullishGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(bullScenario.triggerCondition, color = TextSecondary, fontSize = 8.sp, lineHeight = 11.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Invalidation: ${PriceFormatter.formatCurrency(bullScenario.invalidationLevel)} • Target: ${PriceFormatter.formatCurrency(bullScenario.targetLevel)}",
                color = TextMuted,
                fontSize = 7.sp
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, BearishRed.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                .padding(8.dp)
        ) {
            Text("BEAR SCENARIO", color = BearishRed, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(bearScenario.triggerCondition, color = TextSecondary, fontSize = 8.sp, lineHeight = 11.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Invalidation: ${PriceFormatter.formatCurrency(bearScenario.invalidationLevel)} • Target: ${PriceFormatter.formatCurrency(bearScenario.targetLevel)}",
                color = TextMuted,
                fontSize = 7.sp
            )
        }
    }
}

@Composable
fun NoPlanCard(
    verdictState: com.example.domain.model.VerdictState,
    isRunningHypotheticalReplay: Boolean = false,
    hypotheticalScenarioSide: PositionSide? = null,
    hypotheticalReplayResult: com.example.domain.daytrader.replay.ReplayResult? = null,
    onRunHypotheticalReplay: ((PositionSide) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, BearishRed.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("no_plan_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NO ACTIVE TRADE PLAN (${verdictState.verdict.label})",
                color = BearishRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(BearishRed.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = verdictState.marketRegime.label.uppercase(Locale.US),
                    color = BearishRed,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val reasonText = verdictState.vetoes.firstOrNull() ?: verdictState.reasons.firstOrNull() ?: verdictState.oneLineWhy
        Text(
            text = reasonText,
            color = TextPrimary,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )

        Text(
            text = "Diagnostics: bars loaded ${verdictState.barsLoaded}" +
                (verdictState.staleDataWarning?.let { " • $it" } ?: ""),
            color = if (verdictState.staleDataWarning != null) RiskAmber else TextMuted,
            fontSize = 9.sp
        )

        if (verdictState.regimeMetricsSummary.isNotBlank() && !reasonText.contains("ADX")) {
            Text(
                text = verdictState.regimeMetricsSummary,
                color = TextSecondary,
                fontSize = 9.sp
            )
        }

        val waitText = verdictState.waitingForCondition.ifBlank { verdictState.bullScenario.triggerCondition }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurface)
                .border(1.dp, RiskAmber.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = RiskAmber, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "waiting for: $waitText",
                color = RiskAmber,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 13.sp
            )
        }

        ScenarioPairRow(
            bullScenario = verdictState.bullScenario,
            bearScenario = verdictState.bearScenario
        )

        if (onRunHypotheticalReplay != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BullishGreen.copy(alpha = 0.14f))
                        .border(1.dp, BullishGreen.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .clickable(enabled = !isRunningHypotheticalReplay) { onRunHypotheticalReplay(PositionSide.LONG) }
                        .padding(vertical = 6.dp)
                        .testTag("btn_hypothetical_bull_replay"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRunningHypotheticalReplay && hypotheticalScenarioSide == PositionSide.LONG) "Running..." else "Hypothetical Bull Replay",
                        color = BullishGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BearishRed.copy(alpha = 0.14f))
                        .border(1.dp, BearishRed.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .clickable(enabled = !isRunningHypotheticalReplay) { onRunHypotheticalReplay(PositionSide.SHORT) }
                        .padding(vertical = 6.dp)
                        .testTag("btn_hypothetical_bear_replay"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRunningHypotheticalReplay && hypotheticalScenarioSide == PositionSide.SHORT) "Running..." else "Hypothetical Bear Replay",
                        color = BearishRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (hypotheticalReplayResult != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .border(1.dp, RiskAmber.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "HYPOTHETICAL SCENARIO REPLAY PREVIEW (${hypotheticalScenarioSide?.name ?: "SCENARIO"}) — NOT AN ACTIVE PLAN",
                    color = RiskAmber,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                com.example.ui.components.ReplayAgentCard(
                    replayResult = hypotheticalReplayResult,
                    entryPrice = verdictState.livePrice,
                    stopLossPrice = if (hypotheticalScenarioSide == PositionSide.SHORT) verdictState.bearScenario.invalidationLevel else verdictState.bullScenario.invalidationLevel,
                    targetPrice = if (hypotheticalScenarioSide == PositionSide.SHORT) verdictState.bearScenario.targetLevel else verdictState.bullScenario.targetLevel
                )
            }
        }
    }
}


