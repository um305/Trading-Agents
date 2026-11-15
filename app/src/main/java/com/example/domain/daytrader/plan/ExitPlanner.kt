package com.example.domain.daytrader.plan

import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.model.MarketProfileType
import com.example.domain.daytrader.session.MarketCalendar
import com.example.domain.model.PositionSide
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

enum class StopRuleType(val label: String) {
    OR_MIDPOINT("Opening Range Midpoint"),
    OR_EXTREME("Opening Range Low/High"),
    STRUCTURE_SWEEP("Structure / Sweep Wick Far Edge"),
    ATR_MULTIPLE("ATR x K Multiple"),
    PERCENTAGE("Fixed Percentage"),
    MANUAL_PRICE("Manual Specific Price")
}

enum class TargetRuleType(val label: String) {
    R_MULTIPLE("Fixed R Multiple (e.g. 2R)"),
    STRUCTURE_LEVEL("Structural Liquidity / High / Low"),
    MANUAL_PRICE("Manual Specific Price")
}

data class DayTraderPlan(
    val symbol: String,
    val side: PositionSide,
    val entryPrice: Double,
    val stopRule: StopRuleType,
    val stopPrice: Double,
    val targetRule: TargetRuleType,
    val targetPrice: Double,
    val riskPerUnit: Double,
    val rewardPerUnit: Double,
    val netRewardRisk: Double,
    val tickSize: Double,
    val flatByTimestamp: Long,
    val flatByDescription: String,
    val triggerCondition: String,
    val isTriggerFired: Boolean,
    val validationError: String? = null,
    val openingRangeHigh: Double? = null,
    val openingRangeLow: Double? = null,
    val openingRangeMid: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isExecutable: Boolean get() = validationError == null && isTriggerFired
}

object ExitPlanner {

    /**
     * Constructs and validates a DayTraderPlan.
     * Enforces:
     * 1. Stop rounded AWAY from entry to tick size.
     * 2. Target rounded to tick size.
     * 3. Refuses with reasons:
     *    - Stop on wrong side or at entry
     *    - Target on wrong side
     *    - Risk < max(0.05% of price, 3 x spread)
     *    - Entry at or after flat-by time
     *    - Never loosens anything to make a plan pass
     */
    fun createPlan(
        symbol: String,
        side: PositionSide,
        entryPrice: Double,
        stopRule: StopRuleType,
        rawStopPrice: Double,
        targetRule: TargetRuleType = TargetRuleType.R_MULTIPLE,
        targetRMultiple: Double = 2.0,
        rawTargetPrice: Double? = null,
        spread: Double = 0.0,
        tickSize: Double = 0.01,
        entryTimestamp: Long = System.currentTimeMillis(),
        profile: MarketProfile = MarketProfile.defaultUsStock(),
        isTriggerFired: Boolean = false,
        triggerCondition: String = "5m candle closes beyond trigger",
        openingRangeHigh: Double? = null,
        openingRangeLow: Double? = null
    ): DayTraderPlan {
        val safeTick = if (tickSize > 0.0) tickSize else 0.01

        // 1. Determine Stop Price and Round AWAY from entry to tick size
        // For LONG: stop is BELOW entry -> floor to tick
        // For SHORT: stop is ABOVE entry -> ceil to tick
        val computedStop = when (stopRule) {
            StopRuleType.OR_MIDPOINT -> {
                if (openingRangeHigh != null && openingRangeLow != null) {
                    (openingRangeHigh + openingRangeLow) / 2.0
                } else rawStopPrice
            }
            StopRuleType.OR_EXTREME -> {
                if (side == PositionSide.LONG) openingRangeLow ?: rawStopPrice
                else openingRangeHigh ?: rawStopPrice
            }
            else -> rawStopPrice
        }

        val roundedStop = if (side == PositionSide.LONG) {
            floor(computedStop / safeTick) * safeTick
        } else {
            ceil(computedStop / safeTick) * safeTick
        }

        val riskPerUnit = abs(entryPrice - roundedStop)

        // 2. Determine Target Price and round to tick size
        val computedTarget = when (targetRule) {
            TargetRuleType.R_MULTIPLE -> {
                if (side == PositionSide.LONG) entryPrice + (targetRMultiple * riskPerUnit)
                else entryPrice - (targetRMultiple * riskPerUnit)
            }
            else -> rawTargetPrice ?: if (side == PositionSide.LONG) entryPrice + (2.0 * riskPerUnit) else entryPrice - (2.0 * riskPerUnit)
        }

        val roundedTarget = if (side == PositionSide.LONG) {
            ceil(computedTarget / safeTick) * safeTick
        } else {
            floor(computedTarget / safeTick) * safeTick
        }

        val rewardPerUnit = abs(roundedTarget - entryPrice)
        val netRewardRisk = if (riskPerUnit > 0.0) rewardPerUnit / riskPerUnit else 0.0

        // 3. Flat-by time calculation
        val flatByTs = if (profile.type == MarketProfileType.US_EQUITY) {
            MarketCalendar.getStockFlatByTimestamp(entryTimestamp)
        } else {
            // Crypto: max hold 12 candles on setup TF (e.g. 12 x 5m = 1 hour, or 24h default)
            entryTimestamp + (profile.maxHoldBars * 5 * 60 * 1000L)
        }

        val df = SimpleDateFormat("HH:mm z", Locale.US).apply { timeZone = profile.timeZone }
        val flatByDesc = "Flat-by: ${df.format(flatByTs)} (${if (profile.type == MarketProfileType.US_EQUITY) "No overnight" else "${profile.maxHoldBars} bars max hold"})"

        // 4. Strict Refusal Validations (never loosen anything to pass)
        var error: String? = null

        val minAllowedRisk = max(entryPrice * 0.0005, 3.0 * spread) // max(0.05% of price, 3 x spread)

        when {
            side == PositionSide.LONG && roundedStop >= entryPrice -> {
                error = "Refused: LONG stop ($roundedStop) is at or above entry ($entryPrice)."
            }
            side == PositionSide.SHORT && roundedStop <= entryPrice -> {
                error = "Refused: SHORT stop ($roundedStop) is at or below entry ($entryPrice)."
            }
            side == PositionSide.LONG && roundedTarget <= entryPrice -> {
                error = "Refused: LONG target ($roundedTarget) is at or below entry ($entryPrice)."
            }
            side == PositionSide.SHORT && roundedTarget >= entryPrice -> {
                error = "Refused: SHORT target ($roundedTarget) is at or above entry ($entryPrice)."
            }
            riskPerUnit < minAllowedRisk -> {
                error = "Refused: Risk ($${String.format(Locale.US, "%.4f", riskPerUnit)}) is less than minimum allowed breathing room ($${String.format(Locale.US, "%.4f", minAllowedRisk)} = max(0.05% price, 3x spread))."
            }
            entryTimestamp >= flatByTs -> {
                error = "Refused: Entry time is at or after the required flat-by deadline ($flatByDesc)."
            }
        }

        val orMid = if (openingRangeHigh != null && openingRangeLow != null) (openingRangeHigh + openingRangeLow) / 2.0 else null

        return DayTraderPlan(
            symbol = symbol,
            side = side,
            entryPrice = entryPrice,
            stopRule = stopRule,
            stopPrice = roundedStop,
            targetRule = targetRule,
            targetPrice = roundedTarget,
            riskPerUnit = riskPerUnit,
            rewardPerUnit = rewardPerUnit,
            netRewardRisk = netRewardRisk,
            tickSize = safeTick,
            flatByTimestamp = flatByTs,
            flatByDescription = flatByDesc,
            triggerCondition = triggerCondition,
            isTriggerFired = isTriggerFired,
            validationError = error,
            openingRangeHigh = openingRangeHigh,
            openingRangeLow = openingRangeLow,
            openingRangeMid = orMid,
            createdAt = entryTimestamp
        )
    }
}
