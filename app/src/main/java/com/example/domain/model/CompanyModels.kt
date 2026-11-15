package com.example.domain.model

enum class AuthHeaderType {
    BEARER,
    X_GOOG_API_KEY,
    X_API_KEY,
    NONE,
    CUSTOM
}

enum class ApiStyle {
    GEMINI_REST,
    OPENAI_COMPATIBLE,
    ANTHROPIC_MESSAGES,
    COHERE
}

enum class ErrorKind(val label: String, val description: String) {
    INVALID_KEY("Invalid key", "API key was rejected (HTTP 401/403)"),
    NO_ACCESS("No access", "Account does not have permission for this resource"),
    MODEL_NOT_FOUND("Model not found", "Model was not found or decommissioned (HTTP 404)"),
    ENDPOINT_NOT_FOUND("Endpoint not found", "API catalog endpoint URL was not found (HTTP 404)"),
    MODEL_NOT_IN_TIER("Model not in tier", "Model not available in current account tier (Quota limit: 0)"),
    RATE_LIMITED("Rate limited", "Requests per minute limit reached (Cooldown set)"),
    DAILY_QUOTA_USED("Daily quota used", "Daily request limit reached (Cooldown until reset)"),
    ACCOUNT_NEEDS_CREDIT("Account needs credit", "Account has insufficient balance or credit"),
    OVERLOADED("Server overloaded", "Provider capacity overloaded (HTTP 529/503)"),
    SERVER_ERROR("Server error", "Provider internal server error (HTTP 5xx)"),
    NETWORK("Network error", "Unable to establish network connection"),
    TIMEOUT("Timeout", "Request timed out (>12s)"),
    MALFORMED("Malformed response", "Unable to parse provider response"),
    SAFETY_BLOCK("Safety blocked", "Request blocked by safety filters")
}

enum class CompanyStatus(val label: String, val colorHex: Long) {
    NO_KEY("No key", 0xFF6B7280),
    TESTING("Testing...", 0xFF38BDF8),
    CONNECTED("Connected", 0xFF10B981),
    INVALID_KEY("Invalid key", 0xFFEF4444),
    NEEDS_CREDIT("Needs credit", 0xFFF59E0B),
    NEEDS_CREDIT_SKIPPED("Needs credit - skipped by router", 0xFFF59E0B),
    QUOTA_USED("Quota used", 0xFFEF4444)
}

enum class ModelVerificationState {
    VERIFIED,       // Confirmed working via successful HTTP call
    UNVERIFIED,     // Listed in catalog, awaiting lazy probe
    NEEDS_BILLING,  // Account lacks credit / 0 quota for this model
    FAILED          // 404 or persistent error
}

enum class ModelTier(val title: String, val description: String, val badgeColorHex: Long) {
    TOP("TOP", ">= 100B, Reasoner, Opus, Pro", 0xFFA855F7),
    STANDARD("STANDARD", "20-100B, Sonnet, Flash, Chat", 0xFF38BDF8),
    FAST("FAST", "< 20B, Haiku, Mini, Lite", 0xFF10B981)
}

enum class RoleNeedLevel {
    HIGH,
    MEDIUM,
    LOW
}

data class CompanyDefinition(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val authHeaderType: AuthHeaderType,
    val listEndpoint: String?, // null if provider has no list endpoint
    val apiStyle: ApiStyle,
    val getKeyUrl: String,
    val prefixHint: String? = null,
    val needsCreditByDefault: Boolean = false,
    val defaultModelName: String = ""
)

object SupportedCompanies {
    val GEMINI = CompanyDefinition(
        id = "gemini",
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        authHeaderType = AuthHeaderType.X_GOOG_API_KEY,
        listEndpoint = "/v1beta/models?pageSize=1000",
        apiStyle = ApiStyle.GEMINI_REST,
        getKeyUrl = "https://aistudio.google.com/app/apikey",
        prefixHint = "AIza",
        needsCreditByDefault = false,
        defaultModelName = "gemini-2.5-flash"
    )

    val OPENAI = CompanyDefinition(
        id = "openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        authHeaderType = AuthHeaderType.BEARER,
        listEndpoint = "/models",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        getKeyUrl = "https://platform.openai.com/api-keys",
        prefixHint = "sk-",
        needsCreditByDefault = false,
        defaultModelName = "gpt-4o-mini"
    )

    val ANTHROPIC = CompanyDefinition(
        id = "anthropic",
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        authHeaderType = AuthHeaderType.X_API_KEY,
        listEndpoint = "/v1/models",
        apiStyle = ApiStyle.ANTHROPIC_MESSAGES,
        getKeyUrl = "https://console.anthropic.com/settings/keys",
        prefixHint = "sk-ant-",
        needsCreditByDefault = false,
        defaultModelName = "claude-3-5-sonnet-latest"
    )

    val GROQ = CompanyDefinition(
        id = "groq",
        displayName = "Groq Llama-3",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        authHeaderType = AuthHeaderType.BEARER,
        listEndpoint = "/models",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        getKeyUrl = "https://console.groq.com/keys",
        prefixHint = "gsk_",
        needsCreditByDefault = false,
        defaultModelName = "llama-3.3-70b-versatile"
    )

    val OPENROUTER = CompanyDefinition(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        authHeaderType = AuthHeaderType.BEARER,
        listEndpoint = "/models",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        getKeyUrl = "https://openrouter.ai/keys",
        prefixHint = "sk-or-",
        needsCreditByDefault = false,
        defaultModelName = "openrouter/auto"
    )

    val DEEPSEEK = CompanyDefinition(
        id = "deepseek",
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        authHeaderType = AuthHeaderType.BEARER,
        listEndpoint = "/models",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        getKeyUrl = "https://platform.deepseek.com/api_keys",
        prefixHint = "sk-",
        needsCreditByDefault = true,
        defaultModelName = "deepseek-chat"
    )

    val MISTRAL = CompanyDefinition(
        id = "mistral",
        displayName = "Mistral AI",
        defaultBaseUrl = "https://api.mistral.ai/v1",
        authHeaderType = AuthHeaderType.BEARER,
        listEndpoint = "/models",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        getKeyUrl = "https://console.mistral.ai/api-keys/",
        prefixHint = null,
        needsCreditByDefault = false,
        defaultModelName = "mistral-large-latest"
    )

    val COHERE = CompanyDefinition(
        id = "cohere",
        displayName = "Cohere",
        defaultBaseUrl = "https://api.cohere.com/v2",
        authHeaderType = AuthHeaderType.BEARER,
        listEndpoint = "/v1/models?endpoint=chat",
        apiStyle = ApiStyle.COHERE,
        getKeyUrl = "https://dashboard.cohere.com/api-keys",
        prefixHint = "co-",
        needsCreditByDefault = false,
        defaultModelName = "command-r-plus"
    )

    val PERPLEXITY = CompanyDefinition(
        id = "perplexity",
        displayName = "Perplexity AI",
        defaultBaseUrl = "https://api.perplexity.ai",
        authHeaderType = AuthHeaderType.BEARER,
        listEndpoint = null, // curated fallback validated by probe
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        getKeyUrl = "https://www.perplexity.ai/settings/api",
        prefixHint = "pplx-",
        needsCreditByDefault = false,
        defaultModelName = "sonar-pro"
    )

    val OLLAMA = CompanyDefinition(
        id = "ollama",
        displayName = "Local Ollama",
        defaultBaseUrl = "http://localhost:11434",
        authHeaderType = AuthHeaderType.NONE,
        listEndpoint = "/api/tags",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        getKeyUrl = "https://ollama.com",
        prefixHint = null,
        needsCreditByDefault = false,
        defaultModelName = "llama3.2"
    )

    val CUSTOM_OPENAI = CompanyDefinition(
        id = "custom_openai",
        displayName = "Custom OpenAI-Compatible",
        defaultBaseUrl = "https://api.custom-ai.com/v1",
        authHeaderType = AuthHeaderType.BEARER,
        listEndpoint = "/models",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        getKeyUrl = "",
        prefixHint = null,
        needsCreditByDefault = false,
        defaultModelName = "custom-model"
    )

    val ALL: List<CompanyDefinition> = listOf(
        GEMINI,
        OPENAI,
        ANTHROPIC,
        GROQ,
        OPENROUTER,
        DEEPSEEK,
        MISTRAL,
        COHERE,
        PERPLEXITY,
        OLLAMA,
        CUSTOM_OPENAI
    )

    fun findById(id: String): CompanyDefinition? = ALL.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun detectCompanyFromKey(rawKey: String): CompanyDefinition? {
        val key = rawKey.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        return when {
            key.startsWith("AIza") -> GEMINI
            key.startsWith("gsk_") -> GROQ
            key.startsWith("sk-or-") -> OPENROUTER
            key.startsWith("sk-ant-") -> ANTHROPIC
            key.startsWith("pplx-") -> PERPLEXITY
            key.startsWith("co-") -> COHERE
            else -> null // plain sk- or others can be OpenAI or DeepSeek
        }
    }
}

data class DiscoveredModel(
    val id: String, // e.g. "gemini:gemini-2.5-flash"
    val companyId: String,
    val modelName: String,
    val tier: ModelTier,
    val isUsable: Boolean = true,
    val verificationState: ModelVerificationState = ModelVerificationState.UNVERIFIED,
    val isFree: Boolean = false,
    val latencyMs: Long = 0L,
    val contextWindow: Int = 0,
    val callsToday: Int = 0,
    val lastTestedMs: Long = 0L,
    val lastErrorKind: ErrorKind? = null,
    val errorReason: String? = null
)

data class CompanyDiagnosticsInfo(
    val companyId: String,
    val errorKind: ErrorKind,
    val httpCode: Int?,
    val providerStatus: String,
    val redactedUrl: String,
    val providerMessageExcerpt: String, // first 300 chars
    val routerNextAction: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiCompanyConfig(
    val definition: CompanyDefinition,
    val apiKey: String = "",
    val status: CompanyStatus = CompanyStatus.NO_KEY,
    val statusDetail: String? = null,
    val lastErrorKind: ErrorKind? = null,
    val lastDiagnostics: CompanyDiagnosticsInfo? = null,
    val quotaResetTimeMs: Long = 0L,
    val pinnedModelId: String? = null,
    val customEndpoint: String? = null,
    val customAuthHeader: String? = null,
    val callsToday: Int = 0,
    val detectedModels: List<DiscoveredModel> = emptyList()
) {
    val isConnected: Boolean get() = (status == CompanyStatus.CONNECTED || status == CompanyStatus.QUOTA_USED) && apiKey.isNotBlank()
    val isTesting: Boolean get() = status == CompanyStatus.TESTING
    val hasKey: Boolean get() = apiKey.isNotBlank() || definition.authHeaderType == AuthHeaderType.NONE

    // Verification breakdown
    val verifiedCount: Int get() = detectedModels.count { it.verificationState == ModelVerificationState.VERIFIED || (it.isUsable && it.latencyMs > 0) }
    val listedCount: Int get() = detectedModels.size
    val needsBillingCount: Int get() = detectedModels.count { it.verificationState == ModelVerificationState.NEEDS_BILLING || it.lastErrorKind == ErrorKind.MODEL_NOT_IN_TIER || it.lastErrorKind == ErrorKind.ACCOUNT_NEEDS_CREDIT }
    val unverifiedCount: Int get() = detectedModels.count { it.verificationState == ModelVerificationState.UNVERIFIED && it.isUsable && it.latencyMs == 0L }

    val quotaResetsInHours: Int?
        get() {
            if (quotaResetTimeMs <= 0L) return null
            val diffMs = quotaResetTimeMs - System.currentTimeMillis()
            return if (diffMs > 0) ((diffMs + 3599999L) / 3600000L).toInt() else null
        }
}
