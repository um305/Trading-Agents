package com.example.domain.daytrader.session

import com.example.domain.daytrader.model.MarketProfile
import java.util.Calendar
import java.util.TimeZone

/**
 * Handles market calendars, NYSE holidays, 13:00 early closes, flat-by deadlines,
 * and crypto session intervals (00:00, 08:00, 13:30 UTC).
 */
object MarketCalendar {

    val NY_TZ: TimeZone = TimeZone.getTimeZone("America/New_York")
    val UTC_TZ: TimeZone = TimeZone.getTimeZone("UTC")

    /**
     * Checks if given date in New York is an NYSE full market holiday.
     * Recognized holidays:
     * - New Year's Day (Jan 1, or observed Fri/Mon)
     * - Martin Luther King Jr. Day (3rd Monday in Jan)
     * - Washington's Birthday / Presidents' Day (3rd Monday in Feb)
     * - Good Friday (variable Easter-linked)
     * - Memorial Day (last Monday in May)
     * - Juneteenth (June 19, or observed)
     * - Independence Day (July 4, or observed)
     * - Labor Day (1st Monday in Sep)
     * - Thanksgiving Day (4th Thursday in Nov)
     * - Christmas Day (Dec 25, or observed)
     */
    fun isNyseHoliday(cal: Calendar): Boolean {
        val nyCal = Calendar.getInstance(NY_TZ).apply { timeInMillis = cal.timeInMillis }
        val dayOfWeek = nyCal.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return true

        val year = nyCal.get(Calendar.YEAR)
        val month = nyCal.get(Calendar.MONTH) // 0-based
        val dayOfMonth = nyCal.get(Calendar.DAY_OF_MONTH)

        // New Year's Day (Jan 1)
        if (month == Calendar.JANUARY && dayOfMonth == 1) return true
        if (month == Calendar.DECEMBER && dayOfMonth == 31 && dayOfWeek == Calendar.FRIDAY) return true
        if (month == Calendar.JANUARY && dayOfMonth == 2 && dayOfWeek == Calendar.MONDAY) return true

        // Martin Luther King Jr. Day (3rd Monday in Jan)
        if (month == Calendar.JANUARY && dayOfWeek == Calendar.MONDAY && dayOfMonth in 15..21) return true

        // Presidents' Day (3rd Monday in Feb)
        if (month == Calendar.FEBRUARY && dayOfWeek == Calendar.MONDAY && dayOfMonth in 15..21) return true

        // Memorial Day (last Monday in May: 25..31)
        if (month == Calendar.MAY && dayOfWeek == Calendar.MONDAY && dayOfMonth in 25..31) return true

        // Juneteenth (June 19)
        if (month == Calendar.JUNE && dayOfMonth == 19) return true
        if (month == Calendar.JUNE && dayOfMonth == 18 && dayOfWeek == Calendar.FRIDAY) return true
        if (month == Calendar.JUNE && dayOfMonth == 20 && dayOfWeek == Calendar.MONDAY) return true

        // Independence Day (July 4)
        if (month == Calendar.JULY && dayOfMonth == 4) return true
        if (month == Calendar.JULY && dayOfMonth == 3 && dayOfWeek == Calendar.FRIDAY) return true
        if (month == Calendar.JULY && dayOfMonth == 5 && dayOfWeek == Calendar.MONDAY) return true

        // Labor Day (1st Monday in Sep: 1..7)
        if (month == Calendar.SEPTEMBER && dayOfWeek == Calendar.MONDAY && dayOfMonth in 1..7) return true

        // Thanksgiving (4th Thursday in Nov: 22..28)
        if (month == Calendar.NOVEMBER && dayOfWeek == Calendar.THURSDAY && dayOfMonth in 22..28) return true

        // Christmas Day (Dec 25)
        if (month == Calendar.DECEMBER && dayOfMonth == 25) return true
        if (month == Calendar.DECEMBER && dayOfMonth == 24 && dayOfWeek == Calendar.FRIDAY) return true
        if (month == Calendar.DECEMBER && dayOfMonth == 26 && dayOfWeek == Calendar.MONDAY) return true

        return false
    }

    /**
     * Checks if given date is an NYSE 13:00 ET Early Close day:
     * - Day after Thanksgiving (Black Friday)
     * - July 3rd (if weekday and July 4th is weekday)
     * - Christmas Eve Dec 24th (if weekday)
     */
    fun isNyseEarlyCloseDay(cal: Calendar): Boolean {
        val nyCal = Calendar.getInstance(NY_TZ).apply { timeInMillis = cal.timeInMillis }
        val dayOfWeek = nyCal.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return false

        val month = nyCal.get(Calendar.MONTH)
        val dayOfMonth = nyCal.get(Calendar.DAY_OF_MONTH)

        // Day after Thanksgiving (Black Friday: 4th Friday in Nov: 23..29)
        if (month == Calendar.NOVEMBER && dayOfWeek == Calendar.FRIDAY && dayOfMonth in 23..29) return true

        // Christmas Eve (Dec 24 on Mon-Fri)
        if (month == Calendar.DECEMBER && dayOfMonth == 24 && dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY) return true

        // July 3rd (if July 4th is weekday, July 3rd closes early at 13:00)
        if (month == Calendar.JULY && dayOfMonth == 3 && dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY) return true

        return false
    }

    /**
     * Determines flat-by timestamp for a stock trade on the given date.
     * Flat-by is 12:55 ET on early close days and 15:55 ET on regular days.
     */
    fun getStockFlatByTimestamp(entryTimestamp: Long): Long {
        val cal = Calendar.getInstance(NY_TZ).apply { timeInMillis = entryTimestamp }
        val isEarly = isNyseEarlyCloseDay(cal)

        cal.set(Calendar.HOUR_OF_DAY, if (isEarly) 12 else 15)
        cal.set(Calendar.MINUTE, 55)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Returns true if entry timestamp is at or past the flat-by deadline.
     */
    fun isPastFlatBy(timestamp: Long, profile: MarketProfile): Boolean {
        if (profile.type == com.example.domain.daytrader.model.MarketProfileType.US_EQUITY) {
            val flatBy = getStockFlatByTimestamp(timestamp)
            return timestamp >= flatBy
        }
        return false
    }

    /**
     * Crypto session open times: 00:00, 08:00, and 13:30 UTC.
     * Returns the start timestamp of the latest active crypto opening session.
     */
    fun getLatestCryptoSessionOpen(timestamp: Long): Long {
        val cal = Calendar.getInstance(UTC_TZ).apply { timeInMillis = timestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val totalM = hour * 60 + minute

        val (sHour, sMin) = when {
            totalM >= 13 * 60 + 30 -> Pair(13, 30)
            totalM >= 8 * 60 -> Pair(8, 0)
            else -> Pair(0, 0)
        }

        cal.set(Calendar.HOUR_OF_DAY, sHour)
        cal.set(Calendar.MINUTE, sMin)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
