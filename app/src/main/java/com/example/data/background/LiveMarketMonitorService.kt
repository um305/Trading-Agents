package com.example.data.background

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.notification.PriceAlertNotificationManager
import com.example.data.repository.MarketRepository
import com.example.domain.model.AssetType
import com.example.domain.model.TradingMarketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class LiveMarketMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wsClient: BinanceKlineWebSocketClient? = null
    private var heartbeatJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        val notif = buildForegroundNotification("Monitoring live bar closes & armed triggers")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notif)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to enter foreground safely", e)
            stopSelf()
            return START_NOT_STICKY
        }

        if (BackgroundPreferences.getMode(applicationContext) != BackgroundMonitorMode.LIVE) {
            stopSelf()
            return START_NOT_STICKY
        }

        startMonitoringLoop()
        return START_STICKY
    }

    /**
     * Required by Android 15 (API 35+) dataSync foreground service timeout rules.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Android 15 dataSync timeout reached; stopping foreground service cleanly.")
        stopSelf()
    }

    override fun onDestroy() {
        wsClient?.disconnect()
        heartbeatJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoringLoop() {
        val repo = MarketRepository(applicationContext)
        val notifManager = PriceAlertNotificationManager(applicationContext)
        val coordinator = AnalysisCoordinator(applicationContext, repo, notifManager)

        wsClient?.disconnect()
        val ws = BinanceKlineWebSocketClient(repo.getOkHttpClient(), serviceScope)
        wsClient = ws

        serviceScope.launch {
            val watchlist = runCatching { repo.getWatchlist().first() }.getOrElse { emptyList() }
            val topCrypto = watchlist.firstOrNull { it.assetType == AssetType.CRYPTO.name }?.symbol ?: "BTC"
            ws.subscribe(topCrypto, TradingMarketType.FUTURES)

            ws.closedEvents.collect { evt ->
                if (BackgroundPreferences.getMode(applicationContext) != BackgroundMonitorMode.LIVE) return@collect
                repo.cacheRepository.appendClosedWebSocketCandle(
                    symbol = evt.symbol,
                    marketType = evt.marketType,
                    timeframe = evt.timeframe,
                    candle = evt.candle
                )
                coordinator.evaluateSymbol(
                    symbol = evt.symbol,
                    assetType = AssetType.CRYPTO,
                    marketType = evt.marketType,
                    livePriceHint = evt.candle.close,
                    isBackground = true,
                    closedTimeframe = evt.timeframe
                )
            }
        }

        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive && BackgroundPreferences.getMode(applicationContext) == BackgroundMonitorMode.LIVE) {
                val batteryBlock = BackgroundPreferences.isBatterySaverActive(applicationContext) &&
                    !BackgroundPreferences.getAllowOnBatterySaver(applicationContext)
                if (!batteryBlock) {
                    runCatching { coordinator.runBackgroundWatchlistCycle(maxSymbolsPerRun = 10) }
                }
                delay(5L * 60_000L)
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Live Market Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent dataSync channel when Live monitoring mode is enabled."
            }
            nm?.createNotificationChannel(ch)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            901,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle("TradingAgents Live Monitor")
            .setContentText(statusText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        private const val TAG = "LiveMarketService"
        const val CHANNEL_ID = "tauric_live_monitor_channel"
        const val NOTIFICATION_ID = 4401

        fun startSafely(context: Context): Boolean {
            return try {
                val intent = Intent(context.applicationContext, LiveMarketMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.applicationContext.startForegroundService(intent)
                } else {
                    context.applicationContext.startService(intent)
                }
                true
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "OS blocked FGS start from background; falling back to WorkManager.", e)
                false
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start LiveMarketMonitorService", e)
                false
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(Intent(context.applicationContext, LiveMarketMonitorService::class.java))
        }
    }
}
