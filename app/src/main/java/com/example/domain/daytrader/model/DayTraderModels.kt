package com.example.domain.daytrader.model

import java.util.Calendar
import java.util.TimeZone

enum class MarketProfileType(val label: String) {
    US_EQUITY("US Stocks (NYSE/Nasdaq)"),
    CRYPTO("Crypto (24/7 Futures/Spot)"),
    EQUITY_PERP("Equity Perps (Stock Tracking)")
}

data class TimeWindow(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    fun contains(hour: Int, minute: Int): Boolean {
        val currentMinutes = hour * 60 + minute
        val startM = startHour * 60 + startMinute
        val endM = endHour * 60 + endMinute
        return currentMinutes in startM..endM
    }

    override fun toString(): String = String.format("%02d:%02d–%02d:%02d", startHour, startMinute, endHour, endMinute)
}

data class MarketProfile(
    val type: MarketProfileType,
    val sessionName: String,
    val timeZone: TimeZone,
    val sessionOpenHour: Int,
    val sessionOpenMinute: Int,
    val sessionCloseHour: Int,
    val sessionCloseMinute: Int,
    val openingRangeMinutes: Int = 15, // 3 x 5-minute bars
    val regularFlatByHour: Int = 15,
    val regularFlatByMinute: Int = 55,
    val earlyCloseFlatByHour: Int = 12,
    val earlyCloseFlatByMinute: Int = 55,
    val maxHoldBars: Int = 12, // default 12 candles
    val entryWindows: List<TimeWindow> = listOf(
        TimeWindow(9, 45, 11, 30),
        TimeWindow(14, 0, 15, 30)
    ),
    val allowShorts: Boolean = true,
    val flatBeforeWeekendForPerps: Boolean = true
) {
    companion object {
        val NY_TIMEZONE: TimeZone = TimeZone.getTimeZone("America/New_York")
        val UTC_TIMEZONE: TimeZone = TimeZone.getTimeZone("UTC")

        fun defaultUsStock(): MarketProfile = MarketProfile(
            type = MarketProfileType.US_EQUITY,
            sessionName = "US Regular Cash Session",
            timeZone = NY_TIMEZONE,
            sessionOpenHour = 9,
            sessionOpenMinute = 30,
            sessionCloseHour = 16,
            sessionCloseMinute = 0,
            openingRangeMinutes = 15,
            regularFlatByHour = 15,
            regularFlatByMinute = 55,
            earlyCloseFlatByHour = 12,
            earlyCloseFlatByMinute = 55,
            maxHoldBars = 78,
            entryWindows = listOf(
                TimeWindow(9, 45, 11, 30),
                TimeWindow(14, 0, 15, 30)
            ),
            allowShorts = true
        )

        fun defaultCrypto(): MarketProfile = MarketProfile(
            type = MarketProfileType.CRYPTO,
            sessionName = "Crypto 24/7 Global",
            timeZone = UTC_TIMEZONE,
            sessionOpenHour = 0,
            sessionOpenMinute = 0,
            sessionCloseHour = 23,
            sessionCloseMinute = 59,
            openingRangeMinutes = 15,
            regularFlatByHour = 23,
            regularFlatByMinute = 55,
            maxHoldBars = 12, // 12 candles on setup TF
            entryWindows = emptyList(),
            allowShorts = true
        )

        fun defaultEquityPerp(): MarketProfile = MarketProfile(
            type = MarketProfileType.EQUITY_PERP,
            sessionName = "Equity Perp (Crypto hours + Underlying Cash)",
            timeZone = NY_TIMEZONE,
            sessionOpenHour = 9,
            sessionOpenMinute = 30,
            sessionCloseHour = 16,
            sessionCloseMinute = 0,
            openingRangeMinutes = 15,
            regularFlatByHour = 15,
            regularFlatByMinute = 55,
            maxHoldBars = 12,
            entryWindows = listOf(
                TimeWindow(9, 45, 11, 30),
                TimeWindow(14, 0, 15, 30)
            ),
            allowShorts = true,
            flatBeforeWeekendForPerps = true
        )
    }
}
