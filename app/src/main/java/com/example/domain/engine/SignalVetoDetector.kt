package com.example.domain.engine

import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.model.TimeWindow
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.MarketRegime
import com.example.domain.model.MarketSessionStatus
import com.example.domain.model.OffHoursMode
import com.example.domain.model.OrderFlowReport
import com.example.domain.model.PositionSide
import com.example.domain.model.SoftFlag
import com.example.domain.model.SoftFlagType
import com.example.domain.model.TechnicalIndicators
import com.example.domain.model.Timeframe
import com.example.domain.model.TradingMarketType
import com.example.domain.util.PriceFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Signal Engine v2: L0 Veto Detector & Market Regime Classification
 * Strict hierarchy: Hard Vetoes -> NO TRADE; Soft Flags -> degrade grade & produce conditional WAIT plan.
 */
object SignalVetoDetector {

    data class VetoCheckResult(
        val isVetoed: Boolean,
        val vetoReasons: List<String>,
        val hardVetoes: List<String> = vetoReasons,
        val softFlags: List<SoftFlag> = emptyList(),
        val sessionStatus: MarketSessionStatus,
        val gapRiskWarning: String? = null,
        val bufferMultiplier: Double = 1.0,
        val sizeMultiplier: Double = 1.0
    )

    fun evaluateL0Vetoes(
        symbol: String,
        side: PositionSide,
        assetType: AssetType,
        marketType: TradingMarketType,
        livePrice: Double,
        candles: List<CandlePoint>,
        indicators: TechnicalIndicators,
        timeframe: Timeframe,
        orderFlow: OrderFlowReport? = null,
        offHoursMode: OffHoursMode = OffHoursMode.CAUTION,
        perpPrice: Double? = null,
        spotPrice: Double? = null,
        spreadPercent: Double = 0.0,
        plannedStopDistance: Double? = null,
        highImpactEventWithin30Min: Boolean = false,
        futuresPerpAvailable: Boolean = (assetType == AssetType.CRYPTO),
        dataMixedFromSpot: Boolean = false,
        currentTimeMillis: Long = System.currentTimeMillis(),
        customStockEntryWindows: List<TimeWindow>? = null,
        newsShockCaution: Boolean = false
    ): VetoCheckResult {
        val hardVetoes = mutableListOf<String>()
        val softFlags = mutableListOf<SoftFlag>()
        var bufferMult = 1.0
        var sizeMult = 1.0
        var gapWarning: String? = null

        val sessionStatus = determineSessionStatus(symbol, assetType, currentTimeMillis)

        // 0. Live Price Sanity Guard
        if (livePrice <= 0.0 || livePrice.isNaN() || livePrice.isInfinite()) {
            hardVetoes.add("Data Integrity Veto: Live price is invalid ($livePrice).")
            return VetoCheckResult(
                isVetoed = true,
                vetoReasons = hardVetoes,
                hardVetoes = hardVetoes,
                softFlags = softFlags,
                sessionStatus = sessionStatus
            )
        }

        // 1. History Guard (Minimum 100 completed bars for setups)
        if (candles.size < 100) {
            hardVetoes.add("History Guard Veto: Insufficient history (${candles.size}/100 completed bars required).")
            return VetoCheckResult(
                isVetoed = true,
                vetoReasons = hardVetoes,
                hardVetoes = hardVetoes,
                softFlags = softFlags,
                sessionStatus = sessionStatus
            )
        }

        // 2. Futures Market Availability for Shorts requested on Spot
        if (marketType == TradingMarketType.SPOT && side == PositionSide.SHORT && !futuresPerpAvailable) {
            hardVetoes.add("No futures market for $symbol")
            return VetoCheckResult(
                isVetoed = true,
                vetoReasons = hardVetoes,
                hardVetoes = hardVetoes,
                softFlags = softFlags,
                sessionStatus = sessionStatus
            )
        }

        // 3. Stale-bar check: age = now - CLOSE time of last closed bar, limit = 2 * interval + 15s
        val lastCandle = candles.last()
        val candleInterval = timeframe.stepMillis
        val closeTs = lastCandle.effectiveCloseTimestamp(candleInterval)
        val candleAge = (currentTimeMillis - closeTs).coerceAtLeast(0L)
        val staleLimit = (candleInterval * 2L) + 15_000L

        if (candleInterval > 0L && candleAge > staleLimit && sessionStatus == MarketSessionStatus.OPEN) {
            val ageMinutes = (candleAge / 60_000L).coerceAtLeast(1L)
            hardVetoes.add("Data Integrity Veto: Stale data — last closed bar is ${ageMinutes}m old (> 2x ${timeframe.label} + 15s).")
        }

        // Basis in Futures mode is informational only; emit soft flag ONLY if spot candle/data is mixed with perp execution
        if (dataMixedFromSpot && marketType == TradingMarketType.FUTURES) {
            softFlags.add(
                SoftFlag(
                    type = SoftFlagType.MIXED_DATA_EXECUTION,
                    label = "Spot data mixed with perp",
                    detail = "Analysis mixes spot reference data with Futures perp execution.",
                    actionHint = "Confirm perp mark price & orderbook before entry"
                )
            )
        }

        // 4. Session / Market Hours & Off-Hours Policy
        val isEquityPerpOrStock = isEquitySymbol(symbol, assetType)

        if (isEquityPerpOrStock && sessionStatus != MarketSessionStatus.OPEN) {
            when (offHoursMode) {
                OffHoursMode.BLOCK -> {
                    hardVetoes.add("Off-Hours Veto: Cash market closed (${sessionStatus.label}). Mode: Block.")
                }
                OffHoursMode.CAUTION -> {
                    val orderFlowAgrees = orderFlow != null && (
                        (side == PositionSide.LONG && orderFlow.biasScore >= 0.0) ||
                        (side == PositionSide.SHORT && orderFlow.biasScore <= 0.0)
                    )
                    bufferMult = 1.5
                    sizeMult = 0.5
                    gapWarning = "No live stock price - moves are perp-driven and the stock can gap at the next open"
                    if (!orderFlowAgrees && orderFlow != null) {
                        softFlags.add(
                            SoftFlag(
                                type = SoftFlagType.OFF_HOURS_CAUTION,
                                label = "Off-hours flow mismatch",
                                detail = "Order flow disagrees during ${sessionStatus.label}.",
                                actionHint = "Wait for cash market open or order flow alignment"
                            )
                        )
                    } else {
                        softFlags.add(
                            SoftFlag(
                                type = SoftFlagType.OFF_HOURS_CAUTION,
                                label = "Off-hours session (${sessionStatus.label})",
                                detail = "Thin off-hours liquidity (buffer x1.5, size x0.5).",
                                actionHint = "Watch cash session open gap risk"
                            )
                        )
                    }
                }
                OffHoursMode.TREAT_AS_24_7 -> {
                    bufferMult = 1.0
                    sizeMult = 1.0
                }
            }
        }

        // 5. Stock Entry Windows Check (Soft flag -> C / WATCH with "opens at HH:MM")
        if (isEquityPerpOrStock && sessionStatus == MarketSessionStatus.OPEN) {
            val stockWindows = customStockEntryWindows ?: MarketProfile.defaultUsStock().entryWindows
            if (stockWindows.isNotEmpty()) {
                val etCal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York")).apply {
                    timeInMillis = currentTimeMillis
                }
                val h = etCal.get(Calendar.HOUR_OF_DAY)
                val m = etCal.get(Calendar.MINUTE)
                val nowMin = h * 60 + m
                val insideWindow = stockWindows.any { it.contains(h, m) }
                if (!insideWindow) {
                    val sorted = stockWindows.sortedBy { it.startHour * 60 + it.startMinute }
                    val nextWin = sorted.firstOrNull { (it.startHour * 60 + it.startMinute) > nowMin } ?: sorted.first()
                    val opensAt = String.format(Locale.US, "opens at %02d:%02d", nextWin.startHour, nextWin.startMinute)
                    softFlags.add(
                        SoftFlag(
                            type = SoftFlagType.OUTSIDE_ENTRY_WINDOW,
                            label = "Outside entry window ($opensAt)",
                            detail = "Current ET time (${String.format(Locale.US, "%02d:%02d", h, m)}) is outside optimal windows (${sorted.joinToString(", ")}).",
                            actionHint = "Watch & alert — $opensAt ET"
                        )
                    )
                }
            }
        }

        // 6. Structured Event Window Hard Veto (exchange halt / confirmed SEC 8-K shock) vs News Shock CAUTION
        if (highImpactEventWithin30Min) {
            hardVetoes.add("Event Window Veto: Structured exchange halt / 8-K / high-impact release for $symbol.")
        } else if (newsShockCaution) {
            bufferMult = maxOf(bufferMult, 1.5)
            sizeMult = minOf(sizeMult, 0.5)
            softFlags.add(
                SoftFlag(
                    type = SoftFlagType.NEWS_SHOCK_CAUTION,
                    label = "News shock CAUTION (size x0.5, buffer x1.5)",
                    detail = "Symbol headline surge detected; widen stops 1.5x and cut size 0.5x.",
                    actionHint = "Reduce position size 0.5x and wait for volatility absorption"
                )
            )
        }

        // 7. Spread and Depth Hard Veto (spread > 10% of stop distance)
        val stopDistance = plannedStopDistance ?: (indicators.averageTrueRange * 1.5)
        if (stopDistance > 0 && livePrice > 0) {
            val spreadAmount = livePrice * (spreadPercent / 100.0)
            if (spreadAmount > stopDistance * 0.10) {
                hardVetoes.add("Spread & Depth Veto: Bid/ask spread (${PriceFormatter.formatCurrency(spreadAmount)}) exceeds 10% of stop distance.")
            }
        }

        // Thin book handling: if spread is wide or off-hours, widen buffers
        if (spreadPercent > 0.08 || sessionStatus == MarketSessionStatus.WEEKEND) {
            bufferMult = maxOf(bufferMult, 1.5)
        }

        // 8. Volatility Regime SOFT Flag (Dead market creates conditional breakout plan, NEVER hard NO TRADE)
        val regime = classifyMarketRegime(indicators)
        if (regime == MarketRegime.DEAD) {
            val metricsStr = formatRegimeMetrics(indicators)
            softFlags.add(
                SoftFlag(
                    type = SoftFlagType.DEAD_REGIME,
                    label = "Dead regime",
                    detail = metricsStr,
                    actionHint = "Wait for expansion breakout above/below range with RVOL >= 1.2"
                )
            )
        }

        // 9. Over-extension SOFT Flag (> 2.5x ATR from EMA20 / VWAP -> conditional pullback plan)
        val ema20 = indicators.sma20.takeIf { it > 0.0 } ?: livePrice
        val vwap = indicators.sessionVwap.takeIf { it > 0.0 } ?: ema20
        val refMean = (ema20 + vwap) / 2.0
        val distFromMean = abs(livePrice - refMean)
        val maxAllowableExtension = indicators.averageTrueRange * 2.5

        if (livePrice > 0.0 && indicators.averageTrueRange > 0.0 && distFromMean > maxAllowableExtension) {
            softFlags.add(
                SoftFlag(
                    type = SoftFlagType.OVER_EXTENDED,
                    label = "Over-extended (>2.5x ATR)",
                    detail = "Price is ${PriceFormatter.formatCurrency(distFromMean)} from EMA20/VWAP mean (${PriceFormatter.formatCurrency(refMean)}).",
                    actionHint = "Wait for pullback to EMA20 / VWAP / OB"
                )
            )
        }

        return VetoCheckResult(
            isVetoed = hardVetoes.isNotEmpty(),
            vetoReasons = hardVetoes,
            hardVetoes = hardVetoes,
            softFlags = softFlags,
            sessionStatus = sessionStatus,
            gapRiskWarning = gapWarning,
            bufferMultiplier = bufferMult,
            sizeMultiplier = sizeMult
        )
    }

    fun isEquitySymbol(symbol: String, assetType: AssetType): Boolean {
        return assetType == AssetType.STOCK ||
            symbol.contains("SNDK", ignoreCase = true) ||
            symbol.contains("SPY", ignoreCase = true) ||
            symbol.contains("QQQ", ignoreCase = true) ||
            symbol.contains("NVDA", ignoreCase = true) ||
            symbol.contains("TSLA", ignoreCase = true) ||
            symbol.contains("AAPL", ignoreCase = true)
    }

    fun determineSessionStatus(
        symbol: String,
        assetType: AssetType,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): MarketSessionStatus {
        if (!isEquitySymbol(symbol, assetType)) {
            return MarketSessionStatus.OPEN
        }

        val etCal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York")).apply {
            timeInMillis = currentTimeMillis
        }
        val dayOfWeek = etCal.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            return MarketSessionStatus.OFF_HOURS
        }

        val hour = etCal.get(Calendar.HOUR_OF_DAY)
        val minute = etCal.get(Calendar.MINUTE)
        val timeMinutes = hour * 60 + minute

        val preMarketOpen = 4 * 60
        val regularOpen = 9 * 60 + 30
        val regularClose = 16 * 60
        val postMarketClose = 20 * 60

        return when {
            timeMinutes in regularOpen until regularClose -> MarketSessionStatus.OPEN
            timeMinutes in preMarketOpen until regularOpen || timeMinutes in regularClose until postMarketClose -> MarketSessionStatus.EXTENDED
            else -> MarketSessionStatus.OFF_HOURS
        }
    }

    /**
     * Classifies market into TREND, RANGE, EXPANSION, or DEAD:
     * - EXPANSION: Volatility breakout with RVOL >= 1.5 or expanding bandwidth
     * - DEAD (Squeeze/Dead): ATR percentile < 15 AND bandwidth percentile < 10
     * - TREND: ADX >= 20
     * - RANGE: ADX < 20 (including ADX < 15 "no trend"), allowing D_RANGE_FADE at extremes
     */
    fun classifyMarketRegime(indicators: TechnicalIndicators): MarketRegime {
        val adx = indicators.adx
        val er = indicators.efficiencyRatio
        val bwPct = indicators.bandwidthPercentile
        val atrPct = indicators.atrPercentile
        val rvol = indicators.rvol

        return when {
            (bwPct > 75.0 && rvol >= 1.5) || (rvol >= 2.0 && er > 0.45) -> MarketRegime.EXPANSION
            atrPct < 15.0 && bwPct < 10.0 -> MarketRegime.DEAD
            adx >= 20.0 -> MarketRegime.TREND
            else -> MarketRegime.RANGE
        }
    }

    fun formatRegimeMetrics(indicators: TechnicalIndicators): String {
        val range24h = PriceFormatter.formatZoneRange(indicators.range24hLow, indicators.range24hHigh)
        val adxNote = if (indicators.adx < 15.0) " (no trend)" else ""
        return String.format(
            Locale.US,
            "ADX %.1f%s · ATR pct %.0f%% · BW pct %.0f%% · ER %.2f · 24h range %s",
            indicators.adx,
            adxNote,
            indicators.atrPercentile,
            indicators.bandwidthPercentile,
            indicators.efficiencyRatio,
            range24h
        )
    }
}
