package com.example.data.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            when (BackgroundPreferences.getMode(context)) {
                BackgroundMonitorMode.STANDARD -> MarketMonitorWorker.scheduleStandard(context)
                BackgroundMonitorMode.LIVE -> {
                    // Always keep WorkManager scheduled as a reliable fallback across reboots
                    MarketMonitorWorker.scheduleStandard(context)
                    LiveMarketMonitorService.startSafely(context)
                }
                BackgroundMonitorMode.OFF -> Unit
            }
        }
    }
}
