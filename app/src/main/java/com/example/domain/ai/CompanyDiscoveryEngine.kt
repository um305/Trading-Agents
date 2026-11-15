package com.example.domain.ai

import android.util.Log
import com.example.data.db.AiCompanyDao
import com.example.data.db.DiscoveredModelEntity
import com.example.domain.model.ApiStyle
import com.example.domain.model.AuthHeaderType
import com.example.domain.model.CompanyDefinition
import com.example.domain.model.CompanyDiagnosticsInfo
import com.example.domain.model.CompanyStatus
import com.example.domain.model.DiscoveredModel
import com.example.domain.model.ErrorKind
import com.example.domain.model.ModelTier
import com.example.domain.model.ModelVerificationState
import com.example.domain.model.SupportedCompanies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class CatalogFetchException(
    val code: Int,
    val errorKind: ErrorKind,
    val rawBody: String,
    val redactedUrl: String,
    override val message: String
) : Exception(message)

class CompanyDiscoveryEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
    private val aiCompanyDao: AiCompanyDao? = null
) {

    companion object {
        private const val TAG = "DiscoveryEngine"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        // Filtering patterns for non-text / non-chat models
        private val DROP_REGEX = Pattern.compile(
            "(embed|embedding|tts|whisper|audio|speech|dall-e|image|vision-preview|video|moderation|guard|safeguard|realtime|deprecated|babbage|davinci|search|similarity)",
            Pattern.CASE_INSENSITIVE
        )

        // Tier classification regex - FAST checked first or word boundaries to distinguish e.g. flash-lite vs flash
        private val FAST_TIER_REGEX = Pattern.compile(
            "\\b(lite|mini|nano|haiku|instant|small|[1-9]b|1[0-9]b)\\b|(-lite|-mini|-nano|-haiku|-instant|-small|-[1-9]b|-1[0-9]b)",
            Pattern.CASE_INSENSITIVE
        )

        private val TOP_TIER_REGEX = Pattern.compile(
            "\\b(pro|opus|reasoner|r1|thinking|large|405b|100b|[1-9][0-9]{2}b)\\b|(-pro|-opus|-reasoner|-r1|-thinking|-large|-405b)",
            Pattern.CASE_INSENSITIVE
        )

        fun classifyTier(modelId: String): ModelTier {
            val lower = modelId.lowercase()
            return when {
                FAST_TIER_REGEX.matcher(lower).find() -> ModelTier.FAST
                TOP_TIER_REGEX.matcher(lower).find() -> ModelTier.TOP
                else -> ModelTier.STANDARD
            }
        }

        fun isModelFree(companyId: String, modelId: String): Boolean {
            val lower = modelId.lowercase()
            return when (companyId.lowercase()) {
                "openrouter" -> lower.endsWith(":free") || lower.contains("/free")
                "gemini" -> lower.contains("flash")
                "groq" -> true // Groq free tier available
                "ollama" -> true // local
                else -> false
            }
        }

        fun extractVersionScore(modelName: String): Double {
            val matcher = Pattern.compile("(\\d+(\\.\\d+)?)").matcher(modelName)
            var score = 1.0
            while (matcher.find()) {
                val num = matcher.group(1)?.toDoubleOrNull() ?: 1.0
                if (num > score && num < 100.0) {
                    score = num
                }
            }
            if (modelName.contains("preview", ignoreCase = true) || modelName.contains("exp", ignoreCase = true)) {
                score -= 0.1
            }
            return score
        }

        fun redactUrl(url: String): String {
            return url.replace(Regex("([?&])(key|api_key|token)=[^&]+", RegexOption.IGNORE_CASE), "$1$2=REDACTED")
        }

        fun parseRetryDelaySeconds(body: String): Int? {
            // First check regex for retryDelay or retry-after (works both on Android and pure JVM unit tests)
            val retryDelayMatch = Pattern.compile("\"retryDelay\"\\s*:\\s*\"?(\\d+(?:\\.\\d+)?)s?\"?", Pattern.CASE_INSENSITIVE).matcher(body)
            if (retryDelayMatch.find()) {
                val sec = retryDelayMatch.group(1)?.toDoubleOrNull()?.toInt()
                if (sec != null && sec > 0) return sec
            }
            val retryAfterMatch = Pattern.compile("retry[\\s_-]?after[:\\s\"]+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(body)
            if (retryAfterMatch.find()) {
                val sec = retryAfterMatch.group(1)?.toIntOrNull()
                if (sec != null && sec > 0) return sec
            }

            try {
                val root = JSONObject(body)
                val error = root.optJSONObject("error")
                val details = error?.optJSONArray("details")
                if (details != null) {
                    for (i in 0 until details.length()) {
                        val d = details.optJSONObject(i) ?: continue
                        if (d.optString("@type").contains("RetryInfo")) {
                            val delayStr = d.optString("retryDelay")
                            val sec = delayStr.removeSuffix("s").toDoubleOrNull()?.toInt()
                            if (sec != null && sec > 0) return sec
                        }
                    }
                }
            } catch (_: Throwable) {}
            return null
        }

        fun classifyError(companyId: String, code: Int, body: String, isListCall: Boolean = false): ErrorKind? {
            if (code in 200..299) return null
            val lowerId = companyId.lowercase()

            when (lowerId) {
                "gemini" -> {
                    if (code == 400 && body.contains("API_KEY_INVALID", ignoreCase = true)) return ErrorKind.INVALID_KEY
                    if (code == 401 || code == 403) return ErrorKind.INVALID_KEY
                    if (code == 404) return if (isListCall) ErrorKind.ENDPOINT_NOT_FOUND else ErrorKind.MODEL_NOT_FOUND
                    if (code == 429) {
                        try {
                            val root = JSONObject(body)
                            val error = root.optJSONObject("error")
                            val details = error?.optJSONArray("details")
                            var isQuotaPerDay = false
                            var isQuotaPerMinute = false
                            var quotaIdStr = ""

                            if (details != null) {
                                for (i in 0 until details.length()) {
                                    val d = details.optJSONObject(i) ?: continue
                                    val qId = d.optString("quotaId")
                                    val qMetric = d.optString("quotaMetric")
                                    quotaIdStr += " $qId $qMetric"
                                    val violations = d.optJSONArray("violations")
                                    if (violations != null) {
                                        for (v in 0 until violations.length()) {
                                            quotaIdStr += " " + violations.optJSONObject(v)?.optString("quotaMetric")
                                        }
                                    }
                                }
                            }

                            if (quotaIdStr.contains("PerDay", ignoreCase = true) || quotaIdStr.contains("daily", ignoreCase = true)) {
                                isQuotaPerDay = true
                            }
                            if (quotaIdStr.contains("PerMinute", ignoreCase = true) || quotaIdStr.contains("minute", ignoreCase = true)) {
                                isQuotaPerMinute = true
                            }

                            val limitMatch = Pattern.compile("limit:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(body)
                            val limit = if (limitMatch.find()) limitMatch.group(1)?.toIntOrNull() else null

                            return when {
                                limit == 0 -> ErrorKind.MODEL_NOT_IN_TIER
                                isQuotaPerDay -> ErrorKind.DAILY_QUOTA_USED
                                isQuotaPerMinute || (limit != null && limit > 0) -> ErrorKind.RATE_LIMITED
                                else -> ErrorKind.RATE_LIMITED
                            }
                        } catch (_: Exception) {
                            val limitMatch = Pattern.compile("limit:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(body)
                            val limit = if (limitMatch.find()) limitMatch.group(1)?.toIntOrNull() else null
                            return if (limit == 0) ErrorKind.MODEL_NOT_IN_TIER else ErrorKind.RATE_LIMITED
                        }
                    }
                    if (code == 503 || code == 529) return ErrorKind.OVERLOADED
                    if (code >= 500) return ErrorKind.SERVER_ERROR
                }

                "openai" -> {
                    if (code == 401 || code == 403) return ErrorKind.INVALID_KEY
                    if (code == 404) return if (isListCall) ErrorKind.ENDPOINT_NOT_FOUND else ErrorKind.MODEL_NOT_FOUND
                    if (code == 429) {
                        try {
                            val root = JSONObject(body)
                            val error = root.optJSONObject("error")
                            val errCode = error?.optString("code") ?: ""
                            val errType = error?.optString("type") ?: ""
                            if (errCode == "insufficient_quota" || errType == "insufficient_quota") {
                                return ErrorKind.ACCOUNT_NEEDS_CREDIT
                            }
                            return ErrorKind.RATE_LIMITED
                        } catch (_: Exception) {
                            if (body.contains("insufficient_quota", ignoreCase = true)) return ErrorKind.ACCOUNT_NEEDS_CREDIT
                            return ErrorKind.RATE_LIMITED
                        }
                    }
                    if (code == 503 || code == 529) return ErrorKind.OVERLOADED
                    if (code >= 500) return ErrorKind.SERVER_ERROR
                }

                "anthropic" -> {
                    if (code == 401) return ErrorKind.INVALID_KEY
                    if (code == 403) return ErrorKind.NO_ACCESS
                    if (code == 400 && body.contains("credit balance is too low", ignoreCase = true)) {
                        return ErrorKind.ACCOUNT_NEEDS_CREDIT
                    }
                    if (code == 404) {
                        return if (isListCall) ErrorKind.ENDPOINT_NOT_FOUND else ErrorKind.MODEL_NOT_FOUND
                    }
                    if (code == 429) return ErrorKind.RATE_LIMITED
                    if (code == 529) return ErrorKind.OVERLOADED
                    if (code >= 500) return ErrorKind.SERVER_ERROR
                }

                "deepseek" -> {
                    if (code == 401 || code == 403) return ErrorKind.INVALID_KEY
                    if (code == 402 || (code == 400 && body.contains("Insufficient Balance", ignoreCase = true))) {
                        return ErrorKind.ACCOUNT_NEEDS_CREDIT
                    }
                    if (code == 404) return if (isListCall) ErrorKind.ENDPOINT_NOT_FOUND else ErrorKind.MODEL_NOT_FOUND
                    if (code == 429) return ErrorKind.RATE_LIMITED
                    if (code >= 500) return ErrorKind.SERVER_ERROR
                }

                "openrouter" -> {
                    if (code == 401 || code == 403) return ErrorKind.INVALID_KEY
                    if (code == 402) return ErrorKind.ACCOUNT_NEEDS_CREDIT
                    if (code == 404) return if (isListCall) ErrorKind.ENDPOINT_NOT_FOUND else ErrorKind.MODEL_NOT_FOUND
                    if (code == 429) return ErrorKind.RATE_LIMITED
                    if (code == 529 || code == 503) return ErrorKind.OVERLOADED
                    if (code >= 500) return ErrorKind.SERVER_ERROR
                }

                else -> {
                    if (code == 401 || code == 403) return ErrorKind.INVALID_KEY
                    if (code == 402) return ErrorKind.ACCOUNT_NEEDS_CREDIT
                    if (code == 404) return if (isListCall) ErrorKind.ENDPOINT_NOT_FOUND else ErrorKind.MODEL_NOT_FOUND
                    if (code == 429) return ErrorKind.RATE_LIMITED
                    if (code == 503 || code == 529) return ErrorKind.OVERLOADED
                    if (code >= 500) return ErrorKind.SERVER_ERROR
                }
            }

            return when {
                code == 401 || code == 403 -> ErrorKind.INVALID_KEY
                code == 402 -> ErrorKind.ACCOUNT_NEEDS_CREDIT
                code == 404 -> if (isListCall) ErrorKind.ENDPOINT_NOT_FOUND else ErrorKind.MODEL_NOT_FOUND
                code == 429 -> ErrorKind.RATE_LIMITED
                code >= 500 -> ErrorKind.SERVER_ERROR
                else -> ErrorKind.MALFORMED
            }
        }

        fun parseErrorSummary(body: String, code: Int): String {
            return try {
                val root = JSONObject(body)
                when {
                    root.has("error") -> {
                        val err = root.get("error")
                        if (err is JSONObject) {
                            err.optString("message", "HTTP $code")
                        } else err.toString()
                    }
                    root.has("message") -> root.optString("message")
                    else -> body.take(300)
                }
            } catch (_: Exception) {
                body.take(300).ifBlank { "HTTP $code" }
            }
        }
    }

    suspend fun discoverAndProbeCompany(
        company: CompanyDefinition,
        rawApiKey: String,
        customEndpoint: String? = null,
        customAuthHeader: String? = null
    ): DiscoveryResult = withContext(Dispatchers.IO) {
        val cleanKey = rawApiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()

        if (cleanKey.isBlank() && company.authHeaderType != AuthHeaderType.NONE) {
            return@withContext DiscoveryResult(
                status = CompanyStatus.NO_KEY,
                statusDetail = "API key not configured",
                models = emptyList()
            )
        }

        try {
            // 1. Fetch raw model catalog
            val rawCatalog = fetchRawCatalog(company, cleanKey, customEndpoint, customAuthHeader)

            // 2. Filter out non-chat / deprecated models
            val filtered = rawCatalog.filter { id ->
                !DROP_REGEX.matcher(id).find()
            }

            val candidateList = if (filtered.isEmpty()) {
                listOf(company.defaultModelName.ifBlank { "default-model" })
            } else {
                filtered
            }

            // 3. Classify into tiers and rank (newest first)
            val classified = candidateList.map { id ->
                val tier = classifyTier(id)
                val isFree = isModelFree(company.id, id)
                val version = extractVersionScore(id)
                Triple(id, tier, isFree to version)
            }.sortedWith(
                compareByDescending<Triple<String, ModelTier, Pair<Boolean, Double>>> { it.third.second }
                    .thenBy { it.first }
            )

            // Group by tier
            val groupedByTier = classified.groupBy { it.second }
            val topModels = groupedByTier[ModelTier.TOP] ?: emptyList()
            val standardModels = groupedByTier[ModelTier.STANDARD] ?: emptyList()
            val fastModels = groupedByTier[ModelTier.FAST] ?: emptyList()

            // 4. Select top candidates to probe: probe tiers best-first (TOP, STANDARD, FAST)
            // with at most one probe per tier and one at a time to conserve quota
            val orderedTiers = listOf(ModelTier.TOP, ModelTier.STANDARD, ModelTier.FAST)
            val candidateTiers = mutableListOf<Triple<String, ModelTier, Boolean>>()
            for (t in orderedTiers) {
                val candidate = (groupedByTier[t] ?: emptyList()).firstOrNull()
                if (candidate != null) {
                    candidateTiers.add(Triple(candidate.first, t, candidate.third.first))
                }
            }

            val finalModels = mutableListOf<DiscoveredModel>()
            var anySucceeded = false
            var allProbedFailedWithBilling = true
            var anyProbed = false
            var companyWideInvalidKey = false
            var quotaUsedResetHours: Int? = null
            var firstErrorKind: ErrorKind? = null
            var primaryDiagnostics: CompanyDiagnosticsInfo? = null

            // Map of probe results per modelId
            val probeResultsMap = mutableMapOf<String, ProbeResult>()

            // Probe best-first, one per tier
            for (candidate in candidateTiers) {
                val modelId = candidate.first
                anyProbed = true
                val probeResult = probeModel(company, cleanKey, modelId, customEndpoint, customAuthHeader)
                probeResultsMap[modelId] = probeResult

                if (firstErrorKind == null && probeResult.errorKind != null) {
                    firstErrorKind = probeResult.errorKind
                    primaryDiagnostics = CompanyDiagnosticsInfo(
                        companyId = company.id,
                        errorKind = probeResult.errorKind,
                        httpCode = if (probeResult.status == ProbeStatus.SUCCESS) 200 else null,
                        providerStatus = probeResult.status.name,
                        redactedUrl = probeResult.redactedUrl,
                        providerMessageExcerpt = probeResult.message.take(300),
                        routerNextAction = "Auto-downgrade to lower tier model"
                    )
                }

                if (probeResult.status == ProbeStatus.SUCCESS) {
                    anySucceeded = true
                    allProbedFailedWithBilling = false
                } else if (probeResult.status == ProbeStatus.INVALID_KEY) {
                    companyWideInvalidKey = true
                    allProbedFailedWithBilling = false
                    break // Stop probing if key itself is rejected
                } else if (probeResult.status == ProbeStatus.NEEDS_CREDIT || probeResult.status == ProbeStatus.MODEL_NOT_IN_TIER) {
                    // Counts towards billing failure
                } else {
                    allProbedFailedWithBilling = false
                }

                if (probeResult.resetHours != null) {
                    quotaUsedResetHours = maxOf(quotaUsedResetHours ?: 0, probeResult.resetHours)
                }
            }

            // Build DiscoveredModel list
            for (triple in classified) {
                val modelId = triple.first
                val tier = triple.second
                val isFree = triple.third.first
                val probe = probeResultsMap[modelId]

                if (probe != null) {
                    val vState = when (probe.status) {
                        ProbeStatus.SUCCESS -> ModelVerificationState.VERIFIED
                        ProbeStatus.NEEDS_CREDIT, ProbeStatus.MODEL_NOT_IN_TIER -> ModelVerificationState.NEEDS_BILLING
                        else -> ModelVerificationState.FAILED
                    }
                    val usable = probe.status == ProbeStatus.SUCCESS
                    finalModels.add(
                        DiscoveredModel(
                            id = "${company.id}:$modelId",
                            companyId = company.id,
                            modelName = modelId,
                            tier = tier,
                            isUsable = usable,
                            verificationState = vState,
                            isFree = isFree,
                            latencyMs = probe.latencyMs,
                            contextWindow = probe.contextWindow,
                            callsToday = 0,
                            lastTestedMs = System.currentTimeMillis(),
                            lastErrorKind = probe.errorKind,
                            errorReason = if (usable) null else probe.message
                        )
                    )
                } else {
                    // Unverified model: listed in catalog, will be lazily probed by router when needed
                    finalModels.add(
                        DiscoveredModel(
                            id = "${company.id}:$modelId",
                            companyId = company.id,
                            modelName = modelId,
                            tier = tier,
                            isUsable = anySucceeded || !companyWideInvalidKey,
                            verificationState = ModelVerificationState.UNVERIFIED,
                            isFree = isFree,
                            latencyMs = 0L,
                            contextWindow = 0,
                            callsToday = 0,
                            lastTestedMs = System.currentTimeMillis(),
                            lastErrorKind = null,
                            errorReason = null
                        )
                    )
                }
            }

            // Save discovered models to Room if DAO available
            aiCompanyDao?.let { dao ->
                try {
                    dao.deleteDiscoveredModelsForCompany(company.id)
                    dao.insertDiscoveredModels(
                        finalModels.map {
                            DiscoveredModelEntity(
                                id = it.id,
                                companyId = it.companyId,
                                modelName = it.modelName,
                                tier = it.tier.name,
                                isUsable = it.isUsable,
                                isFree = it.isFree,
                                latencyMs = it.latencyMs,
                                contextWindow = it.contextWindow,
                                callsToday = it.callsToday,
                                lastTestedMs = it.lastTestedMs,
                                errorReason = it.errorReason
                            )
                        }
                    )
                    dao.updateLastDiscoveryTime(company.id, System.currentTimeMillis())
                } catch (e: Exception) {
                    Log.w(TAG, "Error caching discovered models in Room: ${e.message}")
                }
            }

            // Status determination per Patch 2 rules:
            // 1. "Invalid key" only when the key itself was rejected (401/403)
            // 2. "Needs credit" only when every model probed across all tiers failed with ACCOUNT_NEEDS_CREDIT or MODEL_NOT_IN_TIER
            // 3. Companies where every model is paid (e.g. DeepSeek) with 0 balance show NEEDS_CREDIT_SKIPPED
            val isAllPaidCompany = company.needsCreditByDefault || company.id.lowercase() == "deepseek"
            val finalStatus = when {
                companyWideInvalidKey -> CompanyStatus.INVALID_KEY
                anyProbed && allProbedFailedWithBilling -> {
                    if (isAllPaidCompany) CompanyStatus.NEEDS_CREDIT_SKIPPED else CompanyStatus.NEEDS_CREDIT
                }
                anySucceeded -> CompanyStatus.CONNECTED
                finalModels.any { it.isUsable } -> CompanyStatus.CONNECTED
                quotaUsedResetHours != null -> CompanyStatus.QUOTA_USED
                else -> CompanyStatus.CONNECTED
            }

            val verifiedCount = finalModels.count { it.verificationState == ModelVerificationState.VERIFIED }
            val listedCount = finalModels.size
            val needsBillingCount = finalModels.count { it.verificationState == ModelVerificationState.NEEDS_BILLING }
            val unverifiedCount = finalModels.count { it.verificationState == ModelVerificationState.UNVERIFIED }

            val detail = when (finalStatus) {
                CompanyStatus.CONNECTED -> "Verified $verifiedCount / Listed $listedCount / Needs billing $needsBillingCount / Unverified $unverifiedCount"
                CompanyStatus.NEEDS_CREDIT -> "Needs credit (all probed tiers require balance)"
                CompanyStatus.NEEDS_CREDIT_SKIPPED -> "Needs credit - skipped by router"
                CompanyStatus.QUOTA_USED -> "Quota used - resets in ${quotaUsedResetHours ?: 1}h"
                CompanyStatus.INVALID_KEY -> "Invalid API key (401/403)"
                else -> null
            }

            DiscoveryResult(
                status = finalStatus,
                statusDetail = detail,
                errorKind = firstErrorKind,
                models = finalModels,
                diagnostics = primaryDiagnostics,
                quotaResetsInHours = quotaUsedResetHours
            )
        } catch (e: Exception) {
            Log.e(TAG, "Discovery error for ${company.displayName}: ${e.message}", e)
            val (status, errKind, redactedUrl) = if (e is CatalogFetchException) {
                val st = when (e.errorKind) {
                    ErrorKind.INVALID_KEY -> CompanyStatus.INVALID_KEY
                    ErrorKind.ENDPOINT_NOT_FOUND -> CompanyStatus.CONNECTED // list URL bug: show in diagnostics, never mark key invalid
                    ErrorKind.ACCOUNT_NEEDS_CREDIT -> CompanyStatus.NEEDS_CREDIT
                    ErrorKind.RATE_LIMITED, ErrorKind.DAILY_QUOTA_USED -> CompanyStatus.QUOTA_USED
                    else -> CompanyStatus.CONNECTED
                }
                Triple(st, e.errorKind, e.redactedUrl)
            } else {
                val msg = e.message ?: "Discovery failed"
                val kind = when {
                    msg.contains("401") || msg.contains("403") -> ErrorKind.INVALID_KEY
                    msg.contains("404") -> ErrorKind.ENDPOINT_NOT_FOUND
                    msg.contains("402") -> ErrorKind.ACCOUNT_NEEDS_CREDIT
                    msg.contains("429") -> ErrorKind.RATE_LIMITED
                    else -> ErrorKind.NETWORK
                }
                Triple(if (kind == ErrorKind.INVALID_KEY) CompanyStatus.INVALID_KEY else CompanyStatus.CONNECTED, kind, redactUrl(company.defaultBaseUrl))
            }

            val diagnostics = CompanyDiagnosticsInfo(
                companyId = company.id,
                errorKind = errKind,
                httpCode = (e as? CatalogFetchException)?.code ?: 404,
                providerStatus = status.name,
                redactedUrl = redactedUrl,
                providerMessageExcerpt = (e.message ?: "Discovery failed").take(300),
                routerNextAction = "Use cached models / fallback catalog"
            )

            // Fallback to cached Room models if available
            val cached = aiCompanyDao?.let { dao ->
                try {
                    dao.getDiscoveredModelsForCompany(company.id).first().map {
                        DiscoveredModel(
                            id = it.id,
                            companyId = it.companyId,
                            modelName = it.modelName,
                            tier = ModelTier.valueOf(it.tier),
                            isUsable = it.isUsable,
                            verificationState = if (it.isUsable) ModelVerificationState.VERIFIED else ModelVerificationState.FAILED,
                            isFree = it.isFree,
                            latencyMs = it.latencyMs,
                            contextWindow = it.contextWindow,
                            callsToday = it.callsToday,
                            lastTestedMs = it.lastTestedMs,
                            errorReason = it.errorReason
                        )
                    }
                } catch (_: Exception) { emptyList() }
            } ?: emptyList()

            DiscoveryResult(
                status = status,
                statusDetail = if (errKind == ErrorKind.ENDPOINT_NOT_FOUND) "Catalog endpoint not found ($redactedUrl)" else e.message,
                errorKind = errKind,
                models = cached,
                diagnostics = diagnostics
            )
        }
    }

    private suspend fun fetchRawCatalog(
        company: CompanyDefinition,
        apiKey: String,
        customEndpoint: String?,
        customAuthHeader: String?
    ): List<String> = withContext(Dispatchers.IO) {
        val base = customEndpoint?.takeIf { it.isNotBlank() } ?: company.defaultBaseUrl
        val cleanBase = base.trim().removeSuffix("/").removeSuffix("/chat/completions")

        when (company.id.lowercase()) {
            "gemini" -> {
                // GET /v1beta/models?pageSize=1000 with header x-goog-api-key
                val results = mutableListOf<String>()
                var pageToken: String? = null
                do {
                    val url = "$cleanBase/v1beta/models?pageSize=1000" + if (pageToken != null) "&pageToken=$pageToken" else ""
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", apiKey)
                        .get()
                        .build()

                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (!resp.isSuccessful) {
                            val errKind = classifyError(company.id, resp.code, body, isListCall = true)
                            throw CatalogFetchException(
                                code = resp.code,
                                errorKind = errKind ?: ErrorKind.SERVER_ERROR,
                                rawBody = body,
                                redactedUrl = redactUrl(url),
                                message = "HTTP ${resp.code}: ${parseErrorSummary(body, resp.code)}"
                            )
                        }
                        val root = JSONObject(body)
                        val modelsArr = root.optJSONArray("models") ?: JSONArray()
                        for (i in 0 until modelsArr.length()) {
                            val m = modelsArr.optJSONObject(i) ?: continue
                            val name = m.optString("name").removePrefix("models/")
                            val methods = m.optJSONArray("supportedGenerationMethods")
                            val canGenerate = methods?.let { arr ->
                                (0 until arr.length()).any { arr.optString(it) == "generateContent" }
                            } ?: true
                            if (canGenerate && name.isNotBlank()) results.add(name)
                        }
                        pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
                    }
                } while (pageToken != null)

                if (results.isEmpty()) listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro") else results
            }

            "anthropic" -> {
                // GET /v1/models (has_more / after_id)
                val results = mutableListOf<String>()
                var afterId: String? = null
                var hasMore = true
                while (hasMore) {
                    val endpointPath = "/v1/models"
                    val url = "$cleanBase$endpointPath?limit=100" + if (afterId != null) "&after_id=$afterId" else ""
                    val req = Request.Builder()
                        .url(url)
                        .addHeader("x-api-key", apiKey)
                        .addHeader("anthropic-version", "2023-06-01")
                        .get()
                        .build()

                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string() ?: ""
                        if (!resp.isSuccessful) {
                            val errKind = classifyError(company.id, resp.code, body, isListCall = true)
                            throw CatalogFetchException(
                                code = resp.code,
                                errorKind = errKind ?: ErrorKind.SERVER_ERROR,
                                rawBody = body,
                                redactedUrl = redactUrl(url),
                                message = "HTTP ${resp.code}: ${parseErrorSummary(body, resp.code)}"
                            )
                        }
                        val root = JSONObject(body)
                        val data = root.optJSONArray("data") ?: JSONArray()
                        for (i in 0 until data.length()) {
                            val obj = data.optJSONObject(i) ?: continue
                            val id = obj.optString("id")
                            if (id.isNotBlank()) results.add(id)
                            afterId = id
                        }
                        hasMore = root.optBoolean("has_more", false) && data.length() > 0
                    }
                }
                if (results.isEmpty()) listOf("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest") else results
            }

            "cohere" -> {
                val url = "$cleanBase/v1/models?endpoint=chat"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        throw Exception("HTTP ${resp.code}: ${parseErrorSummary(body, resp.code)}")
                    }
                    val root = JSONObject(body)
                    val modelsArr = root.optJSONArray("models") ?: JSONArray()
                    val list = mutableListOf<String>()
                    for (i in 0 until modelsArr.length()) {
                        val m = modelsArr.optJSONObject(i) ?: continue
                        val name = m.optString("name")
                        if (name.isNotBlank()) list.add(name)
                    }
                    if (list.isEmpty()) listOf("command-r-plus", "command-r") else list
                }
            }

            "ollama" -> {
                val url = "$cleanBase/api/tags"
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        throw Exception("HTTP ${resp.code}: ${parseErrorSummary(body, resp.code)}")
                    }
                    val root = JSONObject(body)
                    val modelsArr = root.optJSONArray("models") ?: JSONArray()
                    val list = mutableListOf<String>()
                    for (i in 0 until modelsArr.length()) {
                        val m = modelsArr.optJSONObject(i) ?: continue
                        val name = m.optString("name")
                        if (name.isNotBlank()) list.add(name)
                    }
                    if (list.isEmpty()) listOf("llama3.2") else list
                }
            }

            "perplexity" -> {
                // Perplexity has no list endpoint -> use curated list
                listOf("sonar-pro", "sonar", "sonar-reasoning-pro", "sonar-reasoning")
            }

            else -> {
                // Standard OpenAI-compatible GET /models
                val endpoint = company.listEndpoint ?: "/models"
                val url = if (endpoint.startsWith("http")) endpoint else "$cleanBase$endpoint"
                val reqBuilder = Request.Builder().url(url).get()

                when (company.authHeaderType) {
                    AuthHeaderType.BEARER -> reqBuilder.addHeader("Authorization", if (apiKey.startsWith("Bearer ", ignoreCase = true)) apiKey else "Bearer $apiKey")
                    AuthHeaderType.X_API_KEY -> reqBuilder.addHeader("x-api-key", apiKey)
                    AuthHeaderType.CUSTOM -> {
                        val headerName = customAuthHeader ?: "Authorization"
                        reqBuilder.addHeader(headerName, apiKey)
                    }
                    else -> {}
                }

                client.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        val errKind = classifyError(company.id, resp.code, body, isListCall = true)
                        throw CatalogFetchException(
                            code = resp.code,
                            errorKind = errKind ?: ErrorKind.SERVER_ERROR,
                            rawBody = body,
                            redactedUrl = redactUrl(url),
                            message = "HTTP ${resp.code}: ${parseErrorSummary(body, resp.code)}"
                        )
                    }
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    val list = mutableListOf<String>()
                    for (i in 0 until data.length()) {
                        val obj = data.optJSONObject(i) ?: continue
                        val id = obj.optString("id")
                        if (id.isNotBlank()) list.add(id)
                    }
                    if (list.isEmpty()) listOf(company.defaultModelName) else list
                }
            }
        }
    }

    private suspend fun probeModel(
        company: CompanyDefinition,
        apiKey: String,
        modelId: String,
        customEndpoint: String?,
        customAuthHeader: String?
    ): ProbeResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val base = customEndpoint?.takeIf { it.isNotBlank() } ?: company.defaultBaseUrl
        val cleanBase = base.trim().removeSuffix("/").removeSuffix("/chat/completions")

        try {
            when (company.apiStyle) {
                ApiStyle.GEMINI_REST -> {
                    val url = "$cleanBase/v1beta/models/$modelId:generateContent"
                    val payload = JSONObject().apply {
                        put("contents", JSONArray().apply {
                            put(JSONObject().apply {
                                put("parts", JSONArray().apply {
                                    put(JSONObject().apply { put("text", "Ping") })
                                })
                            })
                        })
                        put("generationConfig", JSONObject().apply {
                            put("maxOutputTokens", 5)
                        })
                    }

                    val req = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", apiKey)
                        .post(payload.toString().toRequestBody(JSON_MEDIA))
                        .build()

                    client.newCall(req).execute().use { resp ->
                        val latency = System.currentTimeMillis() - start
                        val body = resp.body?.string() ?: ""
                        classifyResponse(company, modelId, resp.code, body, latency, cleanBase)
                    }
                }

                ApiStyle.ANTHROPIC_MESSAGES -> {
                    val url = "$cleanBase/v1/messages"
                    val payload = JSONObject().apply {
                        put("model", modelId)
                        put("max_tokens", 5)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", "Ping")
                            })
                        })
                    }

                    val req = Request.Builder()
                        .url(url)
                        .addHeader("x-api-key", apiKey)
                        .addHeader("anthropic-version", "2023-06-01")
                        .post(payload.toString().toRequestBody(JSON_MEDIA))
                        .build()

                    client.newCall(req).execute().use { resp ->
                        val latency = System.currentTimeMillis() - start
                        val body = resp.body?.string() ?: ""
                        classifyResponse(company, modelId, resp.code, body, latency, cleanBase)
                    }
                }

                ApiStyle.COHERE -> {
                    val url = "$cleanBase/chat"
                    val payload = JSONObject().apply {
                        put("model", modelId)
                        put("message", "Ping")
                        put("max_tokens", 5)
                    }

                    val req = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(payload.toString().toRequestBody(JSON_MEDIA))
                        .build()

                    client.newCall(req).execute().use { resp ->
                        val latency = System.currentTimeMillis() - start
                        val body = resp.body?.string() ?: ""
                        classifyResponse(company, modelId, resp.code, body, latency, cleanBase)
                    }
                }

                ApiStyle.OPENAI_COMPATIBLE -> {
                    val url = if (cleanBase.endsWith("/v1")) "$cleanBase/chat/completions" else "$cleanBase/v1/chat/completions"
                    val payload = JSONObject().apply {
                        put("model", modelId)
                        put("messages", JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", "Ping")
                            })
                        })
                        put("max_tokens", 5)
                    }

                    val reqBuilder = Request.Builder()
                        .url(url)
                        .post(payload.toString().toRequestBody(JSON_MEDIA))

                    if (company.authHeaderType == AuthHeaderType.BEARER) {
                        reqBuilder.addHeader("Authorization", if (apiKey.startsWith("Bearer ", ignoreCase = true)) apiKey else "Bearer $apiKey")
                    } else if (company.authHeaderType == AuthHeaderType.CUSTOM) {
                        val headerName = customAuthHeader ?: "Authorization"
                        reqBuilder.addHeader(headerName, apiKey)
                    }

                    client.newCall(reqBuilder.build()).execute().use { resp ->
                        val latency = System.currentTimeMillis() - start
                        val body = resp.body?.string() ?: ""
                        classifyResponse(company, modelId, resp.code, body, latency, cleanBase)
                    }
                }
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            ProbeResult(ProbeStatus.FAILED, ErrorKind.NETWORK, latency, 0, e.message ?: "Probe call failed", redactedUrl = redactUrl(cleanBase))
        }
    }

    private fun classifyResponse(
        company: CompanyDefinition,
        modelId: String,
        code: Int,
        body: String,
        latency: Long,
        cleanBase: String
    ): ProbeResult {
        val errorKind = classifyError(company.id, code, body, isListCall = false)
        val parsedMsg = parseErrorSummary(body, code)

        return when (errorKind) {
            null -> {
                // HTTP 200 OK
                ProbeResult(
                    status = ProbeStatus.SUCCESS,
                    errorKind = null,
                    latencyMs = latency,
                    contextWindow = 128_000,
                    message = "OK"
                )
            }
            ErrorKind.INVALID_KEY -> {
                ProbeResult(
                    status = ProbeStatus.INVALID_KEY,
                    errorKind = ErrorKind.INVALID_KEY,
                    latencyMs = latency,
                    message = parsedMsg,
                    redactedUrl = redactUrl(cleanBase)
                )
            }
            ErrorKind.ACCOUNT_NEEDS_CREDIT -> {
                ProbeResult(
                    status = ProbeStatus.NEEDS_CREDIT,
                    errorKind = ErrorKind.ACCOUNT_NEEDS_CREDIT,
                    latencyMs = latency,
                    message = parsedMsg,
                    redactedUrl = redactUrl(cleanBase)
                )
            }
            ErrorKind.MODEL_NOT_IN_TIER -> {
                ProbeResult(
                    status = ProbeStatus.MODEL_NOT_IN_TIER,
                    errorKind = ErrorKind.MODEL_NOT_IN_TIER,
                    latencyMs = latency,
                    message = "Model limit: 0 for current tier ($parsedMsg)",
                    redactedUrl = redactUrl(cleanBase)
                )
            }
            ErrorKind.RATE_LIMITED -> {
                val delaySec = parseRetryDelaySeconds(body) ?: 60
                ProbeResult(
                    status = ProbeStatus.QUOTA_USED,
                    errorKind = ErrorKind.RATE_LIMITED,
                    latencyMs = latency,
                    message = "Rate limit reached (retry in ${delaySec}s)",
                    resetHours = 1,
                    retryAfterSeconds = delaySec,
                    redactedUrl = redactUrl(cleanBase)
                )
            }
            ErrorKind.DAILY_QUOTA_USED -> {
                ProbeResult(
                    status = ProbeStatus.QUOTA_USED,
                    errorKind = ErrorKind.DAILY_QUOTA_USED,
                    latencyMs = latency,
                    message = "Daily quota reached ($parsedMsg)",
                    resetHours = 24,
                    retryAfterSeconds = 86400,
                    redactedUrl = redactUrl(cleanBase)
                )
            }
            ErrorKind.MODEL_NOT_FOUND -> {
                ProbeResult(
                    status = ProbeStatus.FAILED,
                    errorKind = ErrorKind.MODEL_NOT_FOUND,
                    latencyMs = latency,
                    message = "Model not found / decommissioned (HTTP 404)",
                    redactedUrl = redactUrl(cleanBase)
                )
            }
            ErrorKind.ENDPOINT_NOT_FOUND -> {
                ProbeResult(
                    status = ProbeStatus.FAILED,
                    errorKind = ErrorKind.ENDPOINT_NOT_FOUND,
                    latencyMs = latency,
                    message = "Catalog endpoint not found (HTTP 404 at ${redactUrl(cleanBase)})",
                    redactedUrl = redactUrl(cleanBase)
                )
            }
            ErrorKind.OVERLOADED -> {
                ProbeResult(
                    status = ProbeStatus.FAILED,
                    errorKind = ErrorKind.OVERLOADED,
                    latencyMs = latency,
                    message = "Server capacity overloaded (HTTP $code)",
                    redactedUrl = redactUrl(cleanBase)
                )
            }
            ErrorKind.SERVER_ERROR -> {
                ProbeResult(
                    status = ProbeStatus.FAILED,
                    errorKind = ErrorKind.SERVER_ERROR,
                    latencyMs = latency,
                    message = "Server error (HTTP $code: $parsedMsg)",
                    redactedUrl = redactUrl(cleanBase)
                )
            }
            else -> {
                ProbeResult(
                    status = ProbeStatus.FAILED,
                    errorKind = errorKind,
                    latencyMs = latency,
                    message = "HTTP $code: $parsedMsg",
                    redactedUrl = redactUrl(cleanBase)
                )
            }
        }
    }
}

enum class ProbeStatus {
    SUCCESS,
    NEEDS_CREDIT,
    MODEL_NOT_IN_TIER,
    INVALID_KEY,
    QUOTA_USED,
    FAILED
}

data class ProbeResult(
    val status: ProbeStatus,
    val errorKind: ErrorKind? = null,
    val latencyMs: Long = 0L,
    val contextWindow: Int = 128_000,
    val message: String = "",
    val resetHours: Int? = null,
    val retryAfterSeconds: Int? = null,
    val redactedUrl: String = ""
)

data class DiscoveryResult(
    val status: CompanyStatus,
    val statusDetail: String? = null,
    val models: List<DiscoveredModel> = emptyList(),
    val errorKind: ErrorKind? = null,
    val diagnostics: CompanyDiagnosticsInfo? = null,
    val quotaResetsInHours: Int? = null
)
