package com.example.domain.daytrader.timer

import com.example.domain.daytrader.model.MarketProfile
import com.example.domain.daytrader.plan.DayTraderPlan
import com.example.domain.model.CandlePoint
import com.example.domain.model.PositionSide
import com.example.domain.model.SignalVerdictState
import java.text.SimpleDateFormat
import java.util.Locale

enum class EntryTimerState(val label: String) {
    WAITING("WAITING (Trigger Not Met)"),
    TRIGGERED("TRIGGERED (Bar Closed Beyond Level)"),
    EXPIRED("EXPIRED (Window Closed / Invalidated)")
}

data class EntryTimerStatus(
    val state: EntryTimerState,
    val displayBanner: String,
    val triggeringBarTime: String? = null,
    val triggeringBarTimestamp: Long? = null,
    val isBarStillFormingNotice: String? = null,
    val isActionableNow: Boolean = false,
    val transitionReason: String? = null
)

object EntryTimerStateMachine {

    /**
     * Evaluates the Entry Timer state machine strictly on completed bars and only when verdict == TRADE.
     * Under WAIT, even if live price enters the zone, returns WAITING ("Price at zone - not armed").
     */
    fun evaluate(
        plan: DayTraderPlan,
        latestCompletedBar: CandlePoint?,
        isCurrentBarClosed: Boolean,
        profile: MarketProfile,
        currentTimestamp: Long = System.currentTimeMillis(),
        hasActiveVeto: Boolean = false,
        vetoMessage: String? = null,
        verdictState: SignalVerdictState = SignalVerdictState.TRADE,
        isInsideZone: Boolean = false
    ): EntryTimerStatus {
        val df = SimpleDateFormat("HH:mm:ss z", Locale.US).apply { timeZone = profile.timeZone }

        // 1. Check Expired conditions: flat-by passed or active veto
        if (currentTimestamp >= plan.flatByTimestamp) {
            return EntryTimerStatus(
                state = EntryTimerState.EXPIRED,
                displayBanner = "EXPIRED: Flat-by deadline reached (${df.format(plan.flatByTimestamp)}). Plan cancelled.",
                transitionReason = "Flat-by time reached"
            )
        }
        if (hasActiveVeto || verdictState == SignalVerdictState.NO_TRADE) {
            return EntryTimerStatus(
                state = EntryTimerState.EXPIRED,
                displayBanner = "EXPIRED: Veto condition triggered (${vetoMessage ?: "Market structure invalid"}).",
                transitionReason = vetoMessage ?: "Veto triggered"
            )
        }

        val formingNotice = if (!isCurrentBarClosed) "waiting: bar still forming" else null

        // 2. Verdict must be TRADE before a completed bar can trigger
        if (verdictState != SignalVerdictState.TRADE) {
            val msg = if (isInsideZone) {
                "WAITING: Price at zone - not armed"
            } else {
                "WAITING: Plan conditional (${plan.triggerCondition}). ${formingNotice ?: ""}".trim()
            }
            return EntryTimerStatus(
                state = EntryTimerState.WAITING,
                displayBanner = msg,
                isBarStillFormingNotice = formingNotice,
                isActionableNow = false,
                transitionReason = if (isInsideZone) "Price at zone - not armed" else "Verdict is WAIT"
            )
        }

        if (latestCompletedBar == null) {
            return EntryTimerStatus(
                state = EntryTimerState.WAITING,
                displayBanner = "WAITING: Waiting for first completed bar on setup timeframe.",
                isBarStillFormingNotice = formingNotice
            )
        }

        // 3. Evaluate completed bar trigger ONLY when the evaluated bar is closed
        if (!latestCompletedBar.isClosed) {
            return EntryTimerStatus(
                state = EntryTimerState.WAITING,
                displayBanner = "WAITING: Bar still forming on setup timeframe (awaiting close).",
                isBarStillFormingNotice = "waiting: bar still forming",
                isActionableNow = false
            )
        }

        val barClose = latestCompletedBar.close
        val barTimeStr = df.format(latestCompletedBar.timestamp)

        val isTriggered = if (plan.side == PositionSide.LONG) {
            barClose >= plan.entryPrice
        } else {
            barClose <= plan.entryPrice
        }

        return if (isTriggered) {
            EntryTimerStatus(
                state = EntryTimerState.TRIGGERED,
                displayBanner = "TRIGGERED: Completed bar at $barTimeStr closed at $${String.format(Locale.US, "%.4f", barClose)} (Beyond trigger level). Limit / Entry armed.",
                triggeringBarTime = barTimeStr,
                triggeringBarTimestamp = latestCompletedBar.timestamp,
                isBarStillFormingNotice = formingNotice,
                isActionableNow = true,
                transitionReason = "Bar closed beyond trigger level"
            )
        } else {
            EntryTimerStatus(
                state = EntryTimerState.WAITING,
                displayBanner = "WAITING: Last completed bar closed at $${String.format(Locale.US, "%.4f", barClose)} (Trigger: $${String.format(Locale.US, "%.4f", plan.entryPrice)}). ${formingNotice ?: ""}".trim(),
                isBarStillFormingNotice = formingNotice,
                isActionableNow = false
            )
        }
    }
}
