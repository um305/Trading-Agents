package com.example.domain.daytrader.alert

import android.content.Context
import com.example.domain.daytrader.plan.DayTraderPlan
import com.example.domain.daytrader.session.MarketCalendar
import com.example.domain.model.CandidateSetup
import com.example.domain.model.PositionSide
import com.example.data.notification.PriceAlertNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DayTraderAlertType {
    TRIGGER_FIRED,
    STOP_LOSS_HIT,
    TARGET_TP1_HIT,
    TARGET_TP2_HIT,
    TARGET_TP3_HIT,
    FLAT_BY_DEADLINE,
    SESSION_OPENED,
    OPENING_RANGE_FORMED,
    SETUP_EXPIRED,
    NEWS_SHOCK
}

data class DayTraderAlertItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: DayTraderAlertType,
    val symbol: String,
    val headline: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isCritical: Boolean = false
)

/**
 * Dispatches in-app banner alerts and Android notifications for Day-Trader Pack lifecycle events.
 */
class DayTraderAlertManager(
    private val context: Context,
    private val notificationManager: PriceAlertNotificationManager
) {
    private val _alerts = MutableStateFlow<List<DayTraderAlertItem>>(emptyList())
    val alerts: StateFlow<List<DayTraderAlertItem>> = _alerts.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun postAlert(alert: DayTraderAlertItem) {
        val updated = (_alerts.value + alert).takeLast(50)
        _alerts.value = updated

        if (alert.isCritical) {
            notificationManager.sendNotification(
                notificationId = (alert.symbol.hashCode() + alert.type.ordinal),
                title = "[${alert.symbol}] ${alert.headline}",
                message = "${alert.details} (${timeFormat.format(Date(alert.timestamp))})"
            )
        }
    }

    fun onTriggerFired(symbol: String, side: PositionSide, entryPrice: Double, triggerDescription: String) {
        postAlert(
            DayTraderAlertItem(
                type = DayTraderAlertType.TRIGGER_FIRED,
                symbol = symbol,
                headline = "TRIGGER FIRED: ${side.name} @ $${String.format(Locale.US, "%.2f", entryPrice)}",
                details = "Completed bar confirmed trigger condition: $triggerDescription",
                isCritical = true
            )
        )
    }

    fun onStopHit(symbol: String, stopPrice: Double, side: PositionSide) {
        postAlert(
            DayTraderAlertItem(
                type = DayTraderAlertType.STOP_LOSS_HIT,
                symbol = symbol,
                headline = "STOP LOSS HIT: $${String.format(Locale.US, "%.2f", stopPrice)}",
                details = "Plan invalidated for $symbol ${side.name}. Capital preservation active.",
                isCritical = true
            )
        )
    }

    fun onTargetHit(symbol: String, targetTier: Int, targetPrice: Double, realizedR: Double) {
        val type = when (targetTier) {
            1 -> DayTraderAlertType.TARGET_TP1_HIT
            2 -> DayTraderAlertType.TARGET_TP2_HIT
            else -> DayTraderAlertType.TARGET_TP3_HIT
        }
        postAlert(
            DayTraderAlertItem(
                type = type,
                symbol = symbol,
                headline = "TP$targetTier HIT: $${String.format(Locale.US, "%.2f", targetPrice)} (+${String.format(Locale.US, "%.1f", realizedR)}R)",
                details = "Secured target $targetTier on $symbol. Trail stop loss to lock gains.",
                isCritical = false
            )
        )
    }

    fun onFlatByDeadline(symbol: String, isEarlyClose: Boolean) {
        val timeLabel = if (isEarlyClose) "12:55 ET (Early Close)" else "15:55 ET"
        postAlert(
            DayTraderAlertItem(
                type = DayTraderAlertType.FLAT_BY_DEADLINE,
                symbol = symbol,
                headline = "FLAT-BY DEADLINE REACHED ($timeLabel)",
                details = "Mandatory intraday exit deadline. Close remaining open position before market closes.",
                isCritical = true
            )
        )
    }

    fun onOpeningRangeFormed(symbol: String, orHigh: Double, orLow: Double, sessionName: String) {
        postAlert(
            DayTraderAlertItem(
                type = DayTraderAlertType.OPENING_RANGE_FORMED,
                symbol = symbol,
                headline = "15m Opening Range Formed ($sessionName)",
                details = "Range [$${String.format(Locale.US, "%.2f", orLow)} – $${String.format(Locale.US, "%.2f", orHigh)}]. Scanners armed for breakout/breakdown.",
                isCritical = false
            )
        )
    }

    fun onNewsShockDetected(symbol: String, headline: String, formType: String) {
        postAlert(
            DayTraderAlertItem(
                type = DayTraderAlertType.NEWS_SHOCK,
                symbol = symbol,
                headline = "NEWS SHOCK DETECTED ($formType)",
                details = "$headline. Position size reduced by 50%, stop buffer widened x1.5.",
                isCritical = true
            )
        )
    }

    fun onSetupExpired(symbol: String, reason: String) {
        postAlert(
            DayTraderAlertItem(
                type = DayTraderAlertType.SETUP_EXPIRED,
                symbol = symbol,
                headline = "SETUP EXPIRED",
                details = "Plan cancelled: $reason",
                isCritical = false
            )
        )
    }

    fun clearAlerts() {
        _alerts.value = emptyList()
    }
}
