package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.abs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.master.HorizonPlan
import com.example.domain.master.MasterVerdict
import com.example.domain.master.TradingHorizon
import com.example.domain.model.CandlePoint
import com.example.domain.model.PositionSide
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.TradePlanLifecycleStatus
import com.example.domain.model.TradeSetupPlan
import com.example.domain.util.PriceFormatter
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
fun CandlestickChart(
    candles: List<CandlePoint>,
    modifier: Modifier = Modifier,
    showSma: Boolean = true,
    tradeSetupPlan: TradeSetupPlan? = null,
    currentPrice: Double = 0.0,
    masterVerdict: MasterVerdict? = null,
    selectedHorizon: TradingHorizon = TradingHorizon.INTRADAY,
    onRecalculateSetup: () -> Unit = {}
) {
    if (candles.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No chart data available", color = TextSecondary)
        }
        return
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var showSmaState by remember { mutableStateOf(showSma) }

    val activeHorizonPlan: HorizonPlan? = remember(masterVerdict, selectedHorizon) {
        masterVerdict?.horizonPlans?.get(selectedHorizon)
    }

    // Multi-target calculations from HorizonPlan (preferred) or TradeSetupPlan
    val effectiveSide = activeHorizonPlan?.side ?: tradeSetupPlan?.side ?: PositionSide.LONG
    val isLong = effectiveSide != PositionSide.SHORT
    val entryBase = activeHorizonPlan?.limitPrice?.takeIf { it > 0.0 } ?: tradeSetupPlan?.recommendedEntry ?: 0.0
    val stopBase = activeHorizonPlan?.stopLoss?.takeIf { it > 0.0 } ?: tradeSetupPlan?.stopLoss ?: 0.0
    val riskSpan = if (entryBase > 0.0 && stopBase > 0.0) kotlin.math.abs(entryBase - stopBase) else 10.0
    val rawTp1 = activeHorizonPlan?.tp1?.takeIf { it > 0.0 }
        ?: tradeSetupPlan?.takeProfit1?.takeIf { it > 0 }
        ?: if (entryBase > 0.0) (if (isLong) entryBase + riskSpan * 2.0 else entryBase - riskSpan * 2.0) else null
    val rawTp2 = activeHorizonPlan?.tp2?.takeIf { it > 0.0 }
        ?: tradeSetupPlan?.takeProfit2?.takeIf { it > 0 }
        ?: if (entryBase > 0.0) (if (isLong) entryBase + riskSpan * 3.5 else entryBase - riskSpan * 3.5) else null
    val rawTp3 = activeHorizonPlan?.tp3?.takeIf { it > 0.0 }
        ?: tradeSetupPlan?.takeProfit3?.takeIf { it > 0 }
        ?: if (entryBase > 0.0) (if (isLong) entryBase + riskSpan * 5.2 else entryBase - riskSpan * 5.2) else null

    val (effectiveTp1, effectiveTp2, effectiveTp3) = if (isLong) {
        val list = listOfNotNull(rawTp1, rawTp2, rawTp3).sorted()
        Triple(list.getOrNull(0), list.getOrNull(1), list.getOrNull(2))
    } else {
        val list = listOfNotNull(rawTp1, rawTp2, rawTp3).sortedDescending()
        Triple(list.getOrNull(0), list.getOrNull(1), list.getOrNull(2))
    }

    // Interactive chart overlay controls
    var showTargetsOverlay by remember { mutableStateOf(true) }
    var focusCandlesMode by remember { mutableStateOf(false) }
    var showZonesOverlay by remember { mutableStateOf(true) }
    var showLiquidityOverlay by remember { mutableStateOf(true) }
    var showVwapOverlay by remember { mutableStateOf(false) }
    var showEmaOverlay by remember { mutableStateOf(true) }
    var showVolProfileOverlay by remember { mutableStateOf(false) }
    var showAllHorizonsOverlay by remember { mutableStateOf(false) }

    // Adjustable Chart Zoom & Pan states (Keyed to symbol and timeframe so live ticks don't jump/reset)
    val totalCandles = candles.size
    // Always-current values for the touch handlers (handlers created once must never read stale numbers)
    val totalCandlesNow by rememberUpdatedState(totalCandles)
    val baseVisibleCount = 28 // Generous width per candle so price action is bold and distinct on all timeframes
    var zoomLevel by remember(tradeSetupPlan?.pairSymbol, tradeSetupPlan?.timeframe) { mutableStateOf(1.0f) }
    var verticalPanPercent by remember(tradeSetupPlan?.pairSymbol, tradeSetupPlan?.timeframe) { mutableStateOf(0f) }
    var verticalScaleFactor by remember(tradeSetupPlan?.pairSymbol, tradeSetupPlan?.timeframe) { mutableStateOf(1.0f) }

    // Calculate visible candle count based on base count and zoom level
    val visibleCandleCount = ((baseVisibleCount / zoomLevel).toInt()).coerceAtLeast(4).coerceAtMost(totalCandles)
    val maxPan = (totalCandles - visibleCandleCount).coerceAtLeast(0)

    // Pan offset initialized to -1f so it sticks to latest prices on right edge until user pans
    var panOffset by remember(tradeSetupPlan?.pairSymbol, tradeSetupPlan?.timeframe) { mutableStateOf(-1f) }
    val effectivePan = if (panOffset < 0f) maxPan.toFloat() else panOffset.coerceIn(0f, maxPan.toFloat())

    // Visible slice of candles based on zoom and horizontal pan
    val clampedStart = effectivePan.toInt().coerceIn(0, maxPan)
    val visibleEnd = (clampedStart + visibleCandleCount).coerceAtMost(totalCandles)
    val visibleCandles = if (totalCandles > 0 && clampedStart < visibleEnd) {
        candles.subList(clampedStart, visibleEnd)
    } else {
        candles
    }

    val visibleCountNow by rememberUpdatedState(visibleCandles.size)

    val candleMin = visibleCandles.minOfOrNull { it.low } ?: 100.0
    val candleMax = visibleCandles.maxOfOrNull { it.high } ?: (candleMin + 1.0)
    val candleSpan = (candleMax - candleMin).coerceAtLeast(candleMax * 0.0008)

    val lastCandleClose = visibleCandles.lastOrNull()?.close ?: candleMax
    val chartAtr = com.example.domain.analytics.QuantitativeEngine.calculateATR(visibleCandles, 14).takeIf { it > 0 } ?: (candleSpan / visibleCandles.size.coerceAtLeast(1))

    val slVal = stopBase.takeIf { it > 0.0 }
    val tp1Val = effectiveTp1?.takeIf { it > 0.0 }
    val tp2Val = effectiveTp2?.takeIf { it > 0.0 }
    val tp3Val = effectiveTp3?.takeIf { it > 0.0 }
    val entryVal = entryBase.takeIf { it > 0.0 }
    val zStart = activeHorizonPlan?.entryZoneMin?.takeIf { it > 0.0 } ?: tradeSetupPlan?.entryZoneStart?.takeIf { it > 0.0 }
    val zEnd = activeHorizonPlan?.entryZoneMax?.takeIf { it > 0.0 } ?: tradeSetupPlan?.entryZoneEnd?.takeIf { it > 0.0 }

    // Dynamic Range calculation:
    // Guarantees candles, entry corridor, SL, and TP targets are reliably framed
    val (effMin, effMax) = if (!showTargetsOverlay || tradeSetupPlan == null) {
        val pad = (candleSpan * 0.12).coerceAtLeast(candleMax * 0.002)
        Pair(candleMin - pad, candleMax + pad)
    } else if (focusCandlesMode) {
        val minP = listOfNotNull(candleMin, entryVal, zStart, zEnd).minOrNull() ?: candleMin
        val maxP = listOfNotNull(candleMax, entryVal, zStart, zEnd).maxOrNull() ?: candleMax
        val pad = ((maxP - minP) * 0.14).coerceAtLeast(maxP * 0.002)
        Pair(minP - pad, maxP + pad)
    } else {
        // Full View: ALWAYS comfortably frame visible candles, Entry corridor, SL, and TP1
        val isValidCandidate = tradeSetupPlan != null && tradeSetupPlan.isAvailable && tradeSetupPlan.recommendedEntry > 0.0
        val safeSl = slVal?.takeIf { abs(it - candleMin) <= candleSpan * 3.0 && abs(it - candleMin) / candleMin <= 0.25 }
        val safeTp1 = tp1Val?.takeIf { abs(it - candleMax) <= candleSpan * 3.0 && abs(it - candleMax) / candleMax <= 0.25 }
        val safeEntry = entryVal?.takeIf { abs(it - candleMin) <= candleSpan * 3.0 }

        val keyLevels = if (isValidCandidate) {
            listOfNotNull(candleMin, candleMax, safeSl, safeEntry, safeTp1)
        } else {
            listOf(candleMin, candleMax)
        }

        val rawMin = keyLevels.minOrNull() ?: candleMin
        val rawMax = keyLevels.maxOrNull() ?: candleMax
        val span = (rawMax - rawMin).coerceAtLeast(candleSpan)
        val pad = (span * 0.08).coerceAtLeast(rawMax * 0.003)
        Pair(rawMin - pad, rawMax + pad)
    }

    // Vertical pan and zoom adjustment scaled proportionally for all price magnitudes
    val actualSpan = (effMax - effMin).coerceAtLeast(0.000001)
    val minAllowedSpan = (effMax * 0.002).coerceAtLeast(0.000001)
    val rawSpan = maxOf(actualSpan * verticalScaleFactor, minAllowedSpan)
    val midPrice = ((effMax + effMin) / 2.0) - (rawSpan * verticalPanPercent)
    val minPrice = midPrice - (rawSpan / 2.0)
    val maxPrice = midPrice + (rawSpan / 2.0)
    val priceRange = if (maxPrice - minPrice > 0) maxPrice - minPrice else 1.0
    val maxVolume = visibleCandles.maxOfOrNull { it.volume } ?: 1.0

    Column(modifier = modifier) {
        // Controls & Scrubber Header
        val activeCandle = selectedIndex?.let { visibleCandles.getOrNull(it) } ?: visibleCandles.lastOrNull() ?: candles.last()
        val dateStr = remember(activeCandle.timestamp) {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            sdf.format(Date(activeCandle.timestamp))
        }

        // Top Info & Controls Bar (Clean non-wrapping header rows)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateStr,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val isCandleUp = activeCandle.close >= activeCandle.open
                    Text(
                        text = "C: $${format2(activeCandle.close)}",
                        color = if (isCandleUp) BullishGreen else BearishRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Overlays Toggle Chips (Pure/Targets, Candles/Full, SMA, Fit All, Reset) - Horizontally scrollable so no wrap or clutter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // Entry & Targets Toggle
                    if (tradeSetupPlan != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showTargetsOverlay) TechCyan.copy(alpha = 0.15f) else DarkSurfaceElevated)
                                .border(1.dp, if (showTargetsOverlay) TechCyan.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(6.dp))
                                .clickable { showTargetsOverlay = !showTargetsOverlay }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("toggle_chart_targets"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = "Toggle Targets",
                                    tint = if (showTargetsOverlay) TechCyan else TextMuted,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = if (showTargetsOverlay) "Targets" else "Pure",
                                    color = if (showTargetsOverlay) TechCyan else TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Focus: Candles vs Full View
                        if (showTargetsOverlay) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (focusCandlesMode) BullishGreen.copy(alpha = 0.15f) else DarkSurfaceElevated)
                                    .border(1.dp, if (focusCandlesMode) BullishGreen.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(6.dp))
                                    .clickable { focusCandlesMode = !focusCandlesMode }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("toggle_focus_mode"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CenterFocusStrong,
                                        contentDescription = "Toggle Focus",
                                        tint = if (focusCandlesMode) BullishGreen else TextMuted,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = if (focusCandlesMode) "Candles" else "Full",
                                        color = if (focusCandlesMode) BullishGreen else TextMuted,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // SMA(14) Toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (showSmaState) BullishGreen.copy(alpha = 0.15f) else DarkSurfaceElevated)
                            .border(1.dp, if (showSmaState) BullishGreen.copy(alpha = 0.5f) else DarkBorder, RoundedCornerShape(6.dp))
                            .clickable { showSmaState = !showSmaState }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("toggle_chart_sma"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = null,
                                tint = if (showSmaState) BullishGreen else TextMuted,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "SMA",
                                color = if (showSmaState) BullishGreen else TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Fit All Data (View 100% of candles)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, TechCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable {
                                if (totalCandles > 0) {
                                    zoomLevel = (baseVisibleCount.toFloat() / totalCandles.toFloat()).coerceIn(0.12f, 1.0f)
                                    panOffset = 0f
                                    verticalPanPercent = 0f
                                    selectedIndex = null
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("fit_all_chart_data"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = "Fit All",
                                tint = TechCyan,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "All",
                                color = TechCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 1-Tap Reset Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                            .clickable {
                                zoomLevel = 1.0f
                                panOffset = maxPan.toFloat()
                                verticalPanPercent = 0f
                                verticalScaleFactor = 1.0f
                                selectedIndex = null
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("reset_chart_view"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Chart",
                                tint = TechCyan,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Reset",
                                color = TechCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Secondary Indicator & Overlay Toggles Row (Zones, Liquidity, VWAP, EMA20/50, VolProfile, All Horizons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val toggleItems = listOf(
                    Triple("Zones", showZonesOverlay) { showZonesOverlay = !showZonesOverlay },
                    Triple("Liquidity", showLiquidityOverlay) { showLiquidityOverlay = !showLiquidityOverlay },
                    Triple("VWAP", showVwapOverlay) { showVwapOverlay = !showVwapOverlay },
                    Triple("EMA20/50", showEmaOverlay) { showEmaOverlay = !showEmaOverlay },
                    Triple("VolProfile", showVolProfileOverlay) { showVolProfileOverlay = !showVolProfileOverlay },
                    Triple("All horizons", showAllHorizonsOverlay) { showAllHorizonsOverlay = !showAllHorizonsOverlay }
                )
                toggleItems.forEach { (label, active, onToggle) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) TechCyan.copy(alpha = 0.16f) else DarkSurfaceElevated)
                            .border(1.dp, if (active) TechCyan.copy(alpha = 0.55f) else DarkBorder, RoundedCornerShape(6.dp))
                            .clickable { onToggle() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("chart_toggle_${label.lowercase(Locale.US).replace(" ", "_").replace("/", "_")}")
                    ) {
                        Text(
                            text = label,
                            color = if (active) TechCyan else TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Open, High, Low metrics row with net change (Horizontal scrollable to never wrap or truncate)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeHorizonPlan != null) {
                    Text(
                        text = "${selectedHorizon.label} (${activeHorizonPlan.setupTf.label})",
                        color = TechCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
                Text("O: ${PriceFormatter.formatCurrency(activeCandle.open)}", color = TextSecondary, fontSize = 9.sp, maxLines = 1)
                Text("H: ${PriceFormatter.formatCurrency(activeCandle.high)}", color = BullishGreen, fontSize = 9.sp, maxLines = 1)
                Text("L: ${PriceFormatter.formatCurrency(activeCandle.low)}", color = BearishRed, fontSize = 9.sp, maxLines = 1)
                Text("Vol: ${String.format(Locale.US, "%.0f", activeCandle.volume)}", color = TextMuted, fontSize = 9.sp, maxLines = 1)
                val cDiff = activeCandle.close - activeCandle.open
                val cPct = if (activeCandle.open > 0) (cDiff / activeCandle.open) * 100 else 0.0
                val cColor = if (cDiff >= 0) BullishGreen else BearishRed
                Text(
                    text = PriceFormatter.formatSignedPercent(cPct, 2),
                    color = cColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
        }

        if (tradeSetupPlan != null && showTargetsOverlay && tradeSetupPlan.isAvailable && tradeSetupPlan.recommendedEntry > 0.0 && tradeSetupPlan.stopLoss > 0.0 && tradeSetupPlan.verdictState != com.example.domain.model.SignalVerdictState.NO_TRADE) {
            val liveP = currentPrice
            val zMin = minOf(zStart ?: tradeSetupPlan.entryZoneStart, zEnd ?: tradeSetupPlan.entryZoneEnd)
            val zMax = maxOf(zStart ?: tradeSetupPlan.entryZoneStart, zEnd ?: tradeSetupPlan.entryZoneEnd)

            val checkCandles = if (visibleCandles.size > 60) visibleCandles.takeLast(60) else visibleCandles
            val seriesLow = checkCandles.minOfOrNull { it.low } ?: liveP
            val seriesHigh = checkCandles.maxOfOrNull { it.high } ?: liveP

            val isStoppedOut = tradeSetupPlan.lifecycleStatus == TradePlanLifecycleStatus.STOPPED_OUT
            val isInvalidated = !isStoppedOut && tradeSetupPlan.lifecycleStatus == TradePlanLifecycleStatus.INVALIDATED

            val isTpReached = !isStoppedOut && !isInvalidated && tradeSetupPlan.lifecycleStatus in listOf(
                TradePlanLifecycleStatus.TP1_REACHED,
                TradePlanLifecycleStatus.TP2_REACHED,
                TradePlanLifecycleStatus.TP3_REACHED
            )

            val isTradeVerdict = tradeSetupPlan.verdictState == com.example.domain.model.SignalVerdictState.TRADE
            val inZone = !isStoppedOut && !isInvalidated && !isTpReached && (liveP in zMin..zMax) && isTradeVerdict

            Spacer(modifier = Modifier.height(4.dp))
            if (isStoppedOut) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BearishRed.copy(alpha = 0.16f))
                        .border(1.dp, BearishRed.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(BearishRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STOP LOSS HIT • INVALIDATED",
                            color = BearishRed,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val wickNote = if (isLong && seriesLow <= tradeSetupPlan.stopLoss) " (Wick: $${format2(seriesLow)})" else if (!isLong && seriesHigh >= tradeSetupPlan.stopLoss) " (Wick: $${format2(seriesHigh)})" else ""
                        Text(
                            text = "SL: $${format2(slVal ?: tradeSetupPlan.stopLoss)}$wickNote",
                            color = TextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BearishRed.copy(alpha = 0.25f))
                            .border(1.dp, BearishRed, RoundedCornerShape(4.dp))
                            .clickable { onRecalculateSetup() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("chart_reanchor_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-anchor Setup",
                                tint = BearishRed,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Re-anchor",
                                color = BearishRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (isInvalidated) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BearishRed.copy(alpha = 0.16f))
                        .border(1.dp, BearishRed.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(BearishRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STRUCTURE INVALIDATED",
                            color = BearishRed,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Inval: $${format2(tradeSetupPlan.invalidationPrice)}",
                            color = TextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BearishRed.copy(alpha = 0.25f))
                            .border(1.dp, BearishRed, RoundedCornerShape(4.dp))
                            .clickable { onRecalculateSetup() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("chart_reanchor_inval_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-anchor Setup",
                                tint = BearishRed,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Re-anchor",
                                color = BearishRed,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (isTpReached) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BullishGreen.copy(alpha = 0.16f))
                        .border(1.dp, BullishGreen.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(BullishGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tradeSetupPlan.lifecycleStatus.name.replace("_", " "),
                            color = BullishGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Target Achieved • Live: $${format2(liveP)}",
                            color = TextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BullishGreen.copy(alpha = 0.25f))
                            .border(1.dp, BullishGreen, RoundedCornerShape(4.dp))
                            .clickable { onRecalculateSetup() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("chart_reanchor_tp_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "New Setup",
                                tint = BullishGreen,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "New Setup",
                                color = BullishGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (inZone) BullishGreen.copy(alpha = 0.12f) else DarkSurfaceElevated)
                        .border(1.dp, if (inZone) BullishGreen.copy(alpha = 0.45f) else DarkBorder, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (inZone) BullishGreen else TechCyan)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (inZone) "IN ENTRY ZONE" else if (!isTradeVerdict) "CONDITIONAL PLAN (WAITING)" else "UPCOMING LIMIT",
                            color = if (inZone) BullishGreen else if (!isTradeVerdict) androidx.compose.ui.graphics.Color(0xFFF59E0B) else TechCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Range: $${format2(zMin)} – $${format2(zMax)}",
                            color = TextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Limit: $${format2(entryVal ?: tradeSetupPlan.recommendedEntry)}",
                        color = TechCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }

        val isLandscape = com.example.ui.adaptive.AdaptiveLayoutUtils.isLandscape
        val isExpanded = com.example.ui.adaptive.AdaptiveLayoutUtils.isExpanded
        val chartCanvasHeight = if (isLandscape) 190.dp else if (isExpanded) 290.dp else 255.dp
        val density = LocalDensity.current
        val hasLevelBadges = tradeSetupPlan != null &&
            showTargetsOverlay &&
            tradeSetupPlan.isAvailable &&
            tradeSetupPlan.recommendedEntry > 0.0 &&
            tradeSetupPlan.stopLoss > 0.0 &&
            tradeSetupPlan.verdictState != SignalVerdictState.NO_TRADE
        val hasCone = (activeHorizonPlan?.replayFanBands?.isNotEmpty() == true) && showTargetsOverlay && hasLevelBadges
        val (rightAxisDp, coneDp) = computeChartMarginsDp(hasLevelBadges = hasLevelBadges, hasCone = hasCone)
        val activeRightMarginPx by rememberUpdatedState(with(density) { (rightAxisDp + coneDp).dp.toPx() })

        // Canvas Chart with Overlays and Gestures
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartCanvasHeight)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (visibleCountNow > 0) {
                                val plotW = (size.width - activeRightMarginPx).coerceAtLeast(100f)
                                if (offset.x in 0f..plotW) {
                                    val cWidth = plotW / visibleCountNow
                                    if (cWidth > 0) {
                                        val idx = (offset.x / cWidth).toInt().coerceIn(0, visibleCountNow - 1)
                                        selectedIndex = if (selectedIndex == idx) null else idx
                                    }
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    // One finger dragging SIDEWAYS scrolls through the candles.
                    // Up/down drags are left alone so the whole page can still scroll normally.
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        val count = ((baseVisibleCount / zoomLevel).toInt()).coerceAtLeast(4).coerceAtMost(totalCandlesNow)
                        val plotW = (size.width - activeRightMarginPx).coerceAtLeast(100f)
                        val cWidth = plotW / count
                        if (cWidth > 0f && dragAmount != 0f) {
                            val maxP = (totalCandlesNow - count).coerceAtLeast(0).toFloat()
                            val current = if (panOffset < 0f) maxP else panOffset.coerceIn(0f, maxP)
                            panOffset = (current - dragAmount / cWidth).coerceIn(0f, maxP)
                        }
                    }
                }
                .pointerInput(Unit) {
                    // Two fingers pinching = zoom. One-finger touches are never taken over here.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.size >= 2) {
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) {
                                    zoomLevel = (zoomLevel * zoom).coerceIn(0.12f, 6.0f)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val chartHeight = canvasHeight * 0.80f
            val volumeHeight = canvasHeight * 0.20f
            val rightBadgeMargin = with(density) { rightAxisDp.dp.toPx() }
            val coneMargin = with(density) { coneDp.dp.toPx() }
            val plotWidth = (canvasWidth - rightBadgeMargin - coneMargin).coerceAtLeast(100f)
            val axisLeftX = plotWidth + coneMargin
            val candleCount = visibleCandles.size
            val candleWidth = plotWidth / candleCount

            fun priceToY(price: Double): Float {
                return (chartHeight - ((price - minPrice) / priceRange * chartHeight).toFloat()).coerceIn(0f, chartHeight)
            }

            // Subtle horizontal price gridlines + right Y-axis divider
            val gridFracs = floatArrayOf(0.12f, 0.37f, 0.62f, 0.87f)
            for (frac in gridFracs) {
                val gy = chartHeight * frac
                drawLine(
                    color = DarkBorder.copy(alpha = 0.55f),
                    start = Offset(0f, gy),
                    end = Offset(axisLeftX, gy),
                    strokeWidth = 0.9f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                )
            }
            drawLine(
                color = DarkBorder.copy(alpha = 0.7f),
                start = Offset(axisLeftX, 0f),
                end = Offset(axisLeftX, chartHeight),
                strokeWidth = 1f
            )

            // OVERLAY: Volume Profile horizontal histogram (16 buckets across visible range)
            if (showVolProfileOverlay && visibleCandles.isNotEmpty() && priceRange > 0.0) {
                val buckets = 16
                val volProfile = DoubleArray(buckets)
                visibleCandles.forEach { c ->
                    val b = (((c.close - minPrice) / priceRange) * (buckets - 1)).toInt().coerceIn(0, buckets - 1)
                    volProfile[b] += c.volume
                }
                val maxBucketVol = volProfile.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                val bucketH = chartHeight / buckets
                val maxHistW = plotWidth * 0.20f
                for (b in 0 until buckets) {
                    val w = ((volProfile[b] / maxBucketVol) * maxHistW).toFloat()
                    val yTop = chartHeight - ((b + 1) * bucketH)
                    drawRect(
                        color = TechCyan.copy(alpha = 0.12f),
                        topLeft = Offset(0f, yTop),
                        size = Size(w, maxOf(bucketH - 1f, 1f))
                    )
                }
            }

            // OVERLAY: Optimal Entry Zone Band + Invalidation shading
            if (showZonesOverlay && showTargetsOverlay && entryVal != null && slVal != null &&
                (activeHorizonPlan?.state ?: tradeSetupPlan?.verdictState) != SignalVerdictState.NO_TRADE
            ) {
                val zoneTopPrice = maxOf(zStart ?: entryVal, zEnd ?: entryVal)
                val zoneBottomPrice = minOf(zStart ?: entryVal, zEnd ?: entryVal)
                val zoneTopY = priceToY(zoneTopPrice)
                val zoneBottomY = priceToY(zoneBottomPrice)
                val zoneHeight = maxOf(zoneBottomY - zoneTopY, 4f)
                val zoneColor = if (isLong) BullishGreen else BearishRed

                drawRect(
                    color = zoneColor.copy(alpha = 0.16f),
                    topLeft = Offset(0f, zoneTopY),
                    size = Size(plotWidth, zoneHeight)
                )
                drawLine(
                    color = zoneColor.copy(alpha = 0.70f),
                    start = Offset(0f, zoneTopY),
                    end = Offset(plotWidth + coneMargin, zoneTopY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
                drawLine(
                    color = zoneColor.copy(alpha = 0.70f),
                    start = Offset(0f, zoneBottomY),
                    end = Offset(plotWidth + coneMargin, zoneBottomY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )

                // Invalidation shading from zone far edge to SL
                if (slVal in minPrice..maxPrice) {
                    val slY = priceToY(slVal)
                    val invStartY = if (isLong) zoneBottomY else slY
                    val invEndY = if (isLong) slY else zoneTopY
                    if (invEndY > invStartY) {
                        drawRect(
                            color = BearishRed.copy(alpha = 0.09f),
                            topLeft = Offset(0f, invStartY),
                            size = Size(plotWidth, invEndY - invStartY)
                        )
                    }
                    drawLine(
                        color = BearishRed.copy(alpha = 0.85f),
                        start = Offset(0f, slY),
                        end = Offset(canvasWidth - rightBadgeMargin, slY),
                        strokeWidth = 1.4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                }

                if (effectiveTp1 != null && effectiveTp1 > 0 && effectiveTp1 in minPrice..maxPrice) {
                    val tp1Y = priceToY(effectiveTp1)
                    drawLine(
                        color = BullishGreen.copy(alpha = 0.85f),
                        start = Offset(0f, tp1Y),
                        end = Offset(canvasWidth - rightBadgeMargin, tp1Y),
                        strokeWidth = 1.3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                }

                if (effectiveTp2 != null && effectiveTp2 > 0 && effectiveTp2 in minPrice..maxPrice) {
                    val tp2Y = priceToY(effectiveTp2)
                    drawLine(
                        color = BullishGreen.copy(alpha = 0.70f),
                        start = Offset(0f, tp2Y),
                        end = Offset(canvasWidth - rightBadgeMargin, tp2Y),
                        strokeWidth = 1.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                }

                if (effectiveTp3 != null && effectiveTp3 > 0 && effectiveTp3 in minPrice..maxPrice) {
                    val tp3Y = priceToY(effectiveTp3)
                    drawLine(
                        color = TechCyan.copy(alpha = 0.70f),
                        start = Offset(0f, tp3Y),
                        end = Offset(canvasWidth - rightBadgeMargin, tp3Y),
                        strokeWidth = 1.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                    )
                }

                if (entryVal in minPrice..maxPrice) {
                    val entryY = priceToY(entryVal)
                    drawLine(
                        color = TechCyan.copy(alpha = 0.85f),
                        start = Offset(0f, entryY),
                        end = Offset(canvasWidth - rightBadgeMargin, entryY),
                        strokeWidth = 1.4f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    )
                }
            }

            // OVERLAY: Liquidity BSL (> price) & SSL (< price) lines
            val refLive = if (currentPrice > 0.0) currentPrice else lastCandleClose
            val bslPrice = activeHorizonPlan?.bslLevel?.takeIf { it > refLive && it in minPrice..maxPrice }
            val sslPrice = activeHorizonPlan?.sslLevel?.takeIf { it < refLive && it in minPrice..maxPrice }
            if (showLiquidityOverlay) {
                bslPrice?.let { bsl ->
                    val y = priceToY(bsl)
                    drawLine(
                        color = RiskAmber.copy(alpha = 0.65f),
                        start = Offset(0f, y),
                        end = Offset(plotWidth, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f), 0f)
                    )
                }
                sslPrice?.let { ssl ->
                    val y = priceToY(ssl)
                    drawLine(
                        color = AgentPurple.copy(alpha = 0.65f),
                        start = Offset(0f, y),
                        end = Offset(plotWidth, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f), 0f)
                    )
                }
            }

            // OVERLAY: All horizons faint levels
            if (showAllHorizonsOverlay && masterVerdict != null) {
                masterVerdict.horizonPlans.forEach { (h, hp) ->
                    if (h != selectedHorizon && h != TradingHorizon.OUTLOOK) {
                        hp.limitPrice?.takeIf { it > 0.0 && it in minPrice..maxPrice }?.let { lim ->
                            val y = priceToY(lim)
                            drawLine(
                                color = TechCyan.copy(alpha = 0.25f),
                                start = Offset(0f, y),
                                end = Offset(plotWidth, y),
                                strokeWidth = 0.9f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 6f), 0f)
                            )
                        }
                    }
                }
            }

            // Draw visible historical candles and volume bars + EMA20/EMA50 + Anchored VWAP
            val smaPoints = mutableListOf<Offset>()
            val ema20Points = mutableListOf<Offset>()
            val ema50Points = mutableListOf<Offset>()
            val vwapPoints = mutableListOf<Offset>()
            val smaWindow = 14
            var ema20Val = visibleCandles.first().close
            var ema50Val = visibleCandles.first().close
            var cumPv = 0.0
            var cumVol = 0.0
            val k20 = 2.0 / 21.0
            val k50 = 2.0 / 51.0

            visibleCandles.forEachIndexed { index, candle ->
                val xCenter = (index * candleWidth) + (candleWidth / 2f)

                val highY = priceToY(candle.high)
                val lowY = priceToY(candle.low)
                val openY = priceToY(candle.open)
                val closeY = priceToY(candle.close)

                val isBullish = candle.close >= candle.open
                val candleColor = if (isBullish) BullishGreen else BearishRed

                drawLine(
                    color = candleColor,
                    start = Offset(xCenter, highY),
                    end = Offset(xCenter, lowY),
                    strokeWidth = 2f
                )

                val bodyTop = minOf(openY, closeY)
                val bodyBottom = maxOf(openY, closeY)
                val bodyHeight = maxOf(bodyBottom - bodyTop, 2f)
                val bodyWidth = maxOf(candleWidth * 0.72f, 3f)

                drawRect(
                    color = candleColor,
                    topLeft = Offset(xCenter - (bodyWidth / 2f), bodyTop),
                    size = Size(bodyWidth, bodyHeight)
                )

                val volRatio = (candle.volume / maxVolume).toFloat().coerceIn(0f, 1f)
                val volBarHeight = volumeHeight * volRatio
                val volTop = canvasHeight - volBarHeight
                drawRect(
                    color = candleColor.copy(alpha = 0.35f),
                    topLeft = Offset(xCenter - (bodyWidth / 2f), volTop),
                    size = Size(bodyWidth, volBarHeight)
                )

                if (showSmaState && index >= smaWindow - 1) {
                    val subset = visibleCandles.subList(index - smaWindow + 1, index + 1)
                    val avgClose = subset.map { it.close }.average()
                    smaPoints.add(Offset(xCenter, priceToY(avgClose)))
                }

                if (index == 0) {
                    ema20Val = candle.close
                    ema50Val = candle.close
                } else {
                    ema20Val = candle.close * k20 + ema20Val * (1.0 - k20)
                    ema50Val = candle.close * k50 + ema50Val * (1.0 - k50)
                }
                if (showEmaOverlay) {
                    ema20Points.add(Offset(xCenter, priceToY(ema20Val)))
                    ema50Points.add(Offset(xCenter, priceToY(ema50Val)))
                }
                if (showVwapOverlay) {
                    val tp = (candle.high + candle.low + candle.close) / 3.0
                    val v = candle.volume.coerceAtLeast(1.0)
                    cumPv += tp * v
                    cumVol += v
                    vwapPoints.add(Offset(xCenter, priceToY(cumPv / cumVol)))
                }
            }

            if (showSmaState && smaPoints.size > 1) {
                val path = Path().apply {
                    moveTo(smaPoints.first().x, smaPoints.first().y)
                    for (i in 1 until smaPoints.size) lineTo(smaPoints[i].x, smaPoints[i].y)
                }
                drawPath(path = path, color = TechCyan.copy(alpha = 0.7f), style = Stroke(width = 2.0f))
            }

            if (showEmaOverlay && ema20Points.size > 1) {
                val p20 = Path().apply {
                    moveTo(ema20Points.first().x, ema20Points.first().y)
                    for (i in 1 until ema20Points.size) lineTo(ema20Points[i].x, ema20Points[i].y)
                }
                drawPath(path = p20, color = RiskAmber.copy(alpha = 0.78f), style = Stroke(width = 1.6f))
            }
            if (showEmaOverlay && ema50Points.size > 1) {
                val p50 = Path().apply {
                    moveTo(ema50Points.first().x, ema50Points.first().y)
                    for (i in 1 until ema50Points.size) lineTo(ema50Points[i].x, ema50Points[i].y)
                }
                drawPath(path = p50, color = AgentPurple.copy(alpha = 0.78f), style = Stroke(width = 1.6f))
            }
            if (showVwapOverlay && vwapPoints.size > 1) {
                val pv = Path().apply {
                    moveTo(vwapPoints.first().x, vwapPoints.first().y)
                    for (i in 1 until vwapPoints.size) lineTo(vwapPoints[i].x, vwapPoints[i].y)
                }
                drawPath(path = pv, color = Color.White.copy(alpha = 0.8f), style = Stroke(width = 1.8f))
            }

            // "now" vertical divider at last candle + Replay Forecast Cone (p5..p95)
            val nowX = plotWidth
            drawLine(
                color = Color.White.copy(alpha = 0.30f),
                start = Offset(nowX, 0f),
                end = Offset(nowX, chartHeight),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f), 0f)
            )
            if (hasCone && coneMargin > 8f) {
                val bands = activeHorizonPlan!!.replayFanBands
                val p95Path = Path()
                val p50Path = Path()
                val p5Path = Path()
                val fillPath = Path()
                val anchorPrice = entryVal ?: refLive
                val anchorY = priceToY(anchorPrice)
                p95Path.moveTo(nowX, anchorY)
                p50Path.moveTo(nowX, anchorY)
                p5Path.moveTo(nowX, anchorY)
                fillPath.moveTo(nowX, anchorY)
                bands.forEachIndexed { i, pt ->
                    val frac = (i + 1).toFloat() / bands.size.toFloat()
                    val x = nowX + frac * coneMargin
                    p95Path.lineTo(x, priceToY(pt.p95))
                    p50Path.lineTo(x, priceToY(pt.p50))
                    p5Path.lineTo(x, priceToY(pt.p5))
                    fillPath.lineTo(x, priceToY(pt.p95))
                }
                for (i in bands.indices.reversed()) {
                    val frac = (i + 1).toFloat() / bands.size.toFloat()
                    val x = nowX + frac * coneMargin
                    fillPath.lineTo(x, priceToY(bands[i].p5))
                }
                fillPath.close()
                drawPath(path = fillPath, color = TechCyan.copy(alpha = 0.12f))
                drawPath(
                    path = p95Path,
                    color = TechCyan.copy(alpha = 0.65f),
                    style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
                )
                drawPath(
                    path = p5Path,
                    color = BearishRed.copy(alpha = 0.60f),
                    style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f))
                )
                drawPath(path = p50Path, color = BullishGreen.copy(alpha = 0.75f), style = Stroke(width = 1.4f))

                val notePaint = Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.LTGRAY
                    textSize = with(density) { 7.5.sp.toPx() }
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "replay range, not a prediction",
                    (nowX - with(density) { 105.dp.toPx() }).coerceAtLeast(4f),
                    with(density) { 11.dp.toPx() },
                    notePaint
                )
            }

            // OVERLAY: Live Price Tracker Line
            val liveMarkerPrice = if (currentPrice > 0.0) currentPrice else visibleCandles.lastOrNull()?.close ?: 0.0
            if (liveMarkerPrice > 0.0) {
                val liveY = priceToY(liveMarkerPrice)
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(0f, liveY),
                    end = Offset(axisLeftX, liveY),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )
                drawCircle(
                    color = TechCyan,
                    radius = 3.5f,
                    center = Offset(plotWidth, liveY)
                )
            }

            // Always draw crisp right-edge Y-axis price ticks (behind trade badges, or full scale when no trade badges)
            if (!hasLevelBadges && priceRange > 0.0) {
                val axisPaint = Paint().apply {
                    isAntiAlias = true
                    color = TextMuted.toArgb()
                    textSize = with(density) { 8.5.sp.toPx() }
                }
                val liveYClamped = if (liveMarkerPrice > 0.0) priceToY(liveMarkerPrice) else -999f
                val padX = axisLeftX + with(density) { 4.dp.toPx() }
                for (frac in gridFracs) {
                    val gy = chartHeight * frac
                    if (kotlin.math.abs(gy - liveYClamped) > with(density) { 14.dp.toPx() }) {
                        val gridPrice = maxPrice - (frac * priceRange)
                        val tickLabel = if (gridPrice >= 1000.0) {
                            String.format(Locale.US, "$%.0f", gridPrice)
                        } else {
                            "$${format2(gridPrice)}"
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            tickLabel,
                            padX,
                            (gy + with(density) { 3.dp.toPx() }).coerceIn(with(density) { 10.dp.toPx() }, chartHeight - 2f),
                            axisPaint
                        )
                    }
                }
                if (liveMarkerPrice > 0.0) {
                    val pillH = with(density) { 16.dp.toPx() }
                    val pillY = (liveYClamped - pillH / 2f).coerceIn(1f, chartHeight - pillH - 1f)
                    val pillW = (canvasWidth - padX - 1f).coerceAtLeast(with(density) { 38.dp.toPx() })
                    val cr = with(density) { 3.dp.toPx() }
                    drawRoundRect(
                        color = Color(0xFF0E2233),
                        topLeft = Offset(padX - 2f, pillY),
                        size = Size(pillW, pillH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr, cr)
                    )
                    drawRoundRect(
                        color = TechCyan.copy(alpha = 0.85f),
                        topLeft = Offset(padX - 2f, pillY),
                        size = Size(pillW, pillH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr, cr),
                        style = Stroke(width = with(density) { 1.dp.toPx() })
                    )
                    val livePaint = Paint().apply {
                        isAntiAlias = true
                        color = TechCyan.toArgb()
                        textSize = with(density) { 8.5.sp.toPx() }
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    val liveStr = if (liveMarkerPrice >= 1000.0) String.format(Locale.US, "$%.0f", liveMarkerPrice) else "$${format2(liveMarkerPrice)}"
                    drawContext.canvas.nativeCanvas.drawText(
                        liveStr,
                        padX + with(density) { 2.dp.toPx() },
                        pillY + pillH * 0.72f,
                        livePaint
                    )
                }
            }

            // Draw right margin level badges using nativeCanvas with anti-collision layout
            if (hasLevelBadges && tradeSetupPlan != null) {
                val badgeTextSize = with(density) { 9.sp.toPx() }
                val badgeHeight = with(density) { 18.dp.toPx() }
                val textBaselineOffset = badgeHeight * 0.72f
                val cornerRadiusPx = with(density) { 4.dp.toPx() }
                val minGapPx = with(density) { 2.dp.toPx() }

                val textPaint = Paint().apply {
                    isAntiAlias = true
                    textSize = badgeTextSize
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }

                val specs = mutableListOf<LevelBadgeSpec>()

                val isStoppedRight = tradeSetupPlan.lifecycleStatus == TradePlanLifecycleStatus.STOPPED_OUT
                val isInvalidatedRight = !isStoppedRight && tradeSetupPlan.lifecycleStatus == TradePlanLifecycleStatus.INVALIDATED

                // 1. ENTRY/LIMIT
                val inZoneRight = !isStoppedRight && !isInvalidatedRight && currentPrice in minOf(tradeSetupPlan.entryZoneStart, tradeSetupPlan.entryZoneEnd)..maxOf(tradeSetupPlan.entryZoneStart, tradeSetupPlan.entryZoneEnd) && (tradeSetupPlan.verdictState == com.example.domain.model.SignalVerdictState.TRADE)
                val entryPrice = entryVal ?: tradeSetupPlan.recommendedEntry
                val inBoundsEntry = entryPrice in minPrice..maxPrice
                val isAboveEntry = entryPrice > maxPrice
                val isBelowEntry = entryPrice < minPrice
                val entryTrueY = if (inBoundsEntry) priceToY(entryPrice) else null
                val entryIdealY = when {
                    inBoundsEntry -> priceToY(entryPrice)
                    isAboveEntry -> 2f
                    else -> chartHeight - badgeHeight - 2f
                }
                val entryPrefix = when {
                    isStoppedRight -> "STOPPED"
                    isInvalidatedRight -> "INVAL"
                    inZoneRight -> "ENTRY"
                    else -> "LIMIT"
                }
                val entryLabel = when {
                    inBoundsEntry -> "$entryPrefix $${format2(entryPrice)}"
                    isAboveEntry -> "▲ $entryPrefix $${format2(entryPrice)}"
                    else -> "▼ $entryPrefix $${format2(entryPrice)}"
                }
                specs.add(
                    LevelBadgeSpec(
                        id = if (inZoneRight) "ENTRY" else "LIMIT",
                        label = entryLabel,
                        price = entryPrice,
                        idealY = entryIdealY,
                        isPinnedTop = isAboveEntry,
                        isPinnedBottom = isBelowEntry,
                        textColor = if (isStoppedRight || isInvalidatedRight) TextMuted else TechCyan,
                        bgColor = if (isStoppedRight || isInvalidatedRight) Color(0xFF14171E) else Color(0xFF0F1B2A),
                        strokeColor = if (isStoppedRight || isInvalidatedRight) TextMuted.copy(alpha = 0.4f) else TechCyan,
                        trueLineY = entryTrueY
                    )
                )

                // 2. TP1
                if (effectiveTp1 != null && effectiveTp1 > 0) {
                    val inBounds = effectiveTp1 in minPrice..maxPrice
                    val isAbove = effectiveTp1 > maxPrice
                    val isBelow = effectiveTp1 < minPrice
                    val trueY = if (inBounds) priceToY(effectiveTp1) else null
                    val idealY = when {
                        inBounds -> priceToY(effectiveTp1)
                        isAbove -> 2f
                        else -> chartHeight - badgeHeight - 2f
                    }
                    val label = when {
                        inBounds -> "TP1 $${format2(effectiveTp1)}"
                        isAbove -> "▲ TP1 $${format2(effectiveTp1)}"
                        else -> "▼ TP1 $${format2(effectiveTp1)}"
                    }
                    specs.add(
                        LevelBadgeSpec(
                            id = "TP1",
                            label = label,
                            price = effectiveTp1,
                            idealY = idealY,
                            isPinnedTop = isAbove,
                            isPinnedBottom = isBelow,
                            textColor = BullishGreen,
                            bgColor = Color(0xFF092014),
                            strokeColor = BullishGreen.copy(alpha = 0.7f),
                            trueLineY = trueY
                        )
                    )
                }

                // 3. TP2
                if (effectiveTp2 != null && effectiveTp2 > 0) {
                    val inBounds = effectiveTp2 in minPrice..maxPrice
                    val isAbove = effectiveTp2 > maxPrice
                    val isBelow = effectiveTp2 < minPrice
                    val trueY = if (inBounds) priceToY(effectiveTp2) else null
                    val idealY = when {
                        inBounds -> priceToY(effectiveTp2)
                        isAbove -> 2f
                        else -> chartHeight - badgeHeight - 2f
                    }
                    val label = when {
                        inBounds -> "TP2 $${format2(effectiveTp2)}"
                        isAbove -> "▲ TP2 $${format2(effectiveTp2)}"
                        else -> "▼ TP2 $${format2(effectiveTp2)}"
                    }
                    specs.add(
                        LevelBadgeSpec(
                            id = "TP2",
                            label = label,
                            price = effectiveTp2,
                            idealY = idealY,
                            isPinnedTop = isAbove,
                            isPinnedBottom = isBelow,
                            textColor = BullishGreen,
                            bgColor = Color(0xFF092014),
                            strokeColor = BullishGreen.copy(alpha = 0.6f),
                            trueLineY = trueY
                        )
                    )
                }

                // 4. TP3
                if (effectiveTp3 != null && effectiveTp3 > 0) {
                    val inBounds = effectiveTp3 in minPrice..maxPrice
                    val isAbove = effectiveTp3 > maxPrice
                    val isBelow = effectiveTp3 < minPrice
                    val trueY = if (inBounds) priceToY(effectiveTp3) else null
                    val idealY = when {
                        inBounds -> priceToY(effectiveTp3)
                        isAbove -> 2f
                        else -> chartHeight - badgeHeight - 2f
                    }
                    val label = when {
                        inBounds -> "TP3 $${format2(effectiveTp3)}"
                        isAbove -> "▲ TP3 $${format2(effectiveTp3)}"
                        else -> "▼ TP3 $${format2(effectiveTp3)}"
                    }
                    specs.add(
                        LevelBadgeSpec(
                            id = "TP3",
                            label = label,
                            price = effectiveTp3,
                            idealY = idealY,
                            isPinnedTop = isAbove,
                            isPinnedBottom = isBelow,
                            textColor = TechCyan,
                            bgColor = Color(0xFF091F2C),
                            strokeColor = TechCyan.copy(alpha = 0.6f),
                            trueLineY = trueY
                        )
                    )
                }

                // 5. SL
                if ((slVal ?: tradeSetupPlan.stopLoss) > 0) {
                    val slPrice = slVal ?: tradeSetupPlan.stopLoss
                    val inBounds = slPrice in minPrice..maxPrice
                    val isAbove = slPrice > maxPrice
                    val isBelow = slPrice < minPrice
                    val trueY = if (inBounds) priceToY(slPrice) else null
                    val idealY = when {
                        inBounds -> priceToY(slPrice)
                        isAbove -> 2f
                        else -> chartHeight - badgeHeight - 2f
                    }
                    val slPrefix = if (isStoppedRight) "HIT" else "SL"
                    val label = when {
                        inBounds -> "$slPrefix $${format2(slPrice)}"
                        isAbove -> "▲ $slPrefix $${format2(slPrice)}"
                        else -> "▼ $slPrefix $${format2(slPrice)}"
                    }
                    specs.add(
                        LevelBadgeSpec(
                            id = "SL",
                            label = label,
                            price = slPrice,
                            idealY = idealY,
                            isPinnedTop = isAbove,
                            isPinnedBottom = isBelow,
                            textColor = BearishRed,
                            bgColor = if (isStoppedRight) Color(0xFF3D1418) else Color(0xFF281114),
                            strokeColor = if (isStoppedRight) BearishRed else BearishRed.copy(alpha = 0.7f),
                            trueLineY = trueY
                        )
                    )
                }

                val positionedBadges = ChartBadgeLayoutHelper.computeChartBadgeLayout(
                    specs = specs,
                    chartHeight = chartHeight,
                    badgeHeight = badgeHeight,
                    minGapPx = minGapPx,
                    canvasWidth = canvasWidth,
                    measureTextWidth = { textPaint.measureText(it) }
                )

                for (badge in positionedBadges) {
                    if (badge.drawLeaderLine && badge.trueLineY != null) {
                        val badgeMidY = badge.topY + (badgeHeight / 2f)
                        drawLine(
                            color = badge.strokeColor.copy(alpha = 0.8f),
                            start = Offset(badge.leftX, badgeMidY),
                            end = Offset(badge.leftX - with(density) { 10.dp.toPx() }, badge.trueLineY),
                            strokeWidth = with(density) { 1.dp.toPx() }
                        )
                    }

                    drawRoundRect(
                        color = badge.bgColor,
                        topLeft = Offset(badge.leftX, badge.topY),
                        size = Size(badge.width, badge.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                    drawRoundRect(
                        color = badge.strokeColor,
                        topLeft = Offset(badge.leftX, badge.topY),
                        size = Size(badge.width, badge.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx, cornerRadiusPx),
                        style = Stroke(width = with(density) { 1.dp.toPx() })
                    )

                    textPaint.color = badge.textColor.toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        badge.label,
                        badge.leftX + with(density) { 5.dp.toPx() },
                        badge.topY + textBaselineOffset,
                        textPaint
                    )
                }
            }

            // Draw scrubber crosshair if active
            selectedIndex?.let { idx ->
                val x = (idx * candleWidth) + (candleWidth / 2f)
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(x, 0f),
                    end = Offset(x, canvasHeight),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }
        }

        // Compact levels table under chart
        if (tradeSetupPlan != null && showTargetsOverlay && tradeSetupPlan.isAvailable && tradeSetupPlan.recommendedEntry > 0.0 && tradeSetupPlan.stopLoss > 0.0 && tradeSetupPlan.verdictState != com.example.domain.model.SignalVerdictState.NO_TRADE) {
            Spacer(modifier = Modifier.height(8.dp))
            CompactLevelsTable(tradeSetupPlan = tradeSetupPlan, currentPrice = currentPrice)
        }

        // Visual legend row below chart
        if (tradeSetupPlan != null && showTargetsOverlay && tradeSetupPlan.isAvailable && tradeSetupPlan.recommendedEntry > 0.0 && tradeSetupPlan.stopLoss > 0.0 && tradeSetupPlan.verdictState != com.example.domain.model.SignalVerdictState.NO_TRADE) {
            val zStart = minOf(tradeSetupPlan.entryZoneStart, tradeSetupPlan.entryZoneEnd)
            val zEnd = maxOf(tradeSetupPlan.entryZoneStart, tradeSetupPlan.entryZoneEnd)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp, 3.dp)
                            .background(TechCyan)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limit: $${format2(tradeSetupPlan.recommendedEntry)}", fontSize = 9.sp, color = TechCyan, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(10.dp, 8.dp)
                            .background(BullishGreen.copy(alpha = 0.25f))
                            .border(1.dp, BullishGreen.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Zone: $${format2(zStart)}–$${format2(zEnd)}", fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp, 2.dp)
                            .background(BullishGreen)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("TP1", fontSize = 9.sp, color = BullishGreen, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(8.dp, 2.dp)
                            .background(BullishGreen)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("TP2", fontSize = 9.sp, color = BullishGreen, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(8.dp, 2.dp)
                            .background(TechCyan)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("TP3", fontSize = 9.sp, color = TechCyan, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(8.dp, 2.dp)
                            .background(BearishRed)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("SL", fontSize = 9.sp, color = BearishRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class LevelTableItem(
    val label: String,
    val price: Double,
    val distanceText: String,
    val rMultiple: String,
    val badgeColor: Color,
    val bgColor: Color
)

@Composable
fun CompactLevelsTable(
    tradeSetupPlan: TradeSetupPlan,
    currentPrice: Double,
    modifier: Modifier = Modifier
) {
    val recEntry = tradeSetupPlan.recommendedEntry
    if (recEntry <= 0.0) return

    val isLong = tradeSetupPlan.side == PositionSide.LONG

    val entryDist = if (currentPrice > 0.0) ((currentPrice - recEntry) / recEntry) * 100.0 else 0.0
    val slDist = ((tradeSetupPlan.stopLoss - recEntry) / recEntry) * 100.0

    val entryPrice = recEntry
    val slRange = kotlin.math.abs(recEntry - tradeSetupPlan.stopLoss)

    // Sort targets strictly by distance from entry (TP1 always nearest)
    val sortedTargets = listOf(tradeSetupPlan.takeProfit1, tradeSetupPlan.takeProfit2, tradeSetupPlan.takeProfit3)
        .filter { it > 0.0 }
        .distinct()
        .sortedBy { kotlin.math.abs(it - entryPrice) }

    val resolvedTp1 = sortedTargets.getOrNull(0) ?: (if (isLong) entryPrice + (slRange * 1.5) else entryPrice - (slRange * 1.5))
    val resolvedTp2 = sortedTargets.getOrNull(1) ?: (if (isLong) resolvedTp1 + (slRange * 1.0) else resolvedTp1 - (slRange * 1.0))
    val resolvedTp3 = sortedTargets.getOrNull(2) ?: (if (isLong) resolvedTp2 + (slRange * 1.5) else resolvedTp2 - (slRange * 1.5))

    val tp1Dist = ((resolvedTp1 - recEntry) / recEntry) * 100.0
    val tp2Dist = ((resolvedTp2 - recEntry) / recEntry) * 100.0
    val tp3Dist = ((resolvedTp3 - recEntry) / recEntry) * 100.0

    val r1 = if (slRange > 0.0) kotlin.math.abs(resolvedTp1 - entryPrice) / slRange else 1.5
    val r2 = if (slRange > 0.0) kotlin.math.abs(resolvedTp2 - entryPrice) / slRange else 2.5
    val r3 = if (slRange > 0.0) kotlin.math.abs(resolvedTp3 - entryPrice) / slRange else 4.0

    val levels = listOf(
        LevelTableItem(
            label = if (isLong) "ENTRY" else "LIMIT",
            price = recEntry,
            distanceText = if (currentPrice > 0.0) PriceFormatter.formatSignedPercent(entryDist, 2) else "-",
            rMultiple = "0.0R",
            badgeColor = TechCyan,
            bgColor = Color(0xFF0F1B2A)
        ),
        LevelTableItem(
            label = "SL",
            price = tradeSetupPlan.stopLoss,
            distanceText = "${if (slDist >= 0) "+" else ""}${String.format(Locale.US, "%.2f", slDist)}%",
            rMultiple = "-1.0R",
            badgeColor = BearishRed,
            bgColor = Color(0xFF281114)
        ),
        LevelTableItem(
            label = "TP1",
            price = resolvedTp1,
            distanceText = "${if (tp1Dist >= 0) "+" else ""}${String.format(Locale.US, "%.2f", tp1Dist)}%",
            rMultiple = String.format(Locale.US, "+%.1fR", r1),
            badgeColor = BullishGreen,
            bgColor = Color(0xFF092014)
        ),
        LevelTableItem(
            label = "TP2",
            price = resolvedTp2,
            distanceText = "${if (tp2Dist >= 0) "+" else ""}${String.format(Locale.US, "%.2f", tp2Dist)}%",
            rMultiple = String.format(Locale.US, "+%.1fR", r2),
            badgeColor = BullishGreen,
            bgColor = Color(0xFF092014)
        ),
        LevelTableItem(
            label = "TP3",
            price = resolvedTp3,
            distanceText = "${if (tp3Dist >= 0) "+" else ""}${String.format(Locale.US, "%.2f", tp3Dist)}%",
            rMultiple = String.format(Locale.US, "+%.1fR", r3),
            badgeColor = TechCyan,
            bgColor = Color(0xFF091F2C)
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceElevated)
            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
            .testTag("compact_levels_table")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            levels.forEach { item ->
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(item.bgColor)
                        .border(1.dp, item.badgeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(item.badgeColor.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = item.label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = item.badgeColor
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.rMultiple,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = PriceFormatter.formatCurrency(item.price),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    if (item.label.contains("ENTRY") || item.label.contains("LIMIT")) {
                        val zMin = minOf(tradeSetupPlan.entryZoneStart, tradeSetupPlan.entryZoneEnd)
                        val zMax = maxOf(tradeSetupPlan.entryZoneStart, tradeSetupPlan.entryZoneEnd)
                        if (zMin > 0 && zMax > 0 && zMax > zMin) {
                            Text(
                                text = PriceFormatter.formatZoneRange(zMin, zMax),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = TechCyan
                            )
                        }
                    }
                    Text(
                        text = item.distanceText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (item.distanceText.startsWith("-")) BearishRed else BullishGreen
                    )
                }
            }
        }
    }
}

data class LevelBadgeSpec(
    val id: String,
    val label: String,
    val price: Double,
    val idealY: Float,
    val isPinnedTop: Boolean,
    val isPinnedBottom: Boolean,
    val textColor: Color,
    val bgColor: Color,
    val strokeColor: Color,
    val trueLineY: Float? = null
)

data class PositionedBadge(
    val id: String,
    val label: String,
    val topY: Float,
    val leftX: Float,
    val width: Float,
    val height: Float,
    val textColor: Color,
    val bgColor: Color,
    val strokeColor: Color,
    val trueLineY: Float? = null,
    val drawLeaderLine: Boolean = false
)

object ChartBadgeLayoutHelper {
    fun computeChartBadgeLayout(
        specs: List<LevelBadgeSpec>,
        chartHeight: Float,
        badgeHeight: Float,
        minGapPx: Float,
        canvasWidth: Float,
        measureTextWidth: (String) -> Float
    ): List<PositionedBadge> {
        if (specs.isEmpty()) return emptyList()

        val step = badgeHeight + minGapPx
        val maxBadgesCanFit = (chartHeight / step).toInt().coerceAtLeast(1)

        val workingSpecs = specs.sortedBy { it.idealY }

        // Highest price always on top (TP3 above TP2 above TP1 ...), so the stack reads like a price ladder
        val topPinned = workingSpecs.filter { it.isPinnedTop }.sortedByDescending { it.price }
        val bottomPinned = workingSpecs.filter { it.isPinnedBottom }.sortedByDescending { it.price }
        val inBounds = workingSpecs.filter { !it.isPinnedTop && !it.isPinnedBottom }

        val topMargin = if (topPinned.isNotEmpty()) topPinned.size * step + minGapPx else 2f
        val bottomMargin = if (bottomPinned.isNotEmpty()) bottomPinned.size * step + minGapPx else 2f
        val inBoundsTopLimit = topMargin
        val inBoundsBottomLimit = (chartHeight - badgeHeight - bottomMargin).coerceAtLeast(inBoundsTopLimit)

        val resolvedInBounds = layoutNonOverlappingLabelYs(
            idealYs = inBounds.map { it.idealY - (badgeHeight / 2f) },
            minY = inBoundsTopLimit,
            maxY = inBoundsBottomLimit,
            minSpacingPx = step
        )
        val inBoundsPositions = FloatArray(inBounds.size) { idx -> resolvedInBounds[idx] }

        val result = mutableListOf<PositionedBadge>()

        for (idx in topPinned.indices) {
            val spec = topPinned[idx]
            val y = 2f + (idx * step)
            val textWidth = measureTextWidth(spec.label)
            val width = textWidth + 12f
            val left = canvasWidth - width - 2f
            result.add(
                PositionedBadge(
                    id = spec.id,
                    label = spec.label,
                    topY = y,
                    leftX = left,
                    width = width,
                    height = badgeHeight,
                    textColor = spec.textColor,
                    bgColor = spec.bgColor,
                    strokeColor = spec.strokeColor,
                    trueLineY = spec.trueLineY,
                    drawLeaderLine = false
                )
            )
        }

        for (idx in inBounds.indices) {
            val spec = inBounds[idx]
            val y = inBoundsPositions[idx]
            val textWidth = measureTextWidth(spec.label)
            val width = textWidth + 12f
            val left = canvasWidth - width - 2f
            val badgeCenterY = y + (badgeHeight / 2f)
            val leaderNeeded = spec.trueLineY != null && kotlin.math.abs(badgeCenterY - spec.trueLineY) > 3f
            result.add(
                PositionedBadge(
                    id = spec.id,
                    label = spec.label,
                    topY = y,
                    leftX = left,
                    width = width,
                    height = badgeHeight,
                    textColor = spec.textColor,
                    bgColor = spec.bgColor,
                    strokeColor = spec.strokeColor,
                    trueLineY = spec.trueLineY,
                    drawLeaderLine = leaderNeeded
                )
            )
        }

        for (idx in bottomPinned.indices) {
            val spec = bottomPinned[idx]
            val y = chartHeight - badgeHeight - 2f - ((bottomPinned.size - 1 - idx) * step)
            val textWidth = measureTextWidth(spec.label)
            val width = textWidth + 12f
            val left = canvasWidth - width - 2f
            result.add(
                PositionedBadge(
                    id = spec.id,
                    label = spec.label,
                    topY = y,
                    leftX = left,
                    width = width,
                    height = badgeHeight,
                    textColor = spec.textColor,
                    bgColor = spec.bgColor,
                    strokeColor = spec.strokeColor,
                    trueLineY = spec.trueLineY,
                    drawLeaderLine = false
                )
            )
        }

        return result
    }
}

/**
 * Pure non-overlapping vertical label layout helper (used by chart right-margin badges & unit tests).
 * Preserves input ordering matching [idealYs] and guarantees at least [minSpacingPx] between adjacent
 * labels within [minY..maxY] whenever the container spans `(n - 1) * minSpacingPx`.
 */
fun layoutNonOverlappingLabelYs(
    idealYs: List<Float>,
    minY: Float,
    maxY: Float,
    minSpacingPx: Float
): List<Float> {
    if (idealYs.isEmpty()) return emptyList()
    val clampedMax = maxOf(minY, maxY)
    if (idealYs.size == 1) return listOf(idealYs.first().coerceIn(minY, clampedMax))

    val indexed = idealYs.mapIndexed { idx, y -> idx to y.coerceIn(minY, clampedMax) }
        .sortedBy { it.second }
    val pos = FloatArray(indexed.size) { indexed[it].second }

    // Forward push down
    var cursor = minY
    for (i in pos.indices) {
        pos[i] = maxOf(pos[i], cursor)
        cursor = pos[i] + minSpacingPx
    }

    // Backward pull up inside bounds
    cursor = clampedMax
    for (i in pos.indices.reversed()) {
        pos[i] = minOf(pos[i], cursor)
        cursor = pos[i] - minSpacingPx
    }

    // Re-distribute evenly if constrained range is tighter than total ideal spacing
    if (pos.last() - pos.first() < (pos.size - 1) * minSpacingPx - 0.5f ||
        (1 until pos.size).any { pos[it] - pos[it - 1] < minSpacingPx - 0.5f }
    ) {
        val avail = (clampedMax - minY).coerceAtLeast(0f)
        val step = (avail / (pos.size - 1)).coerceAtMost(minSpacingPx)
        for (i in pos.indices) {
            pos[i] = minY + i * step
        }
    }

    val restored = FloatArray(idealYs.size)
    indexed.forEachIndexed { sortedIdx, (origIdx, _) ->
        restored[origIdx] = pos[sortedIdx]
    }
    return restored.toList()
}

fun computeChartMarginsDp(hasLevelBadges: Boolean, hasCone: Boolean): Pair<Float, Float> {
    val rightAxisDp = if (hasLevelBadges) 74f else 54f
    val coneDp = if (hasCone) 28f else 0f
    return rightAxisDp to coneDp
}

fun format2(num: Double): String {
    return PriceFormatter.formatPrice(num)
}

