package com.example.ui.components

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
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.CryptoExchange
import com.example.domain.model.ExchangePriceQuote
import com.example.domain.util.PriceFormatter
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.BearishRed
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ExchangeSourceSelector(
    selectedExchange: CryptoExchange,
    exchangeQuotes: List<ExchangePriceQuote>,
    onSelectExchange: (CryptoExchange) -> Unit,
    symbol: String,
    modifier: Modifier = Modifier
) {
    val selectedQuote = exchangeQuotes.find { it.exchange == selectedExchange }
    val nowMs = System.currentTimeMillis()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("exchange_source_selector")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(selectedExchange.colorHex))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "EXCHANGE FEED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TechCyan.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${selectedExchange.displayName} Feed",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TechCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Real venue last price, (ask - bid)/mid spread, and quote age for $symbol:",
            fontSize = 11.sp,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal scroll list of exchanges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CryptoExchange.values().forEach { exchange ->
                val isSelected = exchange == selectedExchange
                val quote = exchangeQuotes.find { it.exchange == exchange }
                val price = quote?.price ?: 0.0
                val spread = quote?.spreadPercent ?: 0.0
                val isUnavailable = quote == null || quote.unavailableReason != null || price <= 0.0
                val isStale = quote?.isStale == true
                val ageSec = if (quote != null && quote.fetchedAtMs > 0L) {
                    ((nowMs - quote.fetchedAtMs).coerceAtLeast(0L) / 1000L)
                } else null

                Box(
                    modifier = Modifier
                        .widthIn(min = 148.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(exchange.colorHex).copy(alpha = 0.18f) else DarkSurfaceElevated)
                        .border(
                            1.dp,
                            when {
                                isStale -> RiskAmber
                                isSelected -> Color(exchange.colorHex)
                                else -> DarkBorder
                            },
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectExchange(exchange) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("exchange_tab_${exchange.id}")
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(exchange.colorHex))
                            )
                            Text(
                                text = exchange.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(exchange.colorHex) else TextPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (isStale) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(RiskAmber.copy(alpha = 0.2f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("STALE", color = RiskAmber, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        if (isUnavailable) {
                            Text(
                                text = "Unavailable - ${quote?.unavailableReason ?: "no quote"}",
                                fontSize = 9.sp,
                                color = BearishRed,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2
                            )
                        } else {
                            Text(
                                text = PriceFormatter.formatCurrency(price),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                            val spreadStr = if (spread > 0.0) PriceFormatter.formatPercent(spread, decimals = 3, signed = false) else "-"
                            val ageStr = if (ageSec != null) "${ageSec}s ago" else "-"
                            Text(
                                text = "Spread: $spreadStr • $ageStr",
                                fontSize = 9.sp,
                                color = if (isStale) RiskAmber else TechCyan,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Live Arbitrage & Taker Fee Insights Banner
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(DarkSurfaceElevated)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Icon(
                    imageVector = Icons.Default.CompareArrows,
                    contentDescription = null,
                    tint = TechCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                val latencyText = selectedQuote?.latencyMs?.takeIf { it > 0L }?.let { "${it}ms" } ?: "-"
                Text(
                    text = "Fee: ${selectedExchange.takerFeePercent}% | Latency: $latencyText",
                    fontSize = 10.sp,
                    color = TextMuted,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            val syncLabel = when {
                selectedQuote?.unavailableReason != null -> "Unavailable"
                selectedQuote?.isStale == true -> "STALE (>0.2%)"
                else -> "Live Synced"
            }
            val syncColor = when {
                selectedQuote?.unavailableReason != null -> BearishRed
                selectedQuote?.isStale == true -> RiskAmber
                else -> BullishGreen
            }
            Text(
                text = syncLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = syncColor,
                maxLines = 1
            )
        }
    }
}
