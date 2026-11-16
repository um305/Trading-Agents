package com.example.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.background.BackgroundPreferences
import com.example.data.db.TradingSignal
import com.example.domain.master.TradingHorizon
import java.util.Calendar
import java.util.Locale

enum class MasterAlertTrigger(val label: String, val isHighPriority: Boolean) {
    HORIZON_ARMED_TRADE("Horizon Armed (TRADE)", true),
    PRICE_IN_ARMED_ZONE("Price Entered Zone", true),
    STOP_LOSS_HIT("Stop Loss Hit", true),
    TP_HIT("Take-Profit Hit", false),
    HIGHER_TF_BIAS_FLIP("Higher-TF Bias Flipped", true),
    NEWS_EVENT_CAUTION("High-Impact News / Event", true)
}

class PriceAlertNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "trading_signals_alerts"
        const val CHANNEL_NAME = "Trading Signals & Price Alerts"
        const val CHANNEL_DESC = "Push notifications for horizon alerts, entry triggers, targets, and stops"
        const val EXTRA_SYMBOL = "extra_symbol"
        const val EXTRA_HORIZON = "extra_horizon"
        const val GROUP_KEY_PREFIX = "com.example.alerts.GROUP_"
        const val MAX_GLOBAL_NOTIFICATIONS_PER_HOUR = 6
        const val PER_SYMBOL_COOLDOWN_MS = 30L * 60_000L
        private const val TAG = "PriceAlertNotify"
        private const val PREFS_RATE_LIMIT = "tauric_notif_rate_limit"
        private const val KEY_HOURLY_TIMESTAMPS = "hourly_timestamps"
        private const val KEY_SYMBOL_LAST_SENT_PREFIX = "last_sent_sym_"
        private const val KEY_INBOX_LINES_PREFIX = "inbox_lines_"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val ratePrefs =
        context.applicationContext.getSharedPreferences(PREFS_RATE_LIMIT, Context.MODE_PRIVATE)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setShowBadge(true)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks quiet hours, global 6/hour cap, and 30-min per-symbol cooldown before allowing background alert dispatch.
     */
    fun canDispatchBackgroundAlert(
        symbol: String,
        nowMs: Long = System.currentTimeMillis(),
        hourOfDayOverride: Int? = null
    ): Pair<Boolean, String?> {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        val quiet = BackgroundPreferences.getQuietHours(context)
        val currentHour = hourOfDayOverride ?: Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY)
        if (quiet.isQuietAt(currentHour)) {
            return false to "Suppressed by quiet hours (${quiet.startHour}:00–${quiet.endHour}:00)"
        }

        val oneHourAgo = nowMs - 3_600_000L
        val recentGlobal = readHourlyTimestamps().filter { it >= oneHourAgo }
        if (recentGlobal.size >= MAX_GLOBAL_NOTIFICATIONS_PER_HOUR) {
            return false to "Suppressed by global cap ($MAX_GLOBAL_NOTIFICATIONS_PER_HOUR/hour)"
        }

        val lastForSymbol = ratePrefs.getLong("$KEY_SYMBOL_LAST_SENT_PREFIX$cleanSym", 0L)
        if (lastForSymbol > 0L && (nowMs - lastForSymbol) < PER_SYMBOL_COOLDOWN_MS) {
            val remMin = ((PER_SYMBOL_COOLDOWN_MS - (nowMs - lastForSymbol)) / 60_000L).coerceAtLeast(1L)
            return false to "Suppressed by $cleanSym 30-min cooldown (${remMin}m left)"
        }
        return true to null
    }

    fun sendGroupedMasterAlert(
        symbol: String,
        horizon: TradingHorizon?,
        trigger: MasterAlertTrigger,
        title: String,
        message: String,
        bypassThrottle: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
        hourOfDayOverride: Int? = null
    ): Boolean {
        val cleanSym = symbol.trim().uppercase(Locale.US)
        if (!hasNotificationPermission()) return false
        if (!bypassThrottle) {
            val (allowed, _) = canDispatchBackgroundAlert(cleanSym, nowMs, hourOfDayOverride)
            if (!allowed) return false
        }

        recordNotificationDispatched(cleanSym, nowMs)

        return try {
            val groupKey = "$GROUP_KEY_PREFIX$cleanSym"
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_SYMBOL, cleanSym)
                if (horizon != null) putExtra(EXTRA_HORIZON, horizon.name)
            }
            val requestCode = (cleanSym.hashCode() * 31) + (horizon?.ordinal ?: 9) + trigger.ordinal * 17
            val pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val childId = 20_000 + ((cleanSym.hashCode() and 0x1FFF) * 10) + trigger.ordinal
            val childBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSubText(trigger.label)
                .setGroup(groupKey)
                .setPriority(if (trigger.isHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val prevLines = (ratePrefs.getString("$KEY_INBOX_LINES_PREFIX$cleanSym", "") ?: "")
                .split("\n")
                .filter { it.isNotBlank() }
            val updatedLines = (listOf("$title: $message") + prevLines).take(5)
            ratePrefs.edit().putString("$KEY_INBOX_LINES_PREFIX$cleanSym", updatedLines.joinToString("\n")).apply()

            val summaryStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("$cleanSym Market Alerts (${updatedLines.size})")
            updatedLines.forEach { summaryStyle.addLine(it) }

            val summaryId = 10_000 + (cleanSym.hashCode() and 0x1FFF)
            val summaryBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app_logo)
                .setContentTitle("$cleanSym • ${updatedLines.size} active signal updates")
                .setContentText(title)
                .setStyle(summaryStyle)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val nm = NotificationManagerCompat.from(context)
            nm.notify(childId, childBuilder.build())
            nm.notify(summaryId, summaryBuilder.build())
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending grouped alert", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending grouped alert", e)
            false
        }
    }

    private fun readHourlyTimestamps(): List<Long> {
        val raw = ratePrefs.getString(KEY_HOURLY_TIMESTAMPS, "") ?: ""
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }
    }

    private fun recordNotificationDispatched(symbol: String, nowMs: Long) {
        val oneHourAgo = nowMs - 3_600_000L
        val kept = (readHourlyTimestamps().filter { it >= oneHourAgo } + nowMs).joinToString(",")
        ratePrefs.edit()
            .putString(KEY_HOURLY_TIMESTAMPS, kept)
            .putLong("$KEY_SYMBOL_LAST_SENT_PREFIX$symbol", nowMs)
            .apply()
    }

    fun clearThrottleHistoryForTest() {
        ratePrefs.edit().clear().apply()
    }

    /**
     * Checks a TradingSignal entity against the current live price and triggers a push notification
     * if the price hits or crosses the signal's entry, target, or stop loss threshold.
     */
    fun checkAndTriggerSignalAlert(
        signal: TradingSignal,
        currentPrice: Double,
        previousPrice: Double = currentPrice
    ): Boolean {
        if (!hasNotificationPermission() || currentPrice <= 0.0) return false

        val symbol = signal.pairSymbol
        val entry = signal.entryPrice
        val target = signal.targetPrice
        val stop = signal.stopLossPrice

        if (entry > 0.0) {
            val entryTolerance = entry * 0.005
            val isAtEntry = Math.abs(currentPrice - entry) <= entryTolerance
            val crossedEntry = (previousPrice > entry && currentPrice <= entry) ||
                (previousPrice < entry && currentPrice >= entry)

            if (isAtEntry || crossedEntry) {
                return sendGroupedMasterAlert(
                    symbol = symbol,
                    horizon = null,
                    trigger = MasterAlertTrigger.PRICE_IN_ARMED_ZONE,
                    title = "BUY / LONG ALERT: $symbol Hit Entry",
                    message = "$symbol reached $${formatPrice(currentPrice)} (Target: $${formatPrice(target)}, Stop: $${formatPrice(stop)})."
                )
            }
        }

        if (target > 0.0 && currentPrice >= target) {
            return sendGroupedMasterAlert(
                symbol = symbol,
                horizon = null,
                trigger = MasterAlertTrigger.TP_HIT,
                title = "TAKE-PROFIT HIT: $symbol Target Reached",
                message = "Price crossed target $${formatPrice(target)} at $${formatPrice(currentPrice)} (${signal.riskRewardRatio})."
            )
        }

        if (stop > 0.0 && currentPrice <= stop) {
            return sendGroupedMasterAlert(
                symbol = symbol,
                horizon = null,
                trigger = MasterAlertTrigger.STOP_LOSS_HIT,
                title = "STOP-LOSS ALERT: $symbol Invalidation Hit",
                message = "Price dropped to $${formatPrice(currentPrice)}, breaching stop-loss $${formatPrice(stop)}."
            )
        }

        return false
    }

    /**
     * Sends an immediate push notification for a custom price threshold or manual test.
     */
    fun sendNotification(
        notificationId: Int,
        title: String,
        message: String,
        subText: String? = null,
        symbol: String? = null,
        horizon: TradingHorizon? = null
    ) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted, skipping alert: $title")
            return
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (!symbol.isNullOrBlank()) putExtra(EXTRA_SYMBOL, symbol.trim().uppercase(Locale.US))
                if (horizon != null) putExtra(EXTRA_HORIZON, horizon.name)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 300, 150, 300))

            if (!subText.isNullOrBlank()) {
                builder.setSubText(subText)
            }

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            Log.d(TAG, "Push notification sent successfully: $title")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException sending notification", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting notification", e)
        }
    }

    private fun formatPrice(price: Double): String {
        return if (price >= 1000.0) {
            String.format(Locale.US, "%,.2f", price)
        } else if (price >= 1.0) {
            String.format(Locale.US, "%.4f", price)
        } else {
            String.format(Locale.US, "%.6f", price)
        }
    }
}
