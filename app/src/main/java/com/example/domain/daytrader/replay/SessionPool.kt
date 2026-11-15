package com.example.domain.daytrader.replay

import com.example.domain.daytrader.model.MarketProfileType
import com.example.domain.daytrader.session.MarketCalendar
import com.example.domain.model.CandlePoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Random
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max

/**
 * Encapsulates a single 5-minute bar turned into 4 ratios relative to previous bar's close.
 */
data class BarRatio(
    val timestamp: Long,
    val timeOfDayIndex: Int, // 0..77 for stocks, 0..287 for crypto
    val openRatio: Double,   // open / prevClose
    val highRatio: Double,   // high / prevClose
    val lowRatio: Double,    // low / prevClose
    val closeRatio: Double,  // close / prevClose
    val barAtr: Double
) {
    /**
     * Scales this bar ratio by volatility factor = (recentAtr / poolAtr) clamped [0.5, 2.0].
     * (ratio - 1.0) is expanded or compressed by volatilityScale.
     */
    fun scaleVolatility(factor: Double): BarRatio {
        val clampedFactor = factor.coerceIn(0.5, 2.0)
        return BarRatio(
            timestamp = timestamp,
            timeOfDayIndex = timeOfDayIndex,
            openRatio = 1.0 + (openRatio - 1.0) * clampedFactor,
            highRatio = 1.0 + (highRatio - 1.0) * clampedFactor,
            lowRatio = 1.0 + (lowRatio - 1.0) * clampedFactor,
            closeRatio = 1.0 + (closeRatio - 1.0) * clampedFactor,
            barAtr = barAtr * clampedFactor
        )
    }

    /**
     * Removes the slot's mean per-bar return from all OHLC ratios so the pool
     * keeps candle range/volatility structure without directional drift.
     */
    fun subtractMeanReturn(meanReturn: Double): BarRatio {
        return BarRatio(
            timestamp = timestamp,
            timeOfDayIndex = timeOfDayIndex,
            openRatio = (openRatio - meanReturn).coerceAtLeast(0.0001),
            highRatio = (highRatio - meanReturn).coerceAtLeast(0.0001),
            lowRatio = (lowRatio - meanReturn).coerceAtLeast(0.0001),
            closeRatio = (closeRatio - meanReturn).coerceAtLeast(0.0001),
            barAtr = barAtr
        )
    }
}

/**
 * A verified full session consisting of complete 5-minute bars.
 */
data class FullSession(
    val dateKey: String,
    val isWeekend: Boolean,
    val bars: List<BarRatio>
)

/**
 * Historical session pool built from the last 60 days of 5-minute completed bars.
 */
class SessionPool(
    val symbol: String,
    val profileType: MarketProfileType,
    val sessions: List<FullSession>,
    val poolAtr: Double,
    val recentAtr: Double,
    val isWeekendFallbackCombined: Boolean = false,
    val poolNotice: String? = null
) {
    val totalSessions: Int get() = sessions.size
    val isSufficient: Boolean get() = sessions.size >= 20

    val volatilityScale: Double get() = if (poolAtr > 0.0) (recentAtr / poolAtr).coerceIn(0.5, 2.0) else 1.0

    // Indexed bars by timeOfDayIndex for fast lookup
    private val barsByTimeIndex: Map<Int, List<BarRatio>> = run {
        val map = mutableMapOf<Int, MutableList<BarRatio>>()
        sessions.forEach { session ->
            session.bars.forEach { bar ->
                map.getOrPut(bar.timeOfDayIndex) { mutableListOf() }.add(bar)
            }
        }
        map
    }

    /**
     * Computes the pool's mean per-bar close return (closeRatio - 1.0) for each time-of-day slot.
     */
    fun meanReturnByTimeSlot(): Map<Int, Double> =
        barsByTimeIndex.mapValues { (_, bars) ->
            if (bars.isEmpty()) 0.0 else bars.map { it.closeRatio - 1.0 }.average()
        }

    /**
     * Returns a drift-neutral copy of this pool where each bar has its time slot's
     * mean per-bar return subtracted before sampling.
     */
    fun toDriftNeutralPool(): SessionPool {
        val meanBySlot = meanReturnByTimeSlot()
        val neutralSessions = sessions.map { session ->
            session.copy(
                bars = session.bars.map { bar ->
                    val slotMean = meanBySlot[bar.timeOfDayIndex] ?: 0.0
                    bar.subtractMeanReturn(slotMean)
                }
            )
        }
        return SessionPool(
            symbol = symbol,
            profileType = profileType,
            sessions = neutralSessions,
            poolAtr = poolAtr,
            recentAtr = recentAtr,
            isWeekendFallbackCombined = isWeekendFallbackCombined,
            poolNotice = poolNotice
        )
    }

    /**
     * Samples a single bar from past sessions matching the same time of day (+/- windowRadius).
     */
    fun sampleBar(
        timeOfDayIndex: Int,
        windowRadius: Int = 2,
        random: Random,
        applyVolatilityScaling: Boolean = true
    ): BarRatio? {
        val candidateIndices = ((timeOfDayIndex - windowRadius)..(timeOfDayIndex + windowRadius)).toList()
        val candidateBars = candidateIndices.flatMap { barsByTimeIndex[it] ?: emptyList() }
        if (candidateBars.isEmpty()) return null
        val picked = candidateBars[random.nextInt(candidateBars.size)]
        return if (applyVolatilityScaling) picked.scaleVolatility(volatilityScale) else picked
    }

    /**
     * Block bootstrap: draws a run of consecutive bars (default 3 bars) starting near timeOfDayIndex.
     */
    fun sampleBlock(
        timeOfDayIndex: Int,
        blockSize: Int = 3,
        windowRadius: Int = 2,
        random: Random,
        applyVolatilityScaling: Boolean = true
    ): List<BarRatio> {
        if (sessions.isEmpty()) return emptyList()
        val randomSession = sessions[random.nextInt(sessions.size)]
        val matchingIndices = randomSession.bars.indices.filter { idx ->
            abs(randomSession.bars[idx].timeOfDayIndex - timeOfDayIndex) <= windowRadius
        }
        if (matchingIndices.isEmpty()) {
            val fallback = sampleBar(timeOfDayIndex, windowRadius, random, applyVolatilityScaling)
            return listOfNotNull(fallback)
        }
        val startIdx = matchingIndices[random.nextInt(matchingIndices.size)]
        val block = mutableListOf<BarRatio>()
        for (i in 0 until blockSize) {
            val currentIdx = startIdx + i
            if (currentIdx < randomSession.bars.size) {
                val b = randomSession.bars[currentIdx]
                block.add(if (applyVolatilityScaling) b.scaleVolatility(volatilityScale) else b)
            }
        }
        return block
    }

    companion object {
        /**
         * Builds a SessionPool from a raw list of 5m candles.
         * Enforces:
         * 1. Completed bars only (filters out any in-progress bars).
         * 2. Full sessions only:
         *    - Stocks: exactly 78 bars (09:30-16:00 ET), drops early closes (< 78) and gap days.
         *    - Crypto: exactly 288 bars per UTC day.
         * 3. Calculates 4 ratios to previous bar close.
         * 4. Excludes the current (in-progress) day's session.
         */
        fun buildFromCandles(
            symbol: String,
            profileType: MarketProfileType,
            candles: List<CandlePoint>,
            todayTimestamp: Long = System.currentTimeMillis(),
            isWeekendPoolForCrypto: Boolean? = null
        ): SessionPool {
            val tz = if (profileType == MarketProfileType.CRYPTO) MarketCalendar.UTC_TZ else MarketCalendar.NY_TZ
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = tz }
            val todayKey = dateFormat.format(todayTimestamp)

            // Step 1: Ensure completed, valid bars sorted chronologically
            val validCandles = candles.filter { c ->
                c.isClosed && c.open > 0.0 && c.high > 0.0 && c.low > 0.0 && c.close > 0.0 && c.high >= c.low
            }.sortedBy { it.timestamp }

            // Group candles by calendar date in the market's timezone
            val candlesByDate = mutableMapOf<String, MutableList<CandlePoint>>()
            validCandles.forEach { candle ->
                val dateStr = dateFormat.format(candle.timestamp)
                if (dateStr != todayKey) { // drop today's in-progress session
                    candlesByDate.getOrPut(dateStr) { mutableListOf() }.add(candle)
                }
            }

            val fullSessions = mutableListOf<FullSession>()
            val allAtres = mutableListOf<Double>()

            val cal = Calendar.getInstance(tz)

            for ((dateStr, dayBars) in candlesByDate) {
                cal.time = dateFormat.parse(dateStr) ?: continue
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)

                if (profileType == MarketProfileType.US_EQUITY || profileType == MarketProfileType.EQUITY_PERP) {
                    // Stocks require cash session 09:30-16:00 ET
                    // 78 bars of 5 minutes: 09:30, 09:35 ... 15:55
                    val cashBars = dayBars.filter { b ->
                        val bCal = Calendar.getInstance(tz).apply { timeInMillis = b.timestamp }
                        val h = bCal.get(Calendar.HOUR_OF_DAY)
                        val m = bCal.get(Calendar.MINUTE)
                        val totalM = h * 60 + m
                        totalM in (9 * 60 + 30) until (16 * 60)
                    }.sortedBy { it.timestamp }

                    // Drop half days (early closes have ~42 bars) and incomplete days
                    if (cashBars.size != 78) continue

                    // Build BarRatio list
                    val sessionBarRatios = mutableListOf<BarRatio>()
                    for (i in 0 until cashBars.size) {
                        val current = cashBars[i]
                        val prevClose = if (i > 0) cashBars[i - 1].close else current.open
                        val tr = max(current.high - current.low, max(abs(current.high - prevClose), abs(current.low - prevClose)))
                        allAtres.add(tr)

                        sessionBarRatios.add(
                            BarRatio(
                                timestamp = current.timestamp,
                                timeOfDayIndex = i, // 0..77
                                openRatio = current.open / prevClose,
                                highRatio = current.high / prevClose,
                                lowRatio = current.low / prevClose,
                                closeRatio = current.close / prevClose,
                                barAtr = tr
                            )
                        )
                    }
                    fullSessions.add(FullSession(dateStr, isWeekend = false, bars = sessionBarRatios))

                } else {
                    // Crypto: 288 bars per 24h UTC day
                    if (dayBars.size < 280) continue // allow max 8 missing bars, take first 288
                    val sortedDayBars = dayBars.take(288)

                    if (isWeekendPoolForCrypto != null && isWeekend != isWeekendPoolForCrypto) {
                        continue
                    }

                    val sessionBarRatios = mutableListOf<BarRatio>()
                    for (i in 0 until sortedDayBars.size) {
                        val current = sortedDayBars[i]
                        val prevClose = if (i > 0) sortedDayBars[i - 1].close else current.open
                        val tr = max(current.high - current.low, max(abs(current.high - prevClose), abs(current.low - prevClose)))
                        allAtres.add(tr)

                        val bCal = Calendar.getInstance(tz).apply { timeInMillis = current.timestamp }
                        val timeOfDayIdx = (bCal.get(Calendar.HOUR_OF_DAY) * 60 + bCal.get(Calendar.MINUTE)) / 5

                        sessionBarRatios.add(
                            BarRatio(
                                timestamp = current.timestamp,
                                timeOfDayIndex = timeOfDayIdx.coerceIn(0, 287),
                                openRatio = current.open / prevClose,
                                highRatio = current.high / prevClose,
                                lowRatio = current.low / prevClose,
                                closeRatio = current.close / prevClose,
                                barAtr = tr
                            )
                        )
                    }
                    fullSessions.add(FullSession(dateStr, isWeekend = isWeekend, bars = sessionBarRatios))
                }
            }

            val poolAtr = if (allAtres.isNotEmpty()) allAtres.average() else 1.0

            // Recent 14-bar ATR from the latest valid candles
            val recentCandles = validCandles.takeLast(15)
            val recentAtr = if (recentCandles.size >= 2) {
                (1 until recentCandles.size).map { idx ->
                    val curr = recentCandles[idx]
                    val prev = recentCandles[idx - 1]
                    max(curr.high - curr.low, max(abs(curr.high - prev.close), abs(curr.low - prev.close)))
                }.average()
            } else poolAtr

            return SessionPool(
                symbol = symbol,
                profileType = profileType,
                sessions = fullSessions,
                poolAtr = poolAtr,
                recentAtr = recentAtr
            )
        }

        /**
         * Builds crypto weekend pool first when requested, falling back to combined
         * weekday+weekend pool if weekend has < 20 full sessions.
         */
        fun buildFromCandlesWithWeekendFallback(
            symbol: String,
            profileType: MarketProfileType,
            candles: List<CandlePoint>,
            todayTimestamp: Long = System.currentTimeMillis(),
            preferWeekendOnly: Boolean? = null
        ): SessionPool {
            if (profileType != MarketProfileType.CRYPTO) {
                return buildFromCandles(symbol, profileType, candles, todayTimestamp, null)
            }
            val cal = Calendar.getInstance(MarketCalendar.UTC_TZ).apply { timeInMillis = todayTimestamp }
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekendToday = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            val wantWeekendOnly = preferWeekendOnly ?: isWeekendToday

            val primary = buildFromCandles(symbol, profileType, candles, todayTimestamp, if (wantWeekendOnly) true else false)
            if (wantWeekendOnly && primary.totalSessions < 20) {
                val combined = buildFromCandles(symbol, profileType, candles, todayTimestamp, null)
                val notice = "Weekend pool had ${primary.totalSessions}/20 full sessions — combined with weekday pool (${combined.totalSessions} sessions)."
                return SessionPool(
                    symbol = combined.symbol,
                    profileType = combined.profileType,
                    sessions = combined.sessions,
                    poolAtr = combined.poolAtr,
                    recentAtr = combined.recentAtr,
                    isWeekendFallbackCombined = true,
                    poolNotice = notice
                )
            }
            return primary
        }
    }
}
