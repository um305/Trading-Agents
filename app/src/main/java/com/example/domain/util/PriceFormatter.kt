package com.example.domain.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Shared instrument-precision price, percent, R-multiple, and zone formatter.
 * Guarantees:
 * - Never prints "$0.00", "0.0", "NaN", or "Infinity" for prices, percents, or R-multiples.
 * - Missing / invalid / non-positive prices format as "-".
 * - Sub-dollar prices (e.g., DOGE $0.0651) preserve >= 4 significant digits or instrument tick size.
 */
object PriceFormatter {

    fun isValidPositive(value: Double?): Boolean {
        return value != null && !value.isNaN() && !value.isInfinite() && value > 0.0
    }

    fun isValidFinite(value: Double?): Boolean {
        return value != null && !value.isNaN() && !value.isInfinite()
    }

    fun inferTickSize(referencePrice: Double?, explicitTickSize: Double? = null): Double {
        if (explicitTickSize != null && !explicitTickSize.isNaN() && !explicitTickSize.isInfinite() && explicitTickSize > 0.0) {
            return explicitTickSize
        }
        val p = abs(referencePrice ?: 0.0)
        if (p <= 0.0 || p.isNaN() || p.isInfinite()) return 0.01
        return when {
            p >= 1000.0 -> 0.01
            p >= 10.0 -> 0.01
            p >= 1.0 -> 0.001
            else -> {
                val leadingZeros = floor(-log10(p)).toInt().coerceAtLeast(0)
                val decimals = (leadingZeros + 4).coerceIn(4, 10)
                10.0.pow(-decimals)
            }
        }
    }

    fun decimalsForPrice(value: Double, tickSize: Double? = null): Int {
        val absVal = abs(value)
        val tickDecimals = if (tickSize != null && tickSize > 0.0 && !tickSize.isNaN() && !tickSize.isInfinite()) {
            max(0, ceil(-log10(tickSize) - 1e-9).toInt()).coerceIn(0, 10)
        } else {
            0
        }
        val baseDecimals = when {
            absVal >= 100.0 -> 2
            absVal >= 10.0 -> 2
            absVal >= 1.0 -> 3
            absVal > 0.0 -> {
                val leadingZeros = floor(-log10(absVal)).toInt().coerceAtLeast(0)
                // 4 significant digits after leading zeros; e.g., 0.0651 -> leadingZeros=1 -> 5 decimals -> 0.06510
                (leadingZeros + 4).coerceIn(4, 10)
            }
            else -> 2
        }
        return max(baseDecimals, tickDecimals).coerceIn(0, 10)
    }

    fun formatPrice(value: Double?, tickSize: Double? = null): String {
        if (!isValidPositive(value)) return "-"
        val v = value!!
        val decimals = decimalsForPrice(v, tickSize)
        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = if (abs(v) >= 1000.0) {
            if (decimals == 0) "#,##0" else "#,##0.${"0".repeat(decimals)}"
        } else {
            if (decimals == 0) "0" else "0.${"0".repeat(decimals)}"
        }
        return DecimalFormat(pattern, symbols).format(v)
    }

    fun formatCurrency(value: Double?, tickSize: Double? = null): String {
        if (!isValidPositive(value)) return "-"
        return "$${formatPrice(value, tickSize)}"
    }

    fun formatSignedCurrency(value: Double?, tickSize: Double? = null): String {
        if (!isValidFinite(value)) return "-"
        val v = value!!
        if (abs(v) == 0.0) return "$0"
        val sign = if (v >= 0.0) "+" else "-"
        val absVal = abs(v)
        val decimals = decimalsForPrice(absVal, tickSize)
        val symbols = DecimalFormatSymbols(Locale.US)
        val pattern = if (absVal >= 1000.0) "#,##0.${"0".repeat(decimals)}" else "0.${"0".repeat(decimals)}"
        return "$sign$${DecimalFormat(pattern, symbols).format(absVal)}"
    }

    fun formatPercent(pct: Double?, decimals: Int = 2, signed: Boolean = false): String {
        if (!isValidFinite(pct)) return "-"
        val v = pct!!
        val prefix = if (signed && v > 0.0) "+" else ""
        return "$prefix${String.format(Locale.US, "%.${decimals}f", v)}%"
    }

    fun formatSignedPercent(pct: Double?, decimals: Int = 2): String {
        if (!isValidFinite(pct)) return "-"
        val v = pct!!
        return "${String.format(Locale.US, "%+.${decimals}f", v)}%"
    }

    fun formatRMultiple(r: Double?, signed: Boolean = true, decimals: Int = 2): String {
        if (!isValidFinite(r)) return "-"
        val v = r!!
        return if (signed) {
            "${String.format(Locale.US, "%+.${decimals}f", v)}R"
        } else {
            "${String.format(Locale.US, "%.${decimals}f", v)}R"
        }
    }

    fun formatRatio(r: Double?): String {
        if (!isValidPositive(r)) return "-"
        return "1:${String.format(Locale.US, "%.1f", r!!)}"
    }

    fun formatZoneRange(low: Double?, high: Double?, tickSize: Double? = null): String {
        if (!isValidPositive(low) || !isValidPositive(high)) return "-"
        val minBound = minOf(low!!, high!!)
        val maxBound = maxOf(low, high)
        val effectiveTick = tickSize ?: inferTickSize(minBound)
        return "[${formatCurrency(minBound, effectiveTick)} – ${formatCurrency(maxBound, effectiveTick)}]"
    }

    fun formatQuantity(qty: Double?, decimals: Int = 4): String {
        if (!isValidPositive(qty)) return "-"
        val v = qty!!
        return if (abs(v - v.toLong()) < 1e-8) {
            String.format(Locale.US, "%,d", v.toLong())
        } else {
            String.format(Locale.US, "%.${decimals}f", v).trimEnd('0').trimEnd('.')
        }
    }

    fun formatCash(amount: Double?): String {
        if (!isValidFinite(amount) || (amount ?: 0.0) <= 0.0) return "-"
        val v = amount!!
        return if (v >= 100.0) {
            "$${String.format(Locale.US, "%,.0f", v)}"
        } else {
            "$${String.format(Locale.US, "%,.2f", v)}"
        }
    }

    fun formatCompactUsd(value: Double?): String {
        if (!isValidFinite(value)) return "-"
        val v = value!!
        val absV = abs(v)
        val sign = if (v < 0) "-" else ""
        return sign + when {
            absV >= 1_000_000_000_000.0 -> "$${String.format(Locale.US, "%.2fT", absV / 1_000_000_000_000.0)}"
            absV >= 1_000_000_000.0 -> "$${String.format(Locale.US, "%.2fB", absV / 1_000_000_000.0)}"
            absV >= 1_000_000.0 -> "$${String.format(Locale.US, "%.2fM", absV / 1_000_000.0)}"
            absV >= 1_000.0 -> "$${String.format(Locale.US, "%.1fK", absV / 1_000.0)}"
            absV > 0.0 -> "$${String.format(Locale.US, "%.2f", absV)}"
            else -> "$0"
        }
    }
}
