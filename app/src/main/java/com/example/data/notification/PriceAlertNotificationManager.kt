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
import com.example.data.db.TradingSignal

class PriceAlertNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "trading_signals_alerts"
        const val CHANNEL_NAME = "Trading Signals & Price Alerts"
        const val CHANNEL_DESC = "Push notifications for TradingSignal buy/sell price thresholds and stop losses"
        private const val TAG = "PriceAlertNotify"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

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

        // 1. Entry Threshold Hit (Price touches or crosses down/up into the entry zone)
        if (entry > 0.0) {
            val entryTolerance = entry * 0.005 // within 0.5%
            val isAtEntry = Math.abs(currentPrice - entry) <= entryTolerance
            val crossedEntry = (previousPrice > entry && currentPrice <= entry) ||
                    (previousPrice < entry && currentPrice >= entry)

            if (isAtEntry || crossedEntry) {
                sendNotification(
                    notificationId = (symbol.hashCode() + 1001),
                    title = "BUY / LONG ALERT: $symbol Hit Entry",
                    message = "$symbol reached recommended buy price $${formatPrice(currentPrice)} (Target: $${formatPrice(target)}, Stop: $${formatPrice(stop)}). AI Signal: ${signal.verdict}",
                    subText = "Entry Threshold Triggered"
                )
                return true
            }
        }

        // 2. Target Sell Threshold Hit (Price touches or exceeds take-profit target)
        if (target > 0.0 && currentPrice >= target) {
            sendNotification(
                notificationId = (symbol.hashCode() + 2002),
                title = "TAKE-PROFIT HIT: $symbol Target Reached!",
                message = "Price crossed target $${formatPrice(target)} at $${formatPrice(currentPrice)}! Risk/Reward: ${signal.riskRewardRatio}. Consider securing profit.",
                subText = "Take-Profit Target Hit"
            )
            return true
        }

        // 3. Stop-Loss Hit (Price breaches stop-loss level)
        if (stop > 0.0 && currentPrice <= stop) {
            sendNotification(
                notificationId = (symbol.hashCode() + 3003),
                title = "STOP-LOSS ALERT: $symbol Invalidation Hit",
                message = "Price dropped to $${formatPrice(currentPrice)}, breaching stop-loss level of $${formatPrice(stop)}. Protect capital.",
                subText = "Stop Loss Breached"
            )
            return true
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
        subText: String? = null
    ) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "Notification permission not granted, skipping alert: $title")
            return
        }

        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
            String.format(java.util.Locale.US, "%,.2f", price)
        } else if (price >= 1.0) {
            String.format(java.util.Locale.US, "%.4f", price)
        } else {
            String.format(java.util.Locale.US, "%.6f", price)
        }
    }
}
