package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.MarketDataService
import com.example.domain.model.CryptoExchange
import com.example.domain.model.ExchangePriceQuote
import com.example.domain.util.PriceFormatter
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

enum class SwitcherInterfaceMode {
    TABS,
    DROPDOWN,
    BOTH
}

/**
 * Interactive Dropdown & Tab Interface to seamlessly switch between crypto data sources
 * (Binance, MEXC, Bybit, etc.) for real-time crypto price updates.
 */
@Composable
fun ExchangeDataSourceSwitcher(
    selectedExchange: CryptoExchange,
    exchangeQuotes: List<ExchangePriceQuote>,
    onSelectExchange: (CryptoExchange) -> Unit,
    symbol: String,
    modifier: Modifier = Modifier,
    initialMode: SwitcherInterfaceMode = SwitcherInterfaceMode.BOTH
) {
    var interfaceMode by remember { mutableStateOf(initialMode) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Primary 3 sources requested: Binance, MEXC, Bybit
    val primaryExchanges = listOf(
        CryptoExchange.BINANCE,
        CryptoExchange.MEXC,
        CryptoExchange.BYBIT
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("exchange_data_source_switcher")
    ) {
        // Header with Title & Mode Selector (Tabs / Dropdown toggle)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(selectedExchange.colorHex))
                )
                Spacer(modifier = Modifier.width(7.dp))
                Column {
                    Text(
                        text = "CRYPTO DATA SOURCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Real-time feed: ${selectedExchange.displayName}",
                        fontSize = 10.sp,
                        color = Color(selectedExchange.colorHex),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Interface Mode Toggle (Tabs vs Dropdown vs Both)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (interfaceMode == SwitcherInterfaceMode.TABS) TechCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { interfaceMode = SwitcherInterfaceMode.TABS }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("mode_toggle_tabs")
                ) {
                    Text(
                        text = "Tabs",
                        fontSize = 10.sp,
                        fontWeight = if (interfaceMode == SwitcherInterfaceMode.TABS) FontWeight.Bold else FontWeight.Normal,
                        color = if (interfaceMode == SwitcherInterfaceMode.TABS) TechCyan else TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (interfaceMode == SwitcherInterfaceMode.DROPDOWN) TechCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { interfaceMode = SwitcherInterfaceMode.DROPDOWN }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("mode_toggle_dropdown")
                ) {
                    Text(
                        text = "Dropdown",
                        fontSize = 10.sp,
                        fontWeight = if (interfaceMode == SwitcherInterfaceMode.DROPDOWN) FontWeight.Bold else FontWeight.Normal,
                        color = if (interfaceMode == SwitcherInterfaceMode.DROPDOWN) TechCyan else TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (interfaceMode == SwitcherInterfaceMode.BOTH) TechCyan.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { interfaceMode = SwitcherInterfaceMode.BOTH }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("mode_toggle_both")
                ) {
                    Text(
                        text = "Both",
                        fontSize = 10.sp,
                        fontWeight = if (interfaceMode == SwitcherInterfaceMode.BOTH) FontWeight.Bold else FontWeight.Normal,
                        color = if (interfaceMode == SwitcherInterfaceMode.BOTH) TechCyan else TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 1. DROPDOWN INTERFACE
        if (interfaceMode == SwitcherInterfaceMode.DROPDOWN || interfaceMode == SwitcherInterfaceMode.BOTH) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { dropdownExpanded = !dropdownExpanded },
                    shape = RoundedCornerShape(10.dp),
                    color = DarkSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(selectedExchange.colorHex).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exchange_dropdown_trigger")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(selectedExchange.colorHex))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = selectedExchange.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "LIVE TAPE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BullishGreen,
                                        maxLines = 1
                                    )
                                }
                                val activeQuote = exchangeQuotes.find { it.exchange == selectedExchange }
                                if (activeQuote != null && activeQuote.price > 0.0 && activeQuote.unavailableReason == null) {
                                    Text(
                                        text = "${PriceFormatter.formatCurrency(activeQuote.price)} • Spread: ${PriceFormatter.formatPercent(activeQuote.spreadPercent, 3)} • Latency: ${activeQuote.latencyMs}ms",
                                        fontSize = 10.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else if (activeQuote?.unavailableReason != null) {
                                    Text(
                                        text = "Unavailable • ${MarketDataService.cleanUnavailableReason(activeQuote.unavailableReason)}",
                                        fontSize = 10.sp,
                                        color = com.example.ui.theme.BearishRed,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Select Source",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Open Data Source Dropdown",
                                tint = TechCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Dropdown Menu Popover
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        .testTag("exchange_dropdown_menu")
                ) {
                    Text(
                        text = "SWITCH REAL-TIME SOURCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )

                    HorizontalDivider(color = DarkBorder, thickness = 0.5.dp)

                    CryptoExchange.values().forEach { exchange ->
                        val isSelected = exchange == selectedExchange
                        val quote = exchangeQuotes.find { it.exchange == exchange }
                        val price = quote?.price ?: 0.0

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(exchange.colorHex))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = exchange.displayName,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color(exchange.colorHex) else TextPrimary
                                                )
                                                if (exchange in primaryExchanges) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "PRIMARY",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TechCyan
                                                    )
                                                }
                                            }
                                            Text(
                                                text = when (exchange) {
                                                    CryptoExchange.BINANCE -> "Global Spot/Futures Tape • 0.04% Fee"
                                                    CryptoExchange.MEXC -> "Zero Maker / 0.02% Taker • Deep Altcoins"
                                                    CryptoExchange.BYBIT -> "Derivatives & Spot V5 API • 0.055% Fee"
                                                    CryptoExchange.OKX -> "Unified Account Feed • 0.05% Fee"
                                                    CryptoExchange.COINBASE -> "US Regulated Order Book • 0.60% Fee"
                                                },
                                                fontSize = 10.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        if (quote?.unavailableReason != null) {
                                            Text(
                                                text = "Unavailable • ${MarketDataService.cleanUnavailableReason(quote.unavailableReason)}",
                                                fontSize = 10.sp,
                                                color = com.example.ui.theme.BearishRed,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else if (price > 0.0) {
                                            Text(
                                                text = PriceFormatter.formatCurrency(price),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        }
                                        if (isSelected) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Active Source",
                                                    tint = BullishGreen,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BullishGreen)
                                            }
                                        }
                                    }
                                }
                            },
                            onClick = {
                                onSelectExchange(exchange)
                                dropdownExpanded = false
                            },
                            modifier = Modifier
                                .background(if (isSelected) Color(exchange.colorHex).copy(alpha = 0.12f) else Color.Transparent)
                                .testTag("dropdown_item_${exchange.id}")
                        )
                    }
                }
            }

            if (interfaceMode == SwitcherInterfaceMode.BOTH) {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // 2. TABS INTERFACE
        if (interfaceMode == SwitcherInterfaceMode.TABS || interfaceMode == SwitcherInterfaceMode.BOTH) {
            Column {
                if (interfaceMode == SwitcherInterfaceMode.BOTH) {
                    Text(
                        text = "Quick Source Tabs:",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Row of primary exchange tabs: Binance, MEXC, Bybit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    primaryExchanges.forEach { exchange ->
                        val isSelected = exchange == selectedExchange
                        val quote = exchangeQuotes.find { it.exchange == exchange }
                        val price = quote?.price ?: 0.0
                        val latency = quote?.latencyMs ?: 20L
                        val exchangeColor = Color(exchange.colorHex)

                        val animatedBorderColor by animateColorAsState(
                            targetValue = if (isSelected) exchangeColor else DarkBorder,
                            label = "border_color"
                        )
                        val animatedBgColor by animateColorAsState(
                            targetValue = if (isSelected) exchangeColor.copy(alpha = 0.16f) else DarkSurfaceElevated,
                            label = "bg_color"
                        )

                        Box(
                            modifier = Modifier
                                .width(156.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(animatedBgColor)
                                .border(1.dp, animatedBorderColor, RoundedCornerShape(10.dp))
                                .clickable { onSelectExchange(exchange) }
                                .padding(horizontal = 10.dp, vertical = 9.dp)
                                .testTag("exchange_tab_${exchange.id}")
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(exchangeColor)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = exchange.displayName,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) exchangeColor else TextPrimary,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(BullishGreen.copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                .widthIn(min = 36.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BullishGreen,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (quote?.unavailableReason != null) {
                                        Text(
                                            text = "Unavailable • ${MarketDataService.cleanUnavailableReason(quote.unavailableReason)}",
                                            fontSize = 9.sp,
                                            color = com.example.ui.theme.BearishRed,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else if (price > 0.0) {
                                        Text(
                                            text = PriceFormatter.formatCurrency(price),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    } else {
                                        Text(
                                            text = "${symbol}/USDT",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Text(
                                        text = if (quote?.isStale == true) "STALE" else "${latency}ms",
                                        fontSize = 9.sp,
                                        color = if (quote?.isStale == true) com.example.ui.theme.RiskAmber else if (latency < 25) BullishGreen else TextMuted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Data Source Info Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = TechCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Streaming real-time order flow & candles for $symbol",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${selectedExchange.displayName} API",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(selectedExchange.colorHex),
                maxLines = 1
            )
        }
    }
}

private fun formatPrice(price: Double): String {
    return PriceFormatter.formatPrice(price)
}
