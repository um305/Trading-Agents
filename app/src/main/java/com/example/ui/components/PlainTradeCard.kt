package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.master.HorizonPlan
import com.example.domain.master.MasterVerdict
import com.example.domain.master.TradingHorizon
import com.example.domain.model.PositionSide
import com.example.domain.model.SetupGrade
import com.example.domain.model.SignalVerdictState
import com.example.domain.model.TradingMarketType
import com.example.domain.util.PriceFormatter
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

private fun plainPrice(v: Double?): String =
    v?.takeIf { it.isFinite() && it > 0.0 }?.let { PriceFormatter.formatCurrency(it) } ?: "-"

private fun plainPct(from: Double?, to: Double?): String {
    if (from == null || to == null || !from.isFinite() || !to.isFinite() || from <= 0.0) return ""
    return String.format(Locale.US, "%+.2f%%", (to - from) / from * 100.0)
}

private fun plainPctAbs(from: Double?, to: Double?): String {
    if (from == null || to == null || !from.isFinite() || !to.isFinite() || from <= 0.0) return ""
    return String.format(Locale.US, "%.2f%%", kotlin.math.abs(to - from) / from * 100.0)
}

/**
 * A short, beginner-friendly answer to one question: "What should I do right now?"
 * It only re-states what the engine already decided (same numbers as the big verdict card).
 */
@Composable
fun PlainTradeCard(
    verdict: MasterVerdict,
    selectedHorizon: TradingHorizon,
    isEngineOnly: Boolean,
    modifier: Modifier = Modifier
) {
    val plan: HorizonPlan? = verdict.horizonPlans[selectedHorizon] ?: verdict.bestPlan
    val isSpot = verdict.marketType == TradingMarketType.SPOT
    val hasLevels = plan != null &&
        (plan.limitPrice ?: 0.0) > 0.0 &&
        (plan.stopLoss ?: 0.0) > 0.0

    // ---- 1. Decide the headline + colour -------------------------------------------------
    val side: PositionSide? = plan?.side
    val title: String
    val accent: Color
    val subtitle: String

    if (plan == null || selectedHorizon == TradingHorizon.OUTLOOK) {
        title = "LONG-TERM VIEW ONLY"
        accent = TechCyan
        subtitle = "This view gives background context, not entries. Tap Intraday or Swing in the verdict card below for trade levels."
    } else if (plan.state == SignalVerdictState.TRADE && side != null && hasLevels) {
        if (side == PositionSide.LONG) {
            title = "BUY (go long)"
            accent = BullishGreen
            subtitle = "The engine sees a valid buy setup now."
        } else if (isSpot) {
            title = "DON'T BUY - price may fall"
            accent = BearishRed
            subtitle = "On Spot you can only sell what you already own. To profit from a fall, switch to Futures and open a short."
        } else {
            title = "SELL (go short)"
            accent = BearishRed
            subtitle = "The engine sees a valid sell setup now."
        }
    } else if (plan.state == SignalVerdictState.WAIT) {
        title = "WAIT - do not enter yet"
        accent = RiskAmber
        subtitle = if (plan.needs.isNotBlank()) "Needs: " + plan.needs else "Conditions are not ready yet."
    } else {
        title = "STAY OUT - no clear setup"
        accent = TextSecondary
        val why = plan.vetoes.firstOrNull() ?: plan.conflicts.firstOrNull() ?: verdict.plainSummaryHeadline
        subtitle = if (why.isNotBlank()) why else "Nothing worth trading right now."
    }

    val confidenceText = when (plan?.grade) {
        SetupGrade.A -> "High"
        SetupGrade.B -> "Medium"
        SetupGrade.C_WATCH -> "Low (watch only)"
        else -> "-"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.2.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "WHAT TO DO NOW  -  " + selectedHorizon.label + " view",
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            color = accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = subtitle,
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        if (plan != null && hasLevels && selectedHorizon != TradingHorizon.OUTLOOK) {
            val limit = plan.limitPrice
            val isWait = plan.state != SignalVerdictState.TRADE
            val orderWord = if (side == PositionSide.SHORT) "SELL" else "BUY"

            Spacer(modifier = Modifier.height(2.dp))
            PlainRow(
                label = "Price now",
                value = plainPrice(verdict.livePrice)
            )
            PlainRow(
                label = if (isWait) "If it triggers: $orderWord LIMIT at" else "Enter with a $orderWord LIMIT order at",
                value = plainPrice(limit) +
                    (if (plan.entryZoneMin != null && plan.entryZoneMax != null && (plan.entryZoneMin ?: 0.0) > 0.0)
                        "\n(zone " + plainPrice(plan.entryZoneMin) + " - " + plainPrice(plan.entryZoneMax) + ")"
                    else ""),
                valueColor = TechCyan
            )
            PlainRow(
                label = "Stop-loss (exit here if wrong)",
                value = plainPrice(plan.stopLoss) + "\n(about " + plainPctAbs(limit, plan.stopLoss) + " risk)",
                valueColor = BearishRed
            )
            PlainRow(
                label = "Take profit 1 - sell half",
                value = plainPrice(plan.tp1) + "\n(" + plainPct(limit, plan.tp1) + ")",
                valueColor = BullishGreen
            )
            PlainRow(
                label = "Take profit 2 - sell 30%",
                value = plainPrice(plan.tp2) + "\n(" + plainPct(limit, plan.tp2) + ")",
                valueColor = BullishGreen
            )
            PlainRow(
                label = "Take profit 3 - sell the rest",
                value = plainPrice(plan.tp3) + "\n(" + plainPct(limit, plan.tp3) + ")",
                valueColor = BullishGreen
            )

            val rr = plan.tp1Rr
            if (rr != null && rr.isFinite() && rr > 0.0) {
                PlainRow(
                    label = "Reward vs risk (to take profit 1)",
                    value = "Risk 1 to win " + String.format(Locale.US, "%.1f", rr)
                )
            }
            val pWinPct = (plan.pWin * 100.0).coerceIn(0.0, 100.0)
            PlainRow(
                label = "Engine's chance of reaching take profit 1 before the stop",
                value = String.format(Locale.US, "%.0f%%", pWinPct) +
                    (if (plan.sampleN < 30) "\n(few past examples - rough)" else "")
            )
            PlainRow(label = "Confidence", value = confidenceText)

            val minutesLeft = (plan.validUntilMs - System.currentTimeMillis()) / 60_000L
            if (minutesLeft in 1..(60L * 24L * 14L)) {
                val h = minutesLeft / 60L
                val m = minutesLeft % 60L
                PlainRow(
                    label = "This idea stays valid for about",
                    value = if (h > 0L) "${h}h ${m}m" else "${m}m"
                )
            }
        }

        Text(
            text = "Tip: use a LIMIT order, risk only 1% of your account per trade, and never skip the stop-loss. " +
                (if (isEngineOnly) "Signals come from the built-in engine (no AI key connected). " else "") +
                "These are estimates, not promises.",
            color = TextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun PlainRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(0.5.dp, DarkBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            lineHeight = 17.sp
        )
    }
}
