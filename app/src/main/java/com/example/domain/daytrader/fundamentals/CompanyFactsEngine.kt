package com.example.domain.daytrader.fundamentals

import com.squareup.moshi.Moshi
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class QuarterlyFinancialFact(
    val quarterLabel: String,
    val revenueUsd: Double?,
    val netIncomeUsd: Double?,
    val epsUsd: Double?,
    val grossMarginPercent: Double?,
    val yoyRevenueGrowthPercent: Double?
)

data class CompanyFactsXbrl(
    val symbol: String,
    val cik: String,
    val quarters: List<QuarterlyFinancialFact>,
    val whatChangedNumbersOnly: List<String>,
    val latestGrossMarginPercent: Double?,
    val latestEpsUsd: Double?,
    val latestYoYGrowthPercent: Double?
)

data class StockFundamentalsResult(
    val facts: CompanyFactsXbrl? = null,
    val unavailableReason: String? = null,
    val lastUpdatedMs: Long = 0L
)

data class CryptoFundamentals(
    val symbol: String,
    val marketCapUsd: Double?,
    val fdvUsd: Double?,
    val circulatingSupply: Double?,
    val totalSupply: Double?,
    val volumeToMarketCapRatio: Double?,
    val circulatingSharePercent: Double?
)

object CompanyFactsEngine {

    private val moshiMapAdapter = Moshi.Builder().build().adapter(Map::class.java)

    /**
     * Strips equity-perp suffixes (-PERP, _PERP, BUSDT, USDT, USDC, /USD, -USD) so
     * equity perps map back to the underlying stock's SEC CIK and XBRL facts.
     */
    fun normalizeEquityTickerForSec(symbol: String): String {
        var clean = symbol.trim().uppercase(Locale.US)
        val suffixes = listOf("-PERP", "_PERP", "PERP", "BUSDT", "USDT", "USDC", "/USD", "-USD")
        for (sfx in suffixes) {
            if (clean.endsWith(sfx) && clean.length > sfx.length) {
                clean = clean.removeSuffix(sfx)
            }
        }
        return clean.trim()
    }

    /**
     * Parses raw SEC EDGAR XBRL `api/xbrl/companyfacts/CIK{cik10}.json` JSON payload
     * for the last 8 quarters of revenue, net income, EPS, gross margin, YoY growth, and "what changed".
     */
    @Suppress("UNCHECKED_CAST")
    fun parseCompanyFactsJson(
        symbol: String,
        cik10: String,
        jsonString: String
    ): CompanyFactsXbrl {
        val root = (moshiMapAdapter.fromJson(jsonString) as? Map<String, Any?>).orEmpty()
        val factsObj = (root["facts"] as? Map<String, Any?>).orEmpty()
        val usGaap = (factsObj["us-gaap"] as? Map<String, Any?>)
            ?: (factsObj["ifrs-full"] as? Map<String, Any?>).orEmpty()

        val revSeries = extractQuarterlyTagSeries(
            usGaap,
            listOf(
                "RevenueFromContractWithCustomerExcludingAssessedTax",
                "Revenues",
                "SalesRevenueNet",
                "RevenueFromContractWithCustomerIncludingAssessedTax"
            ),
            unitKeys = listOf("USD"),
            isPerShare = false
        )
        val netIncSeries = extractQuarterlyTagSeries(
            usGaap,
            listOf("NetIncomeLoss", "ProfitLoss"),
            unitKeys = listOf("USD"),
            isPerShare = false
        )
        val epsSeries = extractQuarterlyTagSeries(
            usGaap,
            listOf("EarningsPerShareDiluted", "EarningsPerShareBasic"),
            unitKeys = listOf("USD/shares"),
            isPerShare = true
        )
        val grossProfitSeries = extractQuarterlyTagSeries(
            usGaap,
            listOf("GrossProfit"),
            unitKeys = listOf("USD"),
            isPerShare = false
        )

        val allKeys = (revSeries.keys + netIncSeries.keys + epsSeries.keys + grossProfitSeries.keys)
            .distinct()
            .sortedWith(compareByDescending<String> { periodSortKey(it) })

        val quarters = allKeys.take(8).map { key ->
            val rev = revSeries[key]
            val ni = netIncSeries[key]
            val eps = epsSeries[key]
            val gp = grossProfitSeries[key]
            val gmPct = if (gp != null && rev != null && rev > 0.0) (gp / rev) * 100.0 else null
            val prevYearKey = previousYearFrame(key)
            val prevYearRev = if (prevYearKey != null) revSeries[prevYearKey] else null
            val yoyPct = if (rev != null && prevYearRev != null && prevYearRev > 0.0) {
                ((rev - prevYearRev) / prevYearRev) * 100.0
            } else null

            QuarterlyFinancialFact(
                quarterLabel = key,
                revenueUsd = rev,
                netIncomeUsd = ni,
                epsUsd = eps,
                grossMarginPercent = gmPct,
                yoyRevenueGrowthPercent = yoyPct
            )
        }

        return buildStockFacts(
            symbol = normalizeEquityTickerForSec(symbol),
            cik = cik10,
            rawQuarters = quarters
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractQuarterlyTagSeries(
        gaapObj: Map<String, Any?>,
        tagNames: List<String>,
        unitKeys: List<String>,
        isPerShare: Boolean
    ): Map<String, Double> {
        val result = linkedMapOf<String, Double>()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

        for (tag in tagNames) {
            val tagObj = gaapObj[tag] as? Map<String, Any?> ?: continue
            val unitsObj = tagObj["units"] as? Map<String, Any?> ?: continue
            var arr: List<Any?>? = null
            for (uKey in unitKeys) {
                arr = unitsObj[uKey] as? List<Any?>
                if (!arr.isNullOrEmpty()) break
            }
            if (arr.isNullOrEmpty()) {
                for ((k, v) in unitsObj) {
                    if (!isPerShare || k.contains("shares", ignoreCase = true)) {
                        val list = v as? List<Any?>
                        if (!list.isNullOrEmpty()) {
                            arr = list
                            break
                        }
                    }
                }
            }
            if (arr == null) continue

            for (rawItem in arr) {
                val item = rawItem as? Map<String, Any?> ?: continue
                val form = (item["form"] as? String).orEmpty()
                if (form != "10-Q" && form != "10-K") continue
                val valNum = (item["val"] as? Number)?.toDouble() ?: continue
                if (valNum.isNaN() || valNum.isInfinite()) continue

                val frame = (item["frame"] as? String).orEmpty().trim()
                if (frame.matches(Regex("CY\\d{4}Q[1-4]"))) {
                    result.putIfAbsent(frame, valNum)
                    continue
                }

                // Fallback: single-quarter duration (~75-115 days)
                val startStr = (item["start"] as? String).orEmpty()
                val endStr = (item["end"] as? String).orEmpty()
                if (startStr.isNotBlank() && endStr.isNotBlank()) {
                    val startMs = runCatching { dateFmt.parse(startStr)?.time }.getOrNull() ?: continue
                    val endMs = runCatching { dateFmt.parse(endStr)?.time }.getOrNull() ?: continue
                    val days = (endMs - startMs) / 86_400_000L
                    if (days in 75L..115L) {
                        val derivedFrame = deriveFrameFromEndDate(endStr)
                        if (derivedFrame != null) {
                            result.putIfAbsent(derivedFrame, valNum)
                        }
                    }
                }
            }
            if (result.isNotEmpty()) break
        }
        return result
    }

    private fun deriveFrameFromEndDate(endDate: String): String? {
        val parts = endDate.split("-")
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val q = ((month - 1) / 3) + 1
        return "CY${year}Q${q.coerceIn(1, 4)}"
    }

    private fun previousYearFrame(frame: String): String? {
        val m = Regex("CY(\\d{4})Q([1-4])").matchEntire(frame) ?: return null
        val y = m.groupValues[1].toIntOrNull() ?: return null
        val q = m.groupValues[2]
        return "CY${y - 1}Q$q"
    }

    private fun periodSortKey(frame: String): Int {
        val m = Regex("CY(\\d{4})Q([1-4])").matchEntire(frame) ?: return 0
        val y = m.groupValues[1].toIntOrNull() ?: 0
        val q = m.groupValues[2].toIntOrNull() ?: 0
        return y * 10 + q
    }

    /**
     * Parses and calculates the 8-quarter financial record and the "what changed" list (numbers only).
     */
    fun buildStockFacts(
        symbol: String,
        cik: String,
        rawQuarters: List<QuarterlyFinancialFact>
    ): CompanyFactsXbrl {
        val quartersSorted = rawQuarters.take(8)
        val whatChanged = mutableListOf<String>()

        if (quartersSorted.size >= 2) {
            val qLatest = quartersSorted[0]
            val qPrior = quartersSorted[1]

            if (qLatest.revenueUsd != null && qPrior.revenueUsd != null && qPrior.revenueUsd > 0) {
                val revChgPct = ((qLatest.revenueUsd - qPrior.revenueUsd) / qPrior.revenueUsd) * 100.0
                whatChanged.add("Revenue: $${formatLarge(qLatest.revenueUsd)} vs $${formatLarge(qPrior.revenueUsd)} (${String.format(Locale.US, "%+.1f", revChgPct)}% QoQ)")
            }

            if (qLatest.netIncomeUsd != null && qPrior.netIncomeUsd != null && kotlin.math.abs(qPrior.netIncomeUsd) > 1.0) {
                val niChgPct = ((qLatest.netIncomeUsd - qPrior.netIncomeUsd) / kotlin.math.abs(qPrior.netIncomeUsd)) * 100.0
                whatChanged.add("Net Income: $${formatLarge(qLatest.netIncomeUsd)} vs $${formatLarge(qPrior.netIncomeUsd)} (${String.format(Locale.US, "%+.1f", niChgPct)}% QoQ)")
            }

            if (qLatest.epsUsd != null && qPrior.epsUsd != null) {
                val epsDiff = qLatest.epsUsd - qPrior.epsUsd
                whatChanged.add("EPS: $${String.format(Locale.US, "%.2f", qLatest.epsUsd)} vs $${String.format(Locale.US, "%.2f", qPrior.epsUsd)} (${String.format(Locale.US, "%+.2f", epsDiff)} diff)")
            }

            if (qLatest.grossMarginPercent != null && qPrior.grossMarginPercent != null) {
                val gmDiffBps = (qLatest.grossMarginPercent - qPrior.grossMarginPercent) * 100.0
                whatChanged.add("Gross Margin: ${String.format(Locale.US, "%.1f", qLatest.grossMarginPercent)}% vs ${String.format(Locale.US, "%.1f", qPrior.grossMarginPercent)}% (${String.format(Locale.US, "%+.0f", gmDiffBps)} bps)")
            }

            if (qLatest.yoyRevenueGrowthPercent != null) {
                whatChanged.add("YoY Revenue Growth: ${String.format(Locale.US, "%+.1f", qLatest.yoyRevenueGrowthPercent)}%")
            }
        }

        return CompanyFactsXbrl(
            symbol = symbol,
            cik = cik,
            quarters = quartersSorted,
            whatChangedNumbersOnly = whatChanged,
            latestGrossMarginPercent = quartersSorted.firstOrNull()?.grossMarginPercent,
            latestEpsUsd = quartersSorted.firstOrNull()?.epsUsd,
            latestYoYGrowthPercent = quartersSorted.firstOrNull()?.yoyRevenueGrowthPercent
        )
    }

    /**
     * Builds Crypto fundamentals (Market cap, FDV, circulating share, 24h vol / market cap).
     * If data is missing, safely defaults to null so UI renders "N/A".
     */
    fun buildCryptoFundamentals(
        symbol: String,
        marketCap: Double?,
        fdv: Double?,
        circulatingSupply: Double?,
        totalSupply: Double?,
        volume24h: Double?
    ): CryptoFundamentals {
        val volToMc = if (volume24h != null && marketCap != null && marketCap > 0) volume24h / marketCap else null
        val circShare = if (circulatingSupply != null && totalSupply != null && totalSupply > 0) {
            (circulatingSupply / totalSupply) * 100.0
        } else null

        return CryptoFundamentals(
            symbol = symbol,
            marketCapUsd = marketCap,
            fdvUsd = fdv,
            circulatingSupply = circulatingSupply,
            totalSupply = totalSupply,
            volumeToMarketCapRatio = volToMc,
            circulatingSharePercent = circShare
        )
    }

    private fun formatLarge(value: Double): String {
        val sign = if (value < 0) "-" else ""
        val v = kotlin.math.abs(value)
        return sign + when {
            v >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", v / 1_000_000_000)
            v >= 1_000_000 -> String.format(Locale.US, "%.2fM", v / 1_000_000)
            v >= 1_000 -> String.format(Locale.US, "%.1fK", v / 1_000)
            else -> String.format(Locale.US, "%.2f", v)
        }
    }
}
