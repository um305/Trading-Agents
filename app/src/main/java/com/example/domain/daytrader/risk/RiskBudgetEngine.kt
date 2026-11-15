package com.example.domain.daytrader.risk

import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.plan.DayTraderPlan
import com.example.domain.daytrader.replay.ReplayLean
import com.example.domain.daytrader.replay.ReplayVerdict
import com.example.domain.model.PositionSide
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

import com.example.domain.util.PriceFormatter

data class RiskSettings(
    val accountBalance: Double = 0.0,
    val maxRiskPerTradeDollars: Double = 0.0,
    val riskPercent: Double = 1.0,
    val isConfigured: Boolean = false,
    val dailyLossLimitR: Double = 3.0,          // stop trading for day at -3R
    val maxTradesPerDay: Int = 3,
    val minNetRewardRisk: Double = 1.5,
    val maxPositionSizeNotional: Double = 50_000.0,
    val availableCashOrMargin: Double = 0.0,
    val maxLeverage: Int = 10,
    val isCooldownActive: Boolean = false,
    val cooldownReason: String? = null
)

data class RiskCheckItem(
    val title: String,
    val isPassed: Boolean, // true = PASS, false = BLOCK
    val detail: String
)

data class OrderTicket(
    val symbol: String,
    val side: PositionSide,
    val quantity: Double,
    val unitLabel: String, // "shares" or "contracts"
    val entryPrice: Double,
    val orderType: String = "LIMIT",
    val stopLossTriggerPrice: Double,
    val stopLossType: String = "STOP_MARKET",
    val takeProfitPrice: Double,
    val reduceOnly: Boolean = true,
    val estimatedNotional: Double,
    val marginRequired: Double,
    val estimatedLiquidationPrice: Double,
    val liquidationDistanceRatio: Double,
    val formattedClipboardTicket: String
)

data class RiskEvaluationResult(
    val isAllowed: Boolean,
    val sizedQuantity: Double,
    val unitLabel: String,
    val checks: List<RiskCheckItem>,
    val blockedReasons: List<String>,
    val orderTicket: OrderTicket?,
    val statusBanner: String,
    val isAccountUnconfigured: Boolean = false,
    val isMinNotionalExceeded: Boolean = false,
    val minNotionalDollars: Double = 0.0,
    val minNotionalRiskDollars: Double = 0.0,
    val minNotionalRiskPctOfAccount: Double = 0.0,
    val paperFollowAllowed: Boolean = true
)

object RiskBudgetEngine {

    /**
     * Sizer & Comprehensive Risk Budget Checks.
     */
    fun evaluateRiskAndSize(
        plan: DayTraderPlan,
        profile: MarketProfile,
        settings: RiskSettings = RiskSettings(),
        tradesTakenToday: Int = 0,
        realizedRToday: Double = 0.0,
        replayVerdict: ReplayVerdict? = null,
        currentTimestamp: Long = System.currentTimeMillis(),
        lotStepSize: Double = 1.0, // 1.0 for shares, 0.001 or 1 for crypto perps
        selectedLeverage: Int = 1,
        sizeMultiplier: Double = 1.0,
        acceptHigherRisk: Boolean = false
    ): RiskEvaluationResult {
        val checks = mutableListOf<RiskCheckItem>()
        val blockedReasons = mutableListOf<String>()
        val unitLabel = if (profile.type == com.example.domain.daytrader.model.MarketProfileType.US_EQUITY) "shares" else "contracts"
        val isDailyOrCooldownTripped = (realizedRToday <= -settings.dailyLossLimitR) || settings.isCooldownActive || (tradesTakenToday >= settings.maxTradesPerDay)

        // 0. Valid Plan Guard
        if (plan.entryPrice <= 0.0 || plan.stopPrice <= 0.0 || plan.targetPrice <= 0.0 || plan.riskPerUnit <= 0.0 || plan.validationError != null) {
            checks.add(
                RiskCheckItem(
                    title = "Plan Validity",
                    isPassed = false,
                    detail = "BLOCK: No valid plan (${plan.validationError ?: "missing valid entry/stop/target"})."
                )
            )
            blockedReasons.add("No valid plan")
            return RiskEvaluationResult(
                isAllowed = false,
                sizedQuantity = 0.0,
                unitLabel = unitLabel,
                checks = checks,
                blockedReasons = blockedReasons,
                orderTicket = null,
                statusBanner = "Risk blocked: No valid plan",
                paperFollowAllowed = false
            )
        }

        // 0b. Account Configuration Guard — NEVER invent $25,000 or show $ placeholders before configured
        if (!settings.isConfigured || settings.accountBalance <= 0.0 || settings.maxRiskPerTradeDollars <= 0.0) {
            checks.add(
                RiskCheckItem(
                    title = "Account Risk Setup",
                    isPassed = false,
                    detail = "account not set — configure Account Size and Risk % to calculate real position size."
                )
            )
            blockedReasons.add("account not set")
            return RiskEvaluationResult(
                isAllowed = false,
                sizedQuantity = 0.0,
                unitLabel = unitLabel,
                checks = checks,
                blockedReasons = blockedReasons,
                orderTicket = null,
                statusBanner = "account not set",
                isAccountUnconfigured = true,
                paperFollowAllowed = !isDailyOrCooldownTripped
            )
        }

        // 1. Sizer calculation: quantity = min(floor_to_step(max loss / risk per unit), margin cap)
        val baseRiskBudget = if (settings.maxRiskPerTradeDollars > 0.0) {
            settings.maxRiskPerTradeDollars
        } else {
            settings.accountBalance * (settings.riskPercent / 100.0)
        }
        val effectiveRiskBudget = baseRiskBudget * sizeMultiplier.coerceIn(0.0, 1.0)
        val effectiveLeverage = selectedLeverage.coerceIn(1, settings.maxLeverage.coerceAtLeast(1))
        val rawQty = effectiveRiskBudget / plan.riskPerUnit
        val effectiveAvailableMargin = if (settings.availableCashOrMargin > 0.0) settings.availableCashOrMargin else settings.accountBalance
        val maxQtyByMargin = if (effectiveAvailableMargin > 0.0 && plan.entryPrice > 0.0) {
            (effectiveAvailableMargin * effectiveLeverage) / plan.entryPrice
        } else {
            0.0
        }
        val cappedRawQty = if (maxQtyByMargin > 0.0) minOf(rawQty, maxQtyByMargin) else 0.0
        val sizedQuantity = if (lotStepSize > 0.0) {
            floor(cappedRawQty / lotStepSize) * lotStepSize
        } else {
            floor(cappedRawQty)
        }

        val minOrderQty = maxOf(lotStepSize, 1e-6)
        val minNotionalDollars = minOrderQty * plan.entryPrice
        val minNotionalRiskDollars = minOrderQty * plan.riskPerUnit
        val minNotionalRiskPct = if (settings.accountBalance > 0.0) (minNotionalRiskDollars / settings.accountBalance) * 100.0 else 0.0
        val isMinNotionalExceeded = sizedQuantity <= 0.0
        val orderQtyForExecution = if (isMinNotionalExceeded && acceptHigherRisk) minOrderQty else sizedQuantity

        if (isMinNotionalExceeded) {
            val minNotionalMessage = "Minimum order exceeds your risk budget: min notional ${PriceFormatter.formatCash(minNotionalDollars)} -> risk if stopped ${PriceFormatter.formatCash(minNotionalRiskDollars)} (${String.format(Locale.US, "%.1f", minNotionalRiskPct)}% of account)"
            if (!acceptHigherRisk) {
                checks.add(
                    RiskCheckItem(
                        title = "Position Sizing",
                        isPassed = false,
                        detail = minNotionalMessage
                    )
                )
                blockedReasons.add(minNotionalMessage)
            } else {
                checks.add(
                    RiskCheckItem(
                        title = "Position Sizing",
                        isPassed = true,
                        detail = "OVERRIDE (this trade): Accepting min order ${PriceFormatter.formatQuantity(orderQtyForExecution)} $unitLabel (risk ${PriceFormatter.formatCash(minNotionalRiskDollars)}, ${String.format(Locale.US, "%.1f", minNotionalRiskPct)}% of account)"
                    )
                )
            }
        } else {
            val cappedNote = if (maxQtyByMargin < rawQty) " (Margin-capped)" else ""
            checks.add(
                RiskCheckItem(
                    title = "Position Sizing",
                    isPassed = true,
                    detail = "PASS: ${PriceFormatter.formatQuantity(sizedQuantity)} $unitLabel$cappedNote (Planned Risk: ${PriceFormatter.formatCash(sizedQuantity * plan.riskPerUnit)})"
                )
            )
        }

        // 2. Daily Loss Limit Check (HARD risk safety cap)
        if (realizedRToday <= -settings.dailyLossLimitR) {
            checks.add(
                RiskCheckItem(
                    title = "Daily Loss Limit",
                    isPassed = false,
                    detail = "BLOCK: Realized R today (${String.format(Locale.US, "%.2f", realizedRToday)}R) reached daily loss ceiling (-${settings.dailyLossLimitR}R)."
                )
            )
            blockedReasons.add("Daily loss limit reached (${String.format(Locale.US, "%.2f", realizedRToday)}R / -${settings.dailyLossLimitR}R). Stop trading for today.")
        } else {
            checks.add(
                RiskCheckItem(
                    title = "Daily Loss Limit",
                    isPassed = true,
                    detail = "PASS: Realized R today is ${String.format(Locale.US, "%+.2f", realizedRToday)}R (Limit: -${settings.dailyLossLimitR}R)"
                )
            )
        }

        // 3. Max Trades Per Day Check
        if (tradesTakenToday >= settings.maxTradesPerDay) {
            checks.add(
                RiskCheckItem(
                    title = "Max Trades Per Day",
                    isPassed = false,
                    detail = "BLOCK: $tradesTakenToday/${settings.maxTradesPerDay} daily trades already taken."
                )
            )
            blockedReasons.add("Max daily trade cap reached ($tradesTakenToday/${settings.maxTradesPerDay}).")
        } else {
            checks.add(
                RiskCheckItem(
                    title = "Max Trades Per Day",
                    isPassed = true,
                    detail = "PASS: $tradesTakenToday/${settings.maxTradesPerDay} daily trades taken."
                )
            )
        }

        // 4. Net Reward:Risk Check (informational when >= 1.5, C / WATCH already handles < 1.5 in SignalEngineV2)
        if (plan.netRewardRisk < settings.minNetRewardRisk) {
            checks.add(
                RiskCheckItem(
                    title = "Reward to Risk",
                    isPassed = true,
                    detail = "NOTE: Net R:R (${String.format(Locale.US, "%.2f", plan.netRewardRisk)}) < ${settings.minNetRewardRisk} — C / WATCH conditional entry."
                )
            )
        } else {
            checks.add(
                RiskCheckItem(
                    title = "Reward to Risk",
                    isPassed = true,
                    detail = "PASS: Net R:R is 1:${String.format(Locale.US, "%.2f", plan.netRewardRisk)}"
                )
            )
        }

        // 5. Entry Window Check (NEVER blocks in RiskBudgetEngine; SignalEngineV2 downgrades stocks outside window to C / WATCH)
        val cal = Calendar.getInstance(profile.timeZone).apply { timeInMillis = currentTimestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val insideWindow = profile.entryWindows.isEmpty() || profile.entryWindows.any { it.contains(hour, minute) }

        if (!insideWindow) {
            val validWindowsStr = profile.entryWindows.joinToString(", ") { it.toString() }
            checks.add(
                RiskCheckItem(
                    title = "Entry Window",
                    isPassed = true,
                    detail = "NOTE: Outside optimal windows ($validWindowsStr) — Alert / C WATCH applies."
                )
            )
        } else {
            val label = if (profile.entryWindows.isEmpty()) "24/7 continuous session" else String.format(Locale.US, "%02d:%02d", hour, minute)
            checks.add(
                RiskCheckItem(
                    title = "Entry Window",
                    isPassed = true,
                    detail = "PASS: Active entry schedule ($label)"
                )
            )
        }

        // 6. Max Position Size Notional & Margin
        val notional = orderQtyForExecution * plan.entryPrice
        val marginRequired = notional / effectiveLeverage

        if (notional > settings.maxPositionSizeNotional && settings.maxPositionSizeNotional > 0.0) {
            checks.add(
                RiskCheckItem(
                    title = "Notional Exposure",
                    isPassed = false,
                    detail = "BLOCK: Notional (${PriceFormatter.formatCash(notional)}) exceeds max ceiling (${PriceFormatter.formatCash(settings.maxPositionSizeNotional)})."
                )
            )
            blockedReasons.add("Position notional (${PriceFormatter.formatCash(notional)}) exceeds maximum allowed.")
        } else {
            checks.add(
                RiskCheckItem(
                    title = "Notional Exposure",
                    isPassed = true,
                    detail = "PASS: ${PriceFormatter.formatCash(notional)} Notional"
                )
            )
        }

        // 7. Available Cash or Margin
        if (marginRequired > effectiveAvailableMargin || effectiveAvailableMargin <= 0.0) {
            checks.add(
                RiskCheckItem(
                    title = "Available Margin",
                    isPassed = false,
                    detail = "BLOCK: Margin required (${PriceFormatter.formatCash(marginRequired)}) exceeds available (${PriceFormatter.formatCash(effectiveAvailableMargin)})."
                )
            )
            blockedReasons.add("Margin too low (${PriceFormatter.formatCash(marginRequired)} required vs ${PriceFormatter.formatCash(effectiveAvailableMargin)} available).")
        } else {
            checks.add(
                RiskCheckItem(
                    title = "Available Margin",
                    isPassed = true,
                    detail = "PASS: Margin req ${PriceFormatter.formatCash(marginRequired)} <= Available ${PriceFormatter.formatCash(effectiveAvailableMargin)}"
                )
            )
        }

        // 8. Leverage Limit
        if (selectedLeverage > settings.maxLeverage) {
            checks.add(
                RiskCheckItem(
                    title = "Leverage Limit",
                    isPassed = false,
                    detail = "BLOCK: ${selectedLeverage}x exceeds max allowed (${settings.maxLeverage}x)."
                )
            )
            blockedReasons.add("Leverage exceeds maximum allowed ceiling.")
        } else {
            checks.add(
                RiskCheckItem(
                    title = "Leverage Limit",
                    isPassed = true,
                    detail = "PASS: ${effectiveLeverage}x Isolated"
                )
            )
        }

        // 9. Loss-streak Cooldown Check
        if (settings.isCooldownActive) {
            checks.add(
                RiskCheckItem(
                    title = "Streak Cooldown",
                    isPassed = false,
                    detail = "BLOCK: Loss-streak cooldown active (${settings.cooldownReason ?: "Recent consecutive losses"})."
                )
            )
            blockedReasons.add("Loss-streak cooldown active.")
        } else {
            checks.add(
                RiskCheckItem(
                    title = "Streak Cooldown",
                    isPassed = true,
                    detail = "PASS: No active loss-streak pause"
                )
            )
        }

        // 10. Replay Verdict Check (soft/advisory block for execution if stop-leaning)
        if (replayVerdict != null) {
            when (replayVerdict.lean) {
                ReplayLean.STRONGLY_STOP, ReplayLean.LEANS_STOP -> {
                    checks.add(
                        RiskCheckItem(
                            title = "Replay Engine",
                            isPassed = false,
                            detail = "BLOCK: Replay simulation ${replayVerdict.headline}"
                        )
                    )
                    blockedReasons.add("Replay Agent veto: ${replayVerdict.headline}")
                }
                ReplayLean.INSUFFICIENT_RESOLVED -> {
                    checks.add(
                        RiskCheckItem(
                            title = "Replay Engine",
                            isPassed = true,
                            detail = "NEUTRAL: ${replayVerdict.headline} (no directional veto)"
                        )
                    )
                }
                else -> {
                    checks.add(
                        RiskCheckItem(
                            title = "Replay Engine",
                            isPassed = true,
                            detail = "PASS: ${replayVerdict.headline}"
                        )
                    )
                }
            }
        }

        // Liquidation distance calculation for perps
        val liqPrice = if (plan.side == PositionSide.LONG) {
            plan.entryPrice * (1.0 - (1.0 / effectiveLeverage) * 0.90)
        } else {
            plan.entryPrice * (1.0 + (1.0 / effectiveLeverage) * 0.90)
        }
        val liqDistance = abs(plan.entryPrice - liqPrice)
        val liqDistanceRatio = if (plan.riskPerUnit > 0.0) liqDistance / plan.riskPerUnit else 5.0

        if (profile.type != com.example.domain.daytrader.model.MarketProfileType.US_EQUITY && liqDistanceRatio < 3.0) {
            checks.add(
                RiskCheckItem(
                    title = "Liquidation Buffer",
                    isPassed = false,
                    detail = "BLOCK: Liquidation too close (${String.format(Locale.US, "%.1f", liqDistanceRatio)}x < 3.0x stop distance)."
                )
            )
            blockedReasons.add("Liquidation too close (${String.format(Locale.US, "%.1f", liqDistanceRatio)}x < 3x stop distance).")
        }

        val isOverallAllowed = blockedReasons.isEmpty() && plan.validationError == null && orderQtyForExecution > 0.0

        val orderTicket = if (isOverallAllowed && orderQtyForExecution > 0.0) {
            val clip = StringBuilder().apply {
                append("=== DAY-TRADER ORDER TICKET ===\n")
                append("Symbol: ${plan.symbol}\n")
                append("Action: ${plan.side.name}\n")
                append("Quantity: ${PriceFormatter.formatQuantity(orderQtyForExecution)} $unitLabel\n")
                append("Entry (LIMIT): ${PriceFormatter.formatCurrency(plan.entryPrice)}\n")
                append("Stop Loss (STOP_MARKET): ${PriceFormatter.formatCurrency(plan.stopPrice)}\n")
                append("Take Profit (LIMIT): ${PriceFormatter.formatCurrency(plan.targetPrice)}\n")
                append("Notional: ${PriceFormatter.formatCash(notional)}\n")
                append("Leverage: ${effectiveLeverage}x Isolated\n")
                append("Flat-by: ${plan.flatByDescription}\n")
                append("Reduce-Only: TRUE\n")
            }.toString()

            OrderTicket(
                symbol = plan.symbol,
                side = plan.side,
                quantity = orderQtyForExecution,
                unitLabel = unitLabel,
                entryPrice = plan.entryPrice,
                stopLossTriggerPrice = plan.stopPrice,
                takeProfitPrice = plan.targetPrice,
                reduceOnly = true,
                estimatedNotional = notional,
                marginRequired = marginRequired,
                estimatedLiquidationPrice = liqPrice,
                liquidationDistanceRatio = liqDistanceRatio,
                formattedClipboardTicket = clip
            )
        } else null

        val statusBanner = if (isOverallAllowed) {
            if (isMinNotionalExceeded && acceptHigherRisk) {
                "RISK OVERRIDE ACCEPTED (this trade): Min notional ${PriceFormatter.formatCash(minNotionalDollars)} ticket armed."
            } else {
                "RISK APPROVED: Plan passed risk gates. Order ticket generated."
            }
        } else {
            blockedReasons.firstOrNull() ?: "Risk blocked: ${plan.validationError ?: "Policy constraint"}"
        }

        return RiskEvaluationResult(
            isAllowed = isOverallAllowed,
            sizedQuantity = orderQtyForExecution,
            unitLabel = unitLabel,
            checks = checks,
            blockedReasons = blockedReasons,
            orderTicket = orderTicket,
            statusBanner = statusBanner,
            isAccountUnconfigured = false,
            isMinNotionalExceeded = isMinNotionalExceeded,
            minNotionalDollars = minNotionalDollars,
            minNotionalRiskDollars = minNotionalRiskDollars,
            minNotionalRiskPctOfAccount = minNotionalRiskPct,
            paperFollowAllowed = !isDailyOrCooldownTripped
        )
    }
}
