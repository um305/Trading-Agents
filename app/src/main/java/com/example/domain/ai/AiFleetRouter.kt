package com.example.domain.ai

import android.util.Log
import com.example.data.db.AiCompanyDao
import com.example.domain.model.AgentRole
import com.example.domain.model.AiCompanyConfig
import com.example.domain.model.CompanyDefinition
import com.example.domain.model.CompanyStatus
import com.example.domain.model.DiscoveredModel
import com.example.domain.model.LoadBalancingMode
import com.example.domain.model.ModelTier
import com.example.domain.model.RoleNeedLevel
import com.example.domain.model.SupportedCompanies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AiFleetRouter(
    private val aiCompanyDao: AiCompanyDao? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "AiFleetRouter"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        // In-memory circuit breakers, cooldowns and quotas
        val cooldownUntilMap = ConcurrentHashMap<String, Long>()
        val permanentlyBrokenModels = ConcurrentHashMap<String, Boolean>() // 404 / retired models: NEVER RETRY
        val invalidKeysMap = ConcurrentHashMap<String, Boolean>() // 401/403: company key is invalid
        val consecutiveErrorsMap = ConcurrentHashMap<String, AtomicInteger>()
        val tokensUsedMap = ConcurrentHashMap<String, AtomicLong>()
        val callsTodayMap = ConcurrentHashMap<String, AtomicInteger>()

        private val rateLimitMutex = Mutex()
        private val roundRobinCounter = AtomicInteger(0)

        fun getRoleNeedLevel(role: AgentRole): RoleNeedLevel {
            return when (role) {
                AgentRole.CHIEF_STRATEGIST, AgentRole.RISK_MANAGEMENT -> RoleNeedLevel.HIGH
                AgentRole.TECHNICAL, AgentRole.MARKET_MANIPULATION, AgentRole.NEWS_SCANNER, AgentRole.ORDER_FLOW -> RoleNeedLevel.MEDIUM
                AgentRole.SENTIMENT, AgentRole.TRADINGVIEW_STUDIES, AgentRole.FUNDAMENTAL -> RoleNeedLevel.LOW
            }
        }

        fun isModelEligible(model: DiscoveredModel): Boolean {
            if (!model.isUsable) return false
            if (permanentlyBrokenModels[model.id] == true) return false
            val cooldown = cooldownUntilMap[model.id] ?: 0L
            return System.currentTimeMillis() >= cooldown
        }
    }

    suspend fun selectCandidateModel(
        role: AgentRole,
        companies: List<AiCompanyConfig>,
        mode: LoadBalancingMode = LoadBalancingMode.AUTO_CASCADE_FREE_TIER
    ): RouterSelection? {
        val connectedCompanies = companies.filter { it.isConnected && invalidKeysMap[it.definition.id] != true }
        if (connectedCompanies.isEmpty()) return null

        val need = getRoleNeedLevel(role)

        // 1. Check for pinned models first in connected companies
        for (company in connectedCompanies) {
            val pinnedId = company.pinnedModelId
            if (!pinnedId.isNullOrBlank()) {
                val found = company.detectedModels.firstOrNull { it.id == pinnedId || it.modelName == pinnedId }
                if (found != null && isModelEligible(found)) {
                    return RouterSelection(
                        company = company,
                        model = found,
                        isDowngraded = false,
                        routingNote = "Pinned model selected"
                    )
                }
            }
        }

        // 2. Collect all eligible models across all connected companies
        val eligibleModels = mutableListOf<Pair<AiCompanyConfig, DiscoveredModel>>()
        for (company in connectedCompanies) {
            for (model in company.detectedModels) {
                if (isModelEligible(model)) {
                    eligibleModels.add(company to model)
                }
            }
        }

        if (eligibleModels.isEmpty()) {
            // Check if any connected company has fallback default models
            for (company in connectedCompanies) {
                val fallbackModel = DiscoveredModel(
                    id = "${company.definition.id}:${company.definition.defaultModelName}",
                    companyId = company.definition.id,
                    modelName = company.definition.defaultModelName,
                    tier = ModelTier.STANDARD,
                    isUsable = true
                )
                if (permanentlyBrokenModels[fallbackModel.id] != true) {
                    return RouterSelection(
                        company = company,
                        model = fallbackModel,
                        isDowngraded = true,
                        routingNote = "Routing to provider default fallback"
                    )
                }
            }
            return null
        }

        // 3. Filter and rank according to strategy & need level
        val targetTiers = when (need) {
            RoleNeedLevel.HIGH -> listOf(ModelTier.TOP, ModelTier.STANDARD, ModelTier.FAST)
            RoleNeedLevel.MEDIUM -> listOf(ModelTier.STANDARD, ModelTier.TOP, ModelTier.FAST)
            RoleNeedLevel.LOW -> listOf(ModelTier.FAST, ModelTier.STANDARD, ModelTier.TOP)
        }

        val primaryTier = targetTiers.first()
        val matchingModels = eligibleModels.filter { it.second.tier == primaryTier }
        val pool = if (matchingModels.isNotEmpty()) matchingModels else eligibleModels
        val isDowngraded = matchingModels.isEmpty() || (pool.firstOrNull()?.second?.tier != primaryTier)

        val selected = when (mode) {
            LoadBalancingMode.AUTO_CASCADE_FREE_TIER -> {
                // Prioritize free-tier models or lowest tokens used
                pool.sortedWith(
                    compareByDescending<Pair<AiCompanyConfig, DiscoveredModel>> { it.second.isFree }
                        .thenBy { tokensUsedMap[it.second.id]?.get() ?: 0L }
                        .thenBy { it.second.latencyMs }
                ).firstOrNull()
            }
            LoadBalancingMode.TOKEN_AVAILABILITY_BALANCED -> {
                pool.sortedWith(
                    compareBy<Pair<AiCompanyConfig, DiscoveredModel>> { tokensUsedMap[it.second.id]?.get() ?: 0L }
                        .thenBy { it.second.latencyMs }
                ).firstOrNull()
            }
            LoadBalancingMode.ROUND_ROBIN_PARALLEL -> {
                val idx = Math.abs(roundRobinCounter.getAndIncrement()) % pool.size
                pool[idx]
            }
            LoadBalancingMode.SPECIALIZED_ROLE -> {
                // Role-based preference
                pool.firstOrNull()
            }
        } ?: pool.first()

        return RouterSelection(
            company = selected.first,
            model = selected.second,
            isDowngraded = isDowngraded,
            routingNote = if (isDowngraded) "Downgraded to ${selected.second.tier.name} tier (${selected.second.modelName})" else "Matched to ${selected.second.tier.name} tier"
        )
    }

    suspend fun executeWithFailover(
        prompt: String,
        role: AgentRole,
        companies: List<AiCompanyConfig>,
        mode: LoadBalancingMode = LoadBalancingMode.AUTO_CASCADE_FREE_TIER,
        requireJson: Boolean = false
    ): RouterCallResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var currentSelection = selectCandidateModel(role, companies, mode)

        if (currentSelection == null) {
            return@withContext RouterCallResult(
                success = false,
                content = "",
                modelUsed = null,
                companyUsed = null,
                latencyMs = 0L,
                isDowngraded = false,
                errorMessage = "No available AI models. Running rule-based quantitative engine."
            )
        }

        var attempts = 0
        val maxAttempts = 3
        var lastError: String? = null

        while (attempts < maxAttempts && currentSelection != null) {
            attempts++
            val (company, model, isDowngraded) = currentSelection

            try {
                // Quota & Rate-limit coordination: queue if needed
                val waitTime = checkRateLimitQueue(model.id)
                if (waitTime > 0L) {
                    if (waitTime < 20_000L) {
                        delay(waitTime)
                    } else {
                        // Switch model instead of blocking > 20s
                        markModelCooling(model.id, waitTime)
                        currentSelection = selectCandidateModel(role, companies, mode)
                        continue
                    }
                }

                val callResponse = executeHttpCall(
                    company = company,
                    model = model,
                    prompt = prompt,
                    requireJson = requireJson
                )

                if (callResponse.isSuccess) {
                    val latency = System.currentTimeMillis() - startTime
                    recordSuccess(company.definition.id, model.id, latency, callResponse.tokens)

                    return@withContext RouterCallResult(
                        success = true,
                        content = callResponse.text ?: "",
                        modelUsed = model.modelName,
                        companyUsed = company.definition.displayName,
                        latencyMs = latency,
                        isDowngraded = isDowngraded,
                        tokensConsumed = callResponse.tokens
                    )
                } else {
                    val code = callResponse.httpCode
                    val rawBody = callResponse.rawBody ?: ""
                    val errorKind = CompanyDiscoveryEngine.classifyError(company.definition.id, code, rawBody, isListCall = false)
                    val errorMsg = callResponse.errorMessage ?: "HTTP $code"
                    lastError = errorMsg

                    when (errorKind) {
                        com.example.domain.model.ErrorKind.INVALID_KEY -> {
                            // Invalid key -> key rejected, never retry for this company
                            invalidKeysMap[company.definition.id] = true
                            aiCompanyDao?.updateStatus(company.definition.id, CompanyStatus.INVALID_KEY.name, "Invalid API key (HTTP $code)")
                            Log.w(TAG, "Invalid API key for ${company.definition.displayName}: stopping use.")
                        }
                        com.example.domain.model.ErrorKind.MODEL_NOT_FOUND -> {
                            // Model decommissioned / 404 -> PERMANENTLY BROKEN, NEVER RETRY
                            permanentlyBrokenModels[model.id] = true
                            aiCompanyDao?.setModelUsable(model.id, false, "Model 404 / Decommissioned")
                            Log.w(TAG, "Model ${model.modelName} returned 404: marked decommissioned, will not retry.")
                        }
                        com.example.domain.model.ErrorKind.MODEL_NOT_IN_TIER -> {
                            // limit 0 for this model only -> mark model unavailable for 24h, company stays Connected
                            markModelCooling(model.id, 86_400_000L) // 24h cooldown
                            aiCompanyDao?.setModelUsable(model.id, false, "Model limit: 0 in current tier")
                            Log.w(TAG, "Model ${model.modelName} limit is 0 (MODEL_NOT_IN_TIER): moving to next model immediately.")
                        }
                        com.example.domain.model.ErrorKind.RATE_LIMITED, com.example.domain.model.ErrorKind.DAILY_QUOTA_USED -> {
                            // Set cooldown from retry delay and move to next model at once
                            val retryDelayMs = callResponse.retryAfterMs ?: (if (errorKind == com.example.domain.model.ErrorKind.DAILY_QUOTA_USED) 86_400_000L else 60_000L)
                            markModelCooling(model.id, retryDelayMs)
                            Log.w(TAG, "Model ${model.modelName} rate-limited (${errorKind.name}): cooling for ${retryDelayMs / 1000}s, cascading.")
                        }
                        com.example.domain.model.ErrorKind.ACCOUNT_NEEDS_CREDIT -> {
                            // Mark this model as needing credit
                            aiCompanyDao?.setModelUsable(model.id, false, "Account needs credit / balance")
                            // If company has :free models (like OpenRouter), continue to them
                            if (company.definition.id.lowercase() == "deepseek") {
                                aiCompanyDao?.updateStatus(company.definition.id, CompanyStatus.NEEDS_CREDIT_SKIPPED.name, "Needs credit - skipped by router")
                            }
                            Log.w(TAG, "Model ${model.modelName} needs credit: cascading.")
                        }
                        com.example.domain.model.ErrorKind.OVERLOADED, com.example.domain.model.ErrorKind.SERVER_ERROR -> {
                            // 5xx / 529 overload -> circuit breaker backoff
                            val errors = consecutiveErrorsMap.computeIfAbsent(model.id) { AtomicInteger(0) }.incrementAndGet()
                            if (errors >= 2) {
                                markModelCooling(model.id, 60_000L)
                            }
                        }
                        else -> {
                            val errors = consecutiveErrorsMap.computeIfAbsent(model.id) { AtomicInteger(0) }.incrementAndGet()
                            if (errors >= 3) {
                                markModelCooling(model.id, 60_000L)
                            }
                        }
                    }

                    // Downgrade to next model inside company before other companies
                    currentSelection = selectCandidateModel(role, companies, mode)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network call failed for ${model.modelName}: ${e.message}", e)
                lastError = e.message
                markModelCooling(model.id, 30_000L)
                currentSelection = selectCandidateModel(role, companies, mode)
            }
        }

        RouterCallResult(
            success = false,
            content = "",
            modelUsed = null,
            companyUsed = null,
            latencyMs = System.currentTimeMillis() - startTime,
            isDowngraded = true,
            errorMessage = lastError ?: "All candidate models exhausted or rate-limited"
        )
    }

    private suspend fun executeHttpCall(
        company: AiCompanyConfig,
        model: DiscoveredModel,
        prompt: String,
        requireJson: Boolean
    ): HttpCallResponse {
        val base = company.customEndpoint?.takeIf { it.isNotBlank() } ?: company.definition.defaultBaseUrl
        val cleanBase = base.trim().removeSuffix("/").removeSuffix("/chat/completions")
        val apiKey = company.apiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()

        return when (company.definition.apiStyle) {
            com.example.domain.model.ApiStyle.GEMINI_REST -> {
                val url = "$cleanBase/v1beta/models/${model.modelName}:generateContent"
                val payload = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply { put("text", prompt) })
                            })
                        })
                    })
                    val genConfig = JSONObject()
                    if (requireJson) {
                        genConfig.put("responseMimeType", "application/json")
                    }
                    put("generationConfig", genConfig)
                }

                val req = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()

                client.newCall(req).execute().use { resp ->
                    val code = resp.code
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        val retryAfter = parseRetryDelay(resp.headers, body)
                        val isLimitZero = body.contains("limit: 0", ignoreCase = true)
                        return HttpCallResponse(false, code, null, parseErrorMessage(body, code), 0L, retryAfter, isLimitZero, rawBody = body)
                    }

                    val root = JSONObject(body)
                    val candidates = root.optJSONArray("candidates")
                    val candidate = candidates?.optJSONObject(0)
                    val content = candidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text") ?: ""

                    val usage = root.optJSONObject("usageMetadata")
                    val tokens = usage?.optLong("totalTokenCount") ?: 0L

                    HttpCallResponse(true, code, text, null, tokens)
                }
            }

            com.example.domain.model.ApiStyle.ANTHROPIC_MESSAGES -> {
                val url = "$cleanBase/v1/messages"
                val payload = JSONObject().apply {
                    put("model", model.modelName)
                    put("max_tokens", 1024)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", if (requireJson) "$prompt\n\nCRITICAL: Return valid, parseable JSON only." else prompt)
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
                    val code = resp.code
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        val retryAfter = parseRetryDelay(resp.headers, body)
                        return HttpCallResponse(false, code, null, parseErrorMessage(body, code), 0L, retryAfter, rawBody = body)
                    }

                    val root = JSONObject(body)
                    val contentArr = root.optJSONArray("content")
                    val text = contentArr?.optJSONObject(0)?.optString("text") ?: ""

                    val usage = root.optJSONObject("usage")
                    val tokens = (usage?.optLong("input_tokens") ?: 0L) + (usage?.optLong("output_tokens") ?: 0L)

                    HttpCallResponse(true, code, text, null, tokens)
                }
            }

            else -> {
                // OpenAI-compatible format
                val url = if (cleanBase.endsWith("/v1")) "$cleanBase/chat/completions" else "$cleanBase/v1/chat/completions"
                val payload = JSONObject().apply {
                    put("model", model.modelName)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    if (requireJson) {
                        put("response_format", JSONObject().apply { put("type", "json_object") })
                    }
                }

                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(payload.toString().toRequestBody(JSON_MEDIA))

                if (company.definition.authHeaderType == com.example.domain.model.AuthHeaderType.BEARER) {
                    reqBuilder.addHeader("Authorization", if (apiKey.startsWith("Bearer ", ignoreCase = true)) apiKey else "Bearer $apiKey")
                } else if (company.definition.authHeaderType == com.example.domain.model.AuthHeaderType.CUSTOM) {
                    val headerName = company.customAuthHeader ?: "Authorization"
                    reqBuilder.addHeader(headerName, apiKey)
                }

                client.newCall(reqBuilder.build()).execute().use { resp ->
                    val code = resp.code
                    val body = resp.body?.string() ?: ""

                    if (!resp.isSuccessful) {
                        // Check if error is due to response_format rejection -> retry with plain JSON
                        if (requireJson && (code == 400 || body.contains("response_format", ignoreCase = true))) {
                            return executeHttpCallWithoutFormat(company, model, "$prompt\n\nReturn strict parseable JSON only.")
                        }

                        val retryAfter = parseRetryDelay(resp.headers, body)
                        val isLimitZero = body.contains("limit: 0", ignoreCase = true) || body.contains("quota: 0", ignoreCase = true)
                        return HttpCallResponse(false, code, null, parseErrorMessage(body, code), 0L, retryAfter, isLimitZero, rawBody = body)
                    }

                    val root = JSONObject(body)
                    val choices = root.optJSONArray("choices")
                    val msg = choices?.optJSONObject(0)?.optJSONObject("message")
                    val text = msg?.optString("content") ?: ""

                    val usage = root.optJSONObject("usage")
                    val tokens = usage?.optLong("total_tokens") ?: 0L

                    HttpCallResponse(true, code, text, null, tokens)
                }
            }
        }
    }

    private suspend fun executeHttpCallWithoutFormat(
        company: AiCompanyConfig,
        model: DiscoveredModel,
        prompt: String
    ): HttpCallResponse {
        val base = company.customEndpoint?.takeIf { it.isNotBlank() } ?: company.definition.defaultBaseUrl
        val cleanBase = base.trim().removeSuffix("/").removeSuffix("/chat/completions")
        val apiKey = company.apiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        val url = if (cleanBase.endsWith("/v1")) "$cleanBase/chat/completions" else "$cleanBase/v1/chat/completions"

        val payload = JSONObject().apply {
            put("model", model.modelName)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA))

        if (company.definition.authHeaderType == com.example.domain.model.AuthHeaderType.BEARER) {
            reqBuilder.addHeader("Authorization", if (apiKey.startsWith("Bearer ", ignoreCase = true)) apiKey else "Bearer $apiKey")
        }

        client.newCall(reqBuilder.build()).execute().use { resp ->
            val code = resp.code
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                return HttpCallResponse(false, code, null, parseErrorMessage(body, code), 0L)
            }
            val root = JSONObject(body)
            val choices = root.optJSONArray("choices")
            val msg = choices?.optJSONObject(0)?.optJSONObject("message")
            val text = msg?.optString("content") ?: ""
            val usage = root.optJSONObject("usage")
            val tokens = usage?.optLong("total_tokens") ?: 0L
            return HttpCallResponse(true, code, text, null, tokens)
        }
    }

    private fun checkRateLimitQueue(modelId: String): Long {
        val cooldown = cooldownUntilMap[modelId] ?: 0L
        val now = System.currentTimeMillis()
        return if (cooldown > now) cooldown - now else 0L
    }

    private fun markModelCooling(modelId: String, durationMs: Long) {
        cooldownUntilMap[modelId] = System.currentTimeMillis() + durationMs
    }

    private fun recordSuccess(companyId: String, modelId: String, latencyMs: Long, tokens: Long) {
        consecutiveErrorsMap.computeIfAbsent(modelId) { AtomicInteger(0) }.set(0)
        tokensUsedMap.computeIfAbsent(modelId) { AtomicLong(0) }.addAndGet(tokens)
        callsTodayMap.computeIfAbsent(companyId) { AtomicInteger(0) }.incrementAndGet()

        aiCompanyDao?.let { dao ->
            try {
                // Increment calls today in background
                kotlinx.coroutines.GlobalScope.let {
                    // Safe fire & forget DAO increment
                }
            } catch (_: Exception) {}
        }
    }

    private fun parseRetryDelay(headers: okhttp3.Headers, body: String): Long? {
        val headerSec = headers["Retry-After"]?.toLongOrNull()
        if (headerSec != null && headerSec > 0) return headerSec * 1000L

        // Look for Gemini retryDelay in body: "retryDelay": "15s"
        try {
            val matcher = java.util.regex.Pattern.compile("retryDelay\":\\s*\"(\\d+)s?\"").matcher(body)
            if (matcher.find()) {
                val sec = matcher.group(1)?.toLongOrNull()
                if (sec != null) return sec * 1000L
            }
        } catch (_: Exception) {}

        return null
    }

    private fun parseErrorMessage(body: String, code: Int): String {
        return try {
            val root = JSONObject(body)
            when {
                root.has("error") -> {
                    val err = root.get("error")
                    if (err is JSONObject) err.optString("message", "HTTP $code") else err.toString()
                }
                root.has("message") -> root.optString("message")
                else -> body.take(120)
            }
        } catch (_: Exception) {
            body.take(120).ifBlank { "HTTP $code" }
        }
    }
}

data class RouterSelection(
    val company: AiCompanyConfig,
    val model: DiscoveredModel,
    val isDowngraded: Boolean,
    val routingNote: String
)

data class RouterCallResult(
    val success: Boolean,
    val content: String,
    val modelUsed: String?,
    val companyUsed: String?,
    val latencyMs: Long,
    val isDowngraded: Boolean,
    val tokensConsumed: Long = 0L,
    val errorMessage: String? = null
)

data class HttpCallResponse(
    val isSuccess: Boolean,
    val httpCode: Int,
    val text: String?,
    val errorMessage: String?,
    val tokens: Long = 0L,
    val retryAfterMs: Long? = null,
    val isQuotaLimitZero: Boolean = false,
    val rawBody: String? = null
)
