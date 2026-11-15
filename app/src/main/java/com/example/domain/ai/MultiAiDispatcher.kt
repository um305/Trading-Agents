package com.example.domain.ai

import android.util.Log
import com.example.data.db.AiModelDao
import com.example.domain.model.AgentRole
import com.example.domain.model.AiModelConfig
import com.example.domain.model.AiProvider
import com.example.domain.model.LoadBalancingMode
import com.example.domain.model.ModelAvailability
import com.example.domain.model.ModelWorkloadMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

class MultiAiDispatcher(private val aiModelDao: AiModelDao) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val roundRobinCounter = AtomicInteger(0)

    companion object {
        val tokensUsedMap = ConcurrentHashMap<String, AtomicLong>()
        val inFlightMap = ConcurrentHashMap<String, AtomicInteger>()
        val cooldownUntilMap = ConcurrentHashMap<String, Long>()
        val consecutiveErrorsMap = ConcurrentHashMap<String, AtomicInteger>()
        val totalRequestsMap = ConcurrentHashMap<String, AtomicInteger>()
        val successRequestsMap = ConcurrentHashMap<String, AtomicInteger>()
        val totalLatencyMap = ConcurrentHashMap<String, AtomicLong>()

        const val DEFAULT_HOURLY_TOKEN_QUOTA = 150_000L

        fun estimateTokens(text: String): Long {
            return (text.length / 3.8).toLong().coerceAtLeast(12)
        }

        fun getModelAvailability(modelId: String, currentInFlight: Int, consecutiveErrors: Int): ModelAvailability {
            val now = System.currentTimeMillis()
            val cooldownUntil = cooldownUntilMap[modelId] ?: 0L
            return when {
                now < cooldownUntil -> ModelAvailability.RATE_LIMITED_429
                consecutiveErrors >= 3 -> ModelAvailability.CIRCUIT_BROKEN
                currentInFlight >= 4 -> ModelAvailability.DEGRADED_HIGH_LATENCY
                else -> ModelAvailability.HEALTHY_AVAILABLE
            }
        }

        fun recordModelSuccess(modelId: String, latencyMs: Long, tokensConsumed: Long) {
            tokensUsedMap.computeIfAbsent(modelId) { AtomicLong(0) }.addAndGet(tokensConsumed)
            successRequestsMap.computeIfAbsent(modelId) { AtomicInteger(0) }.incrementAndGet()
            totalRequestsMap.computeIfAbsent(modelId) { AtomicInteger(0) }.incrementAndGet()
            totalLatencyMap.computeIfAbsent(modelId) { AtomicLong(0) }.addAndGet(latencyMs)
            consecutiveErrorsMap.computeIfAbsent(modelId) { AtomicInteger(0) }.set(0)
        }

        fun recordModelError(modelId: String, errorMsg: String) {
            val errors = consecutiveErrorsMap.computeIfAbsent(modelId) { AtomicInteger(0) }.incrementAndGet()
            totalRequestsMap.computeIfAbsent(modelId) { AtomicInteger(0) }.incrementAndGet()

            if (errorMsg.contains("429") || errorMsg.contains("quota", ignoreCase = true) || errorMsg.contains("rate", ignoreCase = true)) {
                cooldownUntilMap[modelId] = System.currentTimeMillis() + 60_000L
            } else if (errors >= 3) {
                cooldownUntilMap[modelId] = System.currentTimeMillis() + 30_000L
            }
        }

        fun rankModelsByTokenAndAvailability(models: List<AiModelConfig>, estimatedTokens: Long = 100L): List<AiModelConfig> {
            return models.sortedWith(
                compareBy<AiModelConfig> {
                    val errors = consecutiveErrorsMap[it.id]?.get() ?: 0
                    val inFlight = inFlightMap[it.id]?.get() ?: 0
                    getModelAvailability(it.id, inFlight, errors).ordinal
                }.thenBy {
                    inFlightMap[it.id]?.get() ?: 0
                }.thenBy {
                    tokensUsedMap[it.id]?.get() ?: 0L
                }
            )
        }

        fun rankModelsForFreeTierCascade(models: List<AiModelConfig>): List<AiModelConfig> {
            return models.sortedWith(
                compareBy<AiModelConfig> {
                    when (it.provider) {
                        AiProvider.GROQ -> 1
                        AiProvider.GEMINI -> 2
                        AiProvider.MISTRAL -> 3
                        AiProvider.DEEPSEEK -> 4
                        AiProvider.OPENROUTER -> 5
                        AiProvider.OLLAMA -> 6
                        else -> 7
                    }
                }.thenBy {
                    tokensUsedMap[it.id]?.get() ?: 0L
                }
            )
        }

        fun getWorkloadMetrics(models: List<AiModelConfig>): List<ModelWorkloadMetrics> {
            val now = System.currentTimeMillis()
            return models.map { model ->
                val inFlight = inFlightMap[model.id]?.get() ?: 0
                val errors = consecutiveErrorsMap[model.id]?.get() ?: 0
                val totalReq = totalRequestsMap[model.id]?.get() ?: model.requestsRouted
                val successReq = successRequestsMap[model.id]?.get() ?: 0
                val totalLat = totalLatencyMap[model.id]?.get() ?: (model.lastLatencyMs * totalReq.coerceAtLeast(1))
                val tokensUsed = tokensUsedMap[model.id]?.get() ?: model.totalTokensUsed
                val cooldownUntil = cooldownUntilMap[model.id] ?: 0L
                val cooldownRemaining = if (now < cooldownUntil) (cooldownUntil - now) / 1000 else 0L

                val avgLat = if (totalReq > 0) totalLat / totalReq else model.lastLatencyMs.coerceAtLeast(120)
                val availability = getModelAvailability(model.id, inFlight, errors)

                ModelWorkloadMetrics(
                    modelId = model.id,
                    provider = model.provider,
                    modelName = model.modelName,
                    estimatedTokensConsumed = tokensUsed,
                    tokenQuotaPerHour = DEFAULT_HOURLY_TOKEN_QUOTA,
                    remainingTokens = (DEFAULT_HOURLY_TOKEN_QUOTA - tokensUsed).coerceAtLeast(0),
                    inFlightRequests = inFlight,
                    availability = availability,
                    consecutiveErrors = errors,
                    totalRequests = totalReq,
                    successfulRequests = successReq,
                    avgLatencyMs = avgLat,
                    cooldownRemainingSec = cooldownRemaining
                )
            }
        }
    }

    suspend fun dispatchPrompt(
        role: AgentRole,
        prompt: String,
        models: List<AiModelConfig>,
        mode: LoadBalancingMode = LoadBalancingMode.TOKEN_AVAILABILITY_BALANCED
    ): AiExecutionResult {
        return executePromptWithFailover(prompt, models, role, mode)
    }

    suspend fun getFleetMetrics(models: List<AiModelConfig>): List<ModelWorkloadMetrics> = withContext(Dispatchers.Default) {
        getWorkloadMetrics(models)
    }

    suspend fun executePromptWithFailover(
        prompt: String,
        models: List<AiModelConfig>,
        role: AgentRole,
        balancingMode: LoadBalancingMode = LoadBalancingMode.TOKEN_AVAILABILITY_BALANCED
    ): AiExecutionResult = withContext(Dispatchers.IO) {
        val enabledModels = models.filter { it.isEnabled && it.apiKey.isNotBlank() }
        if (enabledModels.isEmpty()) {
            return@withContext AiExecutionResult(
                providerName = "Deterministic Quant Engine",
                modelName = "Built-in SMC",
                response = "No active external AI keys configured. Running pure deterministic quantitative calculations.",
                latencyMs = 50,
                success = false,
                errorMessage = "API key unconfigured. Enter keys in AI Fleet."
            )
        }

        val estimatedTokens = estimateTokens(prompt)
        val candidateModels: List<AiModelConfig> = when (balancingMode) {
            LoadBalancingMode.SPECIALIZED_ROLE -> {
                val assigned = enabledModels.filter { it.assignedRole == role }
                val unassigned = enabledModels.filter { it.assignedRole == null }
                val remaining = enabledModels.filter { it.assignedRole != null && it.assignedRole != role }
                (assigned + unassigned + remaining).distinctBy { it.id }
            }
            LoadBalancingMode.AUTO_CASCADE_FREE_TIER -> {
                rankModelsForFreeTierCascade(enabledModels)
            }
            LoadBalancingMode.TOKEN_AVAILABILITY_BALANCED -> {
                rankModelsByTokenAndAvailability(enabledModels, estimatedTokens)
            }
            LoadBalancingMode.ROUND_ROBIN_PARALLEL -> {
                val startIndex = Math.abs(roundRobinCounter.getAndIncrement()) % enabledModels.size
                List(enabledModels.size) { i -> enabledModels[(startIndex + i) % enabledModels.size] }
            }
        }

        var lastError: Exception? = null
        var lastLatency = 0L

        for ((attemptIndex, targetModel) in candidateModels.withIndex()) {
            val startTime = System.currentTimeMillis()
            inFlightMap.computeIfAbsent(targetModel.id) { AtomicInteger(0) }.incrementAndGet()

            var responseText = ""
            var tokensConsumed = 0L
            var isSuccess = false

            try {
                val callResult = executeHttpAiCall(targetModel, prompt)
                responseText = callResult.first
                tokensConsumed = if (callResult.second > 0) callResult.second else (estimatedTokens + estimateTokens(responseText))
                isSuccess = true
            } catch (e: Exception) {
                Log.e("MultiAiDispatcher", "AI call failed for ${targetModel.provider.displayName} (${targetModel.modelName})", e)
                lastError = e
                isSuccess = false
                recordModelError(targetModel.id, e.message ?: "")
            } finally {
                inFlightMap.computeIfAbsent(targetModel.id) { AtomicInteger(0) }.decrementAndGet()
            }

            val latency = System.currentTimeMillis() - startTime
            lastLatency = latency

            if (isSuccess) {
                recordModelSuccess(targetModel.id, latency, tokensConsumed)
                try {
                    aiModelDao.recordRequest(
                        id = targetModel.id,
                        latencyMs = latency,
                        status = "Active (${latency}ms • ${tokensConsumed} tok)",
                        tokens = tokensConsumed,
                        errorMsg = null
                    )
                } catch (e: Exception) {
                    Log.w("MultiAiDispatcher", "Failed to update AI model telemetry", e)
                }

                val failoverPrefix = if (attemptIndex > 0) "[Auto-Failover Active: rerouted to ${targetModel.provider.displayName}] " else ""
                return@withContext AiExecutionResult(
                    providerName = targetModel.provider.displayName,
                    modelName = targetModel.modelName,
                    response = failoverPrefix + responseText,
                    latencyMs = latency,
                    success = true
                )
            }
        }

        AiExecutionResult(
            providerName = "Deterministic Quant Engine",
            modelName = "Built-in SMC",
            response = "External AI failed or rate-limited. Falling back to deterministic quantitative analysis.",
            latencyMs = lastLatency,
            success = false,
            errorMessage = lastError?.message ?: "Fleet call failed"
        )
    }

    suspend fun executeModelCall(
        model: AiModelConfig,
        prompt: String,
        roleName: String = "NewsScannerAgent"
    ): AiExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var responseText = ""
        var tokensConsumed = 0L
        var isSuccess = false
        var errorMsg: String? = null

        val cleanKey = model.apiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (cleanKey.isBlank() && model.provider != AiProvider.OLLAMA) {
            return@withContext AiExecutionResult(
                providerName = model.provider.displayName,
                modelName = model.modelName,
                response = "",
                latencyMs = 0,
                success = false,
                errorMessage = "API key unconfigured. Enter keys in AI Fleet."
            )
        }

        try {
            val callResult = executeHttpAiCall(model.copy(apiKey = cleanKey), prompt)
            responseText = callResult.first
            tokensConsumed = if (callResult.second > 0) callResult.second else (estimateTokens(prompt) + estimateTokens(responseText))
            isSuccess = true
        } catch (e: Exception) {
            Log.e("MultiAiDispatcher", "AI call failed for ${model.provider.displayName}", e)
            errorMsg = e.message ?: "Call failed"
            isSuccess = false
        }

        val latency = System.currentTimeMillis() - startTime
        if (isSuccess) {
            recordModelSuccess(model.id, latency, tokensConsumed)
            try {
                aiModelDao.recordRequest(
                    id = model.id,
                    latencyMs = latency,
                    status = "Online (${latency}ms)",
                    tokens = tokensConsumed,
                    errorMsg = null
                )
            } catch (e: Exception) {
                Log.w("MultiAiDispatcher", "Failed to update AI model telemetry", e)
            }
        } else {
            recordModelError(model.id, errorMsg ?: "Failed")
            try {
                aiModelDao.recordError(model.id, "Error (${errorMsg?.take(22) ?: "Failed"})", errorMsg ?: "Call failed")
            } catch (e: Exception) {
                Log.w("MultiAiDispatcher", "Failed to update AI model error telemetry", e)
            }
        }

        AiExecutionResult(
            providerName = model.provider.displayName,
            modelName = model.modelName,
            response = responseText,
            latencyMs = latency,
            success = isSuccess,
            errorMessage = errorMsg
        )
    }

    suspend fun broadcastPromptToAllModels(
        prompt: String,
        models: List<AiModelConfig>,
        roleName: String = "NewsScannerAgent"
    ): List<AiExecutionResult> = withContext(Dispatchers.IO) {
        val enabledModels = models.filter { it.isEnabled && it.apiKey.isNotBlank() }
        if (enabledModels.isEmpty()) {
            return@withContext emptyList()
        }

        coroutineScope {
            enabledModels.map { model ->
                async {
                    executeModelCall(model, prompt, roleName)
                }
            }.awaitAll()
        }
    }

    suspend fun executeGeminiPrompt(
        prompt: String,
        models: List<AiModelConfig>
    ): AiExecutionResult = withContext(Dispatchers.IO) {
        val geminiModel = models.firstOrNull { it.provider == AiProvider.GEMINI && it.isEnabled && it.apiKey.isNotBlank() }
            ?: models.firstOrNull { it.isEnabled && it.apiKey.isNotBlank() }

        if (geminiModel == null) {
            return@withContext AiExecutionResult(
                providerName = "Google Gemini",
                modelName = "gemini-2.5-flash",
                response = "",
                latencyMs = 0,
                success = false,
                errorMessage = "Gemini API Key missing. Enter your key in AI Fleet."
            )
        }

        executeModelCall(geminiModel, prompt, "NewsScannerAgent (Gemini)")
    }

    suspend fun fetchAvailableModels(config: AiModelConfig): List<String> = withContext(Dispatchers.IO) {
        val cleanKey = config.apiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (cleanKey.isBlank() && config.provider != AiProvider.OLLAMA) {
            throw IllegalArgumentException("API Key is blank. Enter your key first.")
        }

        when (config.provider) {
            AiProvider.GEMINI -> {
                val url = "https://generativelanguage.googleapis.com/v1beta/models"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", cleanKey)
                    .get()
                    .build()

                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        throw Exception("Gemini HTTP ${resp.code}: ${parseErrorSummary(body, resp.code)}")
                    }
                    val root = JSONObject(body)
                    val modelsArr = root.optJSONArray("models") ?: JSONArray()
                    val result = mutableListOf<String>()
                    for (i in 0 until modelsArr.length()) {
                        val m = modelsArr.optJSONObject(i) ?: continue
                        val name = m.optString("name").removePrefix("models/")
                        val methods = m.optJSONArray("supportedGenerationMethods")
                        val canGenerate = methods?.let { arr ->
                            (0 until arr.length()).any { arr.optString(it) == "generateContent" }
                        } ?: true
                        if (canGenerate) result.add(name)
                    }
                    if (result.isEmpty()) listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-pro") else result
                }
            }
            AiProvider.OPENAI, AiProvider.DEEPSEEK, AiProvider.GROQ, AiProvider.MISTRAL, AiProvider.PERPLEXITY, AiProvider.OLLAMA, AiProvider.OPENROUTER, AiProvider.CUSTOM_REST -> {
                val base = config.customEndpoint?.takeIf { it.isNotBlank() } ?: when (config.provider) {
                    AiProvider.OPENAI -> "https://api.openai.com/v1"
                    AiProvider.DEEPSEEK -> "https://api.deepseek.com/v1"
                    AiProvider.GROQ -> "https://api.groq.com/openai/v1"
                    AiProvider.MISTRAL -> "https://api.mistral.ai/v1"
                    AiProvider.PERPLEXITY -> "https://api.perplexity.ai"
                    AiProvider.OLLAMA -> "http://localhost:11434/v1"
                    AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1"
                    else -> "https://api.openai.com/v1"
                }
                val cleanBase = base.trim().removeSuffix("/").removeSuffix("/chat/completions")
                val url = "$cleanBase/models"

                val reqBuilder = Request.Builder().url(url).get()
                if (cleanKey.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", if (cleanKey.startsWith("Bearer ", ignoreCase = true)) cleanKey else "Bearer $cleanKey")
                }

                client.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        throw Exception("${config.provider.displayName} HTTP ${resp.code}: ${parseErrorSummary(body, resp.code)}")
                    }
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    val list = mutableListOf<String>()
                    for (i in 0 until data.length()) {
                        val obj = data.optJSONObject(i) ?: continue
                        val id = obj.optString("id")
                        if (id.isNotBlank()) list.add(id)
                    }
                    if (list.isEmpty()) listOf(config.modelName) else list.sorted()
                }
            }
            AiProvider.ANTHROPIC -> {
                val url = "https://api.anthropic.com/v1/models"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-api-key", cleanKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .get()
                    .build()

                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        throw Exception("Anthropic HTTP ${resp.code}: ${parseErrorSummary(body, resp.code)}")
                    }
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    val list = mutableListOf<String>()
                    for (i in 0 until data.length()) {
                        val obj = data.optJSONObject(i) ?: continue
                        val id = obj.optString("id")
                        if (id.isNotBlank()) list.add(id)
                    }
                    if (list.isEmpty()) listOf("claude-3-5-sonnet-latest", "claude-3-5-haiku-latest") else list
                }
            }
            else -> listOf(config.modelName)
        }
    }

    suspend fun testModelPing(model: AiModelConfig): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanKey = model.apiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (cleanKey.isBlank() && model.provider != AiProvider.OLLAMA) {
            val msg = "Unconfigured: API Key is blank. Enter key in AI Fleet."
            aiModelDao.recordError(model.id, "Unconfigured", msg)
            return@withContext Pair(false, msg)
        }

        val start = System.currentTimeMillis()
        try {
            val callResult = executeHttpAiCall(model.copy(apiKey = cleanKey), "Return: READY")
            val latency = System.currentTimeMillis() - start
            val successMsg = "HTTP 200 OK (${latency}ms) • Verified Online"
            aiModelDao.recordRequest(model.id, latency, "Online (${latency}ms)", callResult.second, null)
            Pair(true, successMsg)
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            val errorText = e.message ?: "Ping failed"
            val brief = errorText.take(28)
            aiModelDao.recordError(model.id, "Error ($brief)", errorText)
            Pair(false, errorText)
        }
    }

    private fun normalizeOpenAiCompatibleUrl(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        return when {
            trimmed.endsWith("/chat/completions") -> trimmed
            trimmed.endsWith("/openai/v1") -> "$trimmed/chat/completions"
            trimmed.endsWith("/openai") -> "$trimmed/v1/chat/completions"
            trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
            trimmed.contains("groq.com") -> {
                if (trimmed.endsWith("/v1")) "$trimmed/chat/completions"
                else if (trimmed.endsWith("/openai")) "$trimmed/v1/chat/completions"
                else "$trimmed/openai/v1/chat/completions"
            }
            else -> "$trimmed/chat/completions"
        }
    }

    private fun parseErrorSummary(rawBody: String, httpCode: Int): String {
        return try {
            val root = JSONObject(rawBody)
            root.optJSONObject("error")?.optString("message")
                ?: root.optString("message")
                ?: root.optString("error")
                ?: "HTTP $httpCode"
        } catch (e: Exception) {
            "HTTP $httpCode: ${rawBody.take(150)}"
        }
    }

    /**
     * Returns Pair<ResponseText, TokensConsumed>
     */
    private fun executeHttpAiCall(config: AiModelConfig, prompt: String): Pair<String, Long> {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val cleanKey = config.apiKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()

        val (url, jsonBody, headers) = when (config.provider) {
            AiProvider.GEMINI -> {
                val fullUrl = "https://generativelanguage.googleapis.com/v1beta/models/${config.modelName}:generateContent"
                val body = JSONObject().apply {
                    val contents = JSONArray().apply {
                        val item = JSONObject().apply {
                            val parts = JSONArray().apply {
                                put(JSONObject().put("text", prompt))
                            }
                            put("parts", parts)
                        }
                        put(item)
                    }
                    put("contents", contents)
                }
                val hdrs = mapOf("x-goog-api-key" to cleanKey)
                Triple(fullUrl, body.toString(), hdrs)
            }
            AiProvider.OPENAI, AiProvider.DEEPSEEK, AiProvider.GROQ, AiProvider.MISTRAL, AiProvider.PERPLEXITY, AiProvider.OLLAMA, AiProvider.OPENROUTER, AiProvider.CUSTOM_REST -> {
                val rawEndpoint = config.customEndpoint?.takeIf { it.isNotBlank() } ?: when (config.provider) {
                    AiProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
                    AiProvider.DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions"
                    AiProvider.GROQ -> "https://api.groq.com/openai/v1/chat/completions"
                    AiProvider.MISTRAL -> "https://api.mistral.ai/v1/chat/completions"
                    AiProvider.PERPLEXITY -> "https://api.perplexity.ai/chat/completions"
                    AiProvider.OLLAMA -> "http://localhost:11434/v1/chat/completions"
                    AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
                    AiProvider.CUSTOM_REST -> "https://api.groq.com/openai/v1/chat/completions"
                    else -> "https://api.openai.com/v1/chat/completions"
                }
                val endpoint = normalizeOpenAiCompatibleUrl(rawEndpoint)

                val body = JSONObject().apply {
                    put("model", config.modelName)
                    val messages = JSONArray().apply {
                        put(JSONObject().put("role", "user").put("content", prompt))
                    }
                    put("messages", messages)
                    put("max_tokens", 300)
                }
                val hdrs = mutableMapOf<String, String>()
                val authHeaderType = config.customAuthHeader?.trim()
                if (authHeaderType.isNullOrBlank() ||
                    authHeaderType.equals("Bearer", ignoreCase = true) ||
                    authHeaderType.equals("Authorization", ignoreCase = true)) {
                    val bearerVal = if (cleanKey.startsWith("Bearer ", ignoreCase = true)) cleanKey else "Bearer $cleanKey"
                    hdrs["Authorization"] = bearerVal
                } else {
                    hdrs[authHeaderType] = cleanKey
                }
                Triple(endpoint, body.toString(), hdrs)
            }
            AiProvider.COHERE -> {
                val rawEndpoint = config.customEndpoint?.takeIf { it.isNotBlank() } ?: "https://api.cohere.com/v2/chat"
                val endpoint = if (rawEndpoint.endsWith("/chat") || rawEndpoint.endsWith("/v2/chat")) rawEndpoint else "$rawEndpoint/v2/chat"
                val body = JSONObject().apply {
                    put("model", config.modelName)
                    val messages = JSONArray().apply {
                        put(JSONObject().put("role", "user").put("content", JSONObject().put("type", "text").put("text", prompt)))
                    }
                    put("messages", messages)
                }
                val bearerVal = if (cleanKey.startsWith("Bearer ", ignoreCase = true)) cleanKey else "Bearer $cleanKey"
                val hdrs = mapOf("Authorization" to bearerVal)
                Triple(endpoint, body.toString(), hdrs)
            }
            AiProvider.CUSTOM_ENDPOINT -> {
                val rawEndpoint = config.customEndpoint?.takeIf { it.isNotBlank() } ?: "https://api.groq.com/openai/v1/chat/completions"
                val endpoint = normalizeOpenAiCompatibleUrl(rawEndpoint)
                val body = JSONObject().apply {
                    put("prompt", prompt)
                    put("model", config.modelName)
                    val messages = JSONArray().apply {
                        put(JSONObject().put("role", "user").put("content", prompt))
                    }
                    put("messages", messages)
                }
                val hdrs = mutableMapOf<String, String>()
                val authHeaderType = config.customAuthHeader?.trim()
                if (authHeaderType.isNullOrBlank() ||
                    authHeaderType.equals("Bearer", ignoreCase = true) ||
                    authHeaderType.equals("Authorization", ignoreCase = true)) {
                    val bearerVal = if (cleanKey.startsWith("Bearer ", ignoreCase = true)) cleanKey else "Bearer $cleanKey"
                    hdrs["Authorization"] = bearerVal
                } else {
                    hdrs[authHeaderType] = cleanKey
                }
                Triple(endpoint, body.toString(), hdrs)
            }
            AiProvider.ANTHROPIC -> {
                val endpoint = config.customEndpoint?.takeIf { it.isNotBlank() } ?: "https://api.anthropic.com/v1/messages"
                val body = JSONObject().apply {
                    put("model", config.modelName)
                    put("max_tokens", 300)
                    val messages = JSONArray().apply {
                        put(JSONObject().put("role", "user").put("content", prompt))
                    }
                    put("messages", messages)
                }
                val hdrs = mapOf(
                    "x-api-key" to cleanKey,
                    "anthropic-version" to "2023-06-01"
                )
                Triple(endpoint, body.toString(), hdrs)
            }
            AiProvider.SIMULATION -> Triple("", "", emptyMap())
        }

        val requestBuilder = Request.Builder().url(url).post(jsonBody.toRequestBody(mediaType))
        headers.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

        client.newCall(requestBuilder.build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val errorSummary = parseErrorSummary(body, resp.code)
                throw Exception("HTTP ${resp.code}: $errorSummary")
            }
            val text = parseAiResponse(config.provider, body)
            val tokens = extractRealTokens(config.provider, body)
            return Pair(text, tokens)
        }
    }

    private fun extractRealTokens(provider: AiProvider, rawJson: String): Long {
        return try {
            val root = JSONObject(rawJson)
            when (provider) {
                AiProvider.GEMINI -> {
                    root.optJSONObject("usageMetadata")?.optLong("totalTokenCount") ?: 0L
                }
                AiProvider.OPENAI, AiProvider.DEEPSEEK, AiProvider.GROQ, AiProvider.MISTRAL, AiProvider.PERPLEXITY, AiProvider.OPENROUTER, AiProvider.CUSTOM_REST -> {
                    root.optJSONObject("usage")?.optLong("total_tokens") ?: 0L
                }
                AiProvider.ANTHROPIC -> {
                    val usage = root.optJSONObject("usage")
                    val inTok = usage?.optLong("input_tokens") ?: 0L
                    val outTok = usage?.optLong("output_tokens") ?: 0L
                    inTok + outTok
                }
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun parseAiResponse(provider: AiProvider, rawJson: String): String {
        val root = try {
            JSONObject(rawJson)
        } catch (e: Exception) {
            return rawJson.take(500)
        }
        return when (provider) {
            AiProvider.GEMINI -> {
                root.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?: "Gemini reasoning received."
            }
            AiProvider.OPENAI, AiProvider.DEEPSEEK, AiProvider.GROQ, AiProvider.MISTRAL, AiProvider.PERPLEXITY, AiProvider.OLLAMA, AiProvider.OPENROUTER, AiProvider.CUSTOM_REST -> {
                val choices = root.optJSONArray("choices")
                val message = choices?.optJSONObject(0)?.optJSONObject("message")
                message?.optString("content")
                    ?: choices?.optJSONObject(0)?.optString("text")
                    ?: root.optString("response")
                    ?: "Model output received."
            }
            AiProvider.COHERE -> {
                root.optJSONObject("message")
                    ?.optJSONArray("content")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?: "Cohere output received."
            }
            AiProvider.CUSTOM_ENDPOINT -> {
                root.optString("response", root.optString("text", root.optString("output", rawJson.take(300))))
            }
            AiProvider.ANTHROPIC -> {
                root.optJSONArray("content")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?: "Claude reasoning received."
            }
            AiProvider.SIMULATION -> "Simulated neural inference"
        }
    }
}

data class AiExecutionResult(
    val providerName: String,
    val modelName: String,
    val response: String,
    val latencyMs: Long,
    val success: Boolean,
    val errorMessage: String? = null
)
