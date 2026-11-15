package com.example.data.background

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import com.example.domain.master.HorizonSettings

enum class BackgroundMonitorMode(val label: String, val description: String) {
    OFF(
        label = "Off",
        description = "No background polling. Analysis runs only while you view a symbol."
    ),
    STANDARD(
        label = "Standard (WorkManager 15m)",
        description = "Polls Watchlist round-robin every 15 min when connected; skips unchanged bars. 1m/5m triggers update on 15 min cycles."
    ),
    LIVE(
        label = "Live (Foreground Service)",
        description = "Persistent live bar-close monitor for instant alerts. Uses more battery while running."
    )
}

data class QuietHoursConfig(
    val enabled: Boolean = false,
    val startHour: Int = 23,
    val endHour: Int = 7
) {
    fun isQuietAt(hourOfDay: Int): Boolean {
        if (!enabled) return false
        return if (startHour == endHour) {
            false
        } else if (startHour < endHour) {
            hourOfDay in startHour until endHour
        } else {
            hourOfDay >= startHour || hourOfDay < endHour
        }
    }
}

data class BackgroundDiagnostics(
    val lastRunAtMs: Long = 0L,
    val nextRunAtMs: Long = 0L,
    val symbolsCovered: Int = 0,
    val watchlistCursor: Int = 0,
    val lastDurationMs: Long = 0L,
    val recentErrors: List<String> = emptyList()
)

object BackgroundPreferences {
    private const val PREFS_NAME = "tauric_background_prefs"
    private const val KEY_MODE = "bg_monitor_mode"
    private const val KEY_QUIET_ENABLED = "quiet_enabled"
    private const val KEY_QUIET_START = "quiet_start_hour"
    private const val KEY_QUIET_END = "quiet_end_hour"
    private const val KEY_ALLOW_BATTERY_SAVER = "allow_bg_on_battery_saver"
    private const val KEY_HORIZON_SETTINGS = "horizon_settings_spec"
    private const val KEY_WATCHLIST_CURSOR = "watchlist_rr_cursor"
    private const val KEY_LAST_RUN_MS = "bg_last_run_ms"
    private const val KEY_NEXT_RUN_MS = "bg_next_run_ms"
    private const val KEY_SYMBOLS_COVERED = "bg_symbols_covered"
    private const val KEY_LAST_DURATION_MS = "bg_last_duration_ms"
    private const val KEY_RECENT_ERRORS = "bg_recent_errors"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMode(context: Context): BackgroundMonitorMode {
        val raw = prefs(context).getString(KEY_MODE, BackgroundMonitorMode.STANDARD.name)
        return runCatching { BackgroundMonitorMode.valueOf(raw ?: "") }
            .getOrDefault(BackgroundMonitorMode.STANDARD)
    }

    fun setMode(context: Context, mode: BackgroundMonitorMode) {
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun getQuietHours(context: Context): QuietHoursConfig {
        val p = prefs(context)
        return QuietHoursConfig(
            enabled = p.getBoolean(KEY_QUIET_ENABLED, false),
            startHour = p.getInt(KEY_QUIET_START, 23).coerceIn(0, 23),
            endHour = p.getInt(KEY_QUIET_END, 7).coerceIn(0, 23)
        )
    }

    fun setQuietHours(context: Context, config: QuietHoursConfig) {
        prefs(context).edit()
            .putBoolean(KEY_QUIET_ENABLED, config.enabled)
            .putInt(KEY_QUIET_START, config.startHour.coerceIn(0, 23))
            .putInt(KEY_QUIET_END, config.endHour.coerceIn(0, 23))
            .apply()
    }

    fun getAllowOnBatterySaver(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALLOW_BATTERY_SAVER, false)

    fun setAllowOnBatterySaver(context: Context, allow: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALLOW_BATTERY_SAVER, allow).apply()
    }

    fun isBatterySaverActive(context: Context): Boolean {
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isPowerSaveMode == true
    }

    fun getHorizonSettings(context: Context): HorizonSettings =
        HorizonSettings.fromPrefString(prefs(context).getString(KEY_HORIZON_SETTINGS, null))

    fun setHorizonSettings(context: Context, settings: HorizonSettings) {
        prefs(context).edit().putString(KEY_HORIZON_SETTINGS, settings.toPrefString()).apply()
    }

    fun getWatchlistCursor(context: Context): Int = prefs(context).getInt(KEY_WATCHLIST_CURSOR, 0)

    fun setWatchlistCursor(context: Context, cursor: Int) {
        prefs(context).edit().putInt(KEY_WATCHLIST_CURSOR, cursor).apply()
    }

    fun recordRunDiagnostics(
        context: Context,
        lastRunMs: Long,
        nextRunMs: Long,
        symbolsCovered: Int,
        durationMs: Long,
        errors: List<String>
    ) {
        val p = prefs(context)
        val existingErrors = p.getString(KEY_RECENT_ERRORS, "")
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val combined = (errors + existingErrors).distinct().take(12).joinToString("\n")
        p.edit()
            .putLong(KEY_LAST_RUN_MS, lastRunMs)
            .putLong(KEY_NEXT_RUN_MS, nextRunMs)
            .putInt(KEY_SYMBOLS_COVERED, symbolsCovered)
            .putLong(KEY_LAST_DURATION_MS, durationMs)
            .putString(KEY_RECENT_ERRORS, combined)
            .apply()
    }

    fun clearErrors(context: Context) {
        prefs(context).edit().remove(KEY_RECENT_ERRORS).apply()
    }

    fun getDiagnostics(context: Context): BackgroundDiagnostics {
        val p = prefs(context)
        val errs = p.getString(KEY_RECENT_ERRORS, "")
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        return BackgroundDiagnostics(
            lastRunAtMs = p.getLong(KEY_LAST_RUN_MS, 0L),
            nextRunAtMs = p.getLong(KEY_NEXT_RUN_MS, 0L),
            symbolsCovered = p.getInt(KEY_SYMBOLS_COVERED, 0),
            watchlistCursor = p.getInt(KEY_WATCHLIST_CURSOR, 0),
            lastDurationMs = p.getLong(KEY_LAST_DURATION_MS, 0L),
            recentErrors = errs
        )
    }
}
