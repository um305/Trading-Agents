package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.example.domain.master.HorizonPlan
import com.example.domain.master.MasterVerdict
import com.example.domain.master.TradingHorizon
import com.example.domain.model.PositionSide
import com.example.domain.model.SignalVerdictState
import com.example.domain.util.PriceFormatter
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ChartShareHelper {

    fun formatHorizonPlanSummary(plan: HorizonPlan): String {
        val sideStr = plan.side?.name ?: "WAIT"
        val zoneStr = plan.formatZoneOrDash()
        val stopStr = plan.formatStopOrDash()
        val tp1Str = plan.tp1?.takeIf { it > 0.0 }?.let { PriceFormatter.formatCurrency(it) } ?: "-"
        val rrStr = plan.tp1Rr?.takeIf { it.isFinite() && it > 0.0 }?.let { String.format(Locale.US, "%.1fR", it) } ?: "-"
        val pWinStr = plan.formatPWinWithWilson()
        return "${plan.horizon.label} (${plan.biasTf.label}/${plan.setupTf.label}/${plan.triggerTf.label}): " +
            "${plan.state.label} $sideStr [${plan.grade.badgeText}] • Zone $zoneStr • SL $stopStr • TP1 $tp1Str ($rrStr) • P(win) $pWinStr"
    }

    fun formatMasterVerdictAsText(verdict: MasterVerdict): String {
        val sb = StringBuilder()
        val liveStr = if (verdict.livePrice > 0.0) PriceFormatter.formatCurrency(verdict.livePrice) else "-"
        sb.appendLine("${verdict.headline} (Live $liveStr • Seed ${verdict.seed})")
        sb.appendLine(verdict.plainBestPlanNumbers)
        sb.appendLine(verdict.plainInvalidation)
        sb.appendLine()
        TradingHorizon.ACTIONABLE.forEach { horizon ->
            verdict.horizonPlans[horizon]?.let { plan ->
                sb.appendLine(formatHorizonPlanSummary(plan))
            }
        }
        sb.appendLine()
        sb.appendLine(verdict.outlook.summaryText)
        if (verdict.conflicts.isNotEmpty()) {
            sb.appendLine("Conflicts: ${verdict.conflicts.joinToString("; ")}")
        }
        sb.appendLine("What flips bias: ${verdict.whatFlipsBias}")
        sb.appendLine("Note: Probabilistic scenario with measured odds — not a guarantee.")
        return sb.toString().trim()
    }

    fun renderHorizonLadderBitmap(verdict: MasterVerdict): Bitmap {
        val width = 1080
        val height = 780
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.parseColor("#0B0F17"))

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#131A26")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#253248")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#94A3B8")
            textSize = 22f
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22D3EE")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rowTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            textSize = 21f
        }

        val topRect = RectF(32f, 28f, width - 32f, 190f)
        canvas.drawRoundRect(topRect, 20f, 20f, cardPaint)
        canvas.drawRoundRect(topRect, 20f, 20f, borderPaint)

        val sideColor = when (verdict.weightedBiasSide) {
            PositionSide.LONG -> Color.parseColor("#10B981")
            PositionSide.SHORT -> Color.parseColor("#EF4444")
            null -> Color.parseColor("#F59E0B")
        }
        titlePaint.color = sideColor
        canvas.drawText(verdict.headline, 58f, 84f, titlePaint)

        val liveStr = if (verdict.livePrice > 0.0) PriceFormatter.formatCurrency(verdict.livePrice) else "-"
        canvas.drawText("Live $liveStr  •  ${verdict.plainBestPlanNumbers}", 58f, 124f, subPaint)
        canvas.drawText(verdict.plainInvalidation.take(86), 58f, 160f, subPaint)

        val ladderRect = RectF(32f, 212f, width - 32f, 646f)
        canvas.drawRoundRect(ladderRect, 20f, 20f, cardPaint)
        canvas.drawRoundRect(ladderRect, 20f, 20f, borderPaint)

        canvas.drawText("HORIZON", 56f, 256f, headerPaint)
        canvas.drawText("STATE / SIDE", 230f, 256f, headerPaint)
        canvas.drawText("ENTRY ZONE", 430f, 256f, headerPaint)
        canvas.drawText("STOP", 670f, 256f, headerPaint)
        canvas.drawText("TP1 / P(WIN)", 830f, 256f, headerPaint)

        var y = 324f
        TradingHorizon.ACTIONABLE.forEachIndexed { idx, horizon ->
            val plan = verdict.horizonPlans[horizon]
            val rowBox = RectF(48f, y - 40f, width - 48f, y + 36f)
            val rowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (idx % 2 == 0) Color.parseColor("#1A2333") else Color.parseColor("#151D2B")
            }
            canvas.drawRoundRect(rowBox, 12f, 12f, rowFill)

            rowTextPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            rowTextPaint.color = Color.WHITE
            canvas.drawText("${horizon.label} (${plan?.setupTf?.label ?: horizon.defaultSetupTf.label})", 62f, y, rowTextPaint)

            val stColor = when (plan?.state) {
                SignalVerdictState.TRADE -> if (plan.side == PositionSide.LONG) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
                SignalVerdictState.WAIT -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#94A3B8")
            }
            rowTextPaint.color = stColor
            val stateLabel = if (plan != null) {
                "${plan.state.label} ${plan.side?.name ?: "WAIT"} [${plan.grade.name.take(1)}]"
            } else {
                "-"
            }
            canvas.drawText(stateLabel, 230f, y, rowTextPaint)

            rowTextPaint.typeface = Typeface.DEFAULT
            rowTextPaint.color = Color.parseColor("#E2E8F0")
            canvas.drawText(plan?.formatZoneOrDash() ?: "-", 430f, y, rowTextPaint)
            canvas.drawText(plan?.formatStopOrDash() ?: "-", 670f, y, rowTextPaint)

            val tp1Str = plan?.tp1?.takeIf { it > 0.0 }?.let { PriceFormatter.formatCurrency(it) } ?: "-"
            val pWinPct = plan?.let { String.format(Locale.US, "%.0f%%", (it.pWin * 100.0).coerceIn(0.0, 100.0)) } ?: "-"
            canvas.drawText("$tp1Str ($pWinPct)", 830f, y, rowTextPaint)
            y += 88f
        }

        val footerRect = RectF(32f, 668f, width - 32f, 752f)
        canvas.drawRoundRect(footerRect, 16f, 16f, cardPaint)
        canvas.drawRoundRect(footerRect, 16f, 16f, borderPaint)
        canvas.drawText(verdict.outlook.summaryText.take(92), 56f, 706f, subPaint)
        canvas.drawText("Scenario odds only (Seed ${verdict.seed}) • Not financial advice", 56f, 736f, subPaint)

        return bitmap
    }

    fun saveMasterVerdictImageToCache(context: Context, verdict: MasterVerdict): File {
        val chartsDir = File(context.cacheDir, "charts").apply { mkdirs() }
        val safeSym = verdict.symbol.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(chartsDir, "${safeSym}_master_verdict.png")
        val bmp = renderHorizonLadderBitmap(verdict)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 96, out)
        }
        return file
    }

    fun shareMasterVerdictImage(context: Context, verdict: MasterVerdict): Boolean {
        return try {
            val file = saveMasterVerdictImageToCache(context, verdict)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${verdict.symbol} Master Verdict (${verdict.marketType.label})")
                putExtra(Intent.EXTRA_TEXT, formatMasterVerdictAsText(verdict))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share ${verdict.symbol} Master Verdict").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }
}
