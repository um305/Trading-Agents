package com.example

import com.example.domain.agents.TradingAgentsEngine
import com.example.domain.ai.AiFleetRouter
import com.example.domain.ai.CompanyDiscoveryEngine
import com.example.domain.model.AgentRole
import com.example.domain.model.AiCompanyConfig
import com.example.domain.model.AssetType
import com.example.domain.model.CandlePoint
import com.example.domain.model.CompanyDefinition
import com.example.domain.model.CompanyStatus
import com.example.domain.model.DiscoveredModel
import com.example.domain.model.LoadBalancingMode
import com.example.domain.model.MarketQuote
import com.example.domain.model.ModelTier
import com.example.domain.model.RoleNeedLevel
import com.example.domain.model.SupportedCompanies
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiFleetRedesignTest {

    @Before
    fun setup() {
        AiFleetRouter.permanentlyBrokenModels.clear()
        AiFleetRouter.cooldownUntilMap.clear()
        AiFleetRouter.invalidKeysMap.clear()
        AiFleetRouter.consecutiveErrorsMap.clear()
        AiFleetRouter.tokensUsedMap.clear()
    }

    @Test
    fun testTierRankingClassification() {
        // TOP tier models
        assertEquals(ModelTier.TOP, CompanyDiscoveryEngine.classifyTier("gemini-2.5-pro"))
        assertEquals(ModelTier.TOP, CompanyDiscoveryEngine.classifyTier("claude-3-opus-20240229"))
        assertEquals(ModelTier.TOP, CompanyDiscoveryEngine.classifyTier("deepseek-reasoner"))
        assertEquals(ModelTier.TOP, CompanyDiscoveryEngine.classifyTier("deepseek-r1"))
        assertEquals(ModelTier.TOP, CompanyDiscoveryEngine.classifyTier("llama-3.1-405b-instruct"))
        assertEquals(ModelTier.TOP, CompanyDiscoveryEngine.classifyTier("mistral-large-latest"))

        // STANDARD tier models
        assertEquals(ModelTier.STANDARD, CompanyDiscoveryEngine.classifyTier("gemini-2.5-flash"))
        assertEquals(ModelTier.STANDARD, CompanyDiscoveryEngine.classifyTier("claude-3-5-sonnet-latest"))
        assertEquals(ModelTier.STANDARD, CompanyDiscoveryEngine.classifyTier("gpt-4o"))
        assertEquals(ModelTier.STANDARD, CompanyDiscoveryEngine.classifyTier("deepseek-chat"))
        assertEquals(ModelTier.STANDARD, CompanyDiscoveryEngine.classifyTier("llama-3.3-70b-versatile"))

        // FAST tier models
        assertEquals(ModelTier.FAST, CompanyDiscoveryEngine.classifyTier("claude-3-5-haiku-latest"))
        assertEquals(ModelTier.FAST, CompanyDiscoveryEngine.classifyTier("gpt-4o-mini"))
        assertEquals(ModelTier.FAST, CompanyDiscoveryEngine.classifyTier("gemini-2.0-flash-lite"))
        assertEquals(ModelTier.FAST, CompanyDiscoveryEngine.classifyTier("llama-3.2-3b-instruct"))
        assertEquals(ModelTier.FAST, CompanyDiscoveryEngine.classifyTier("mistral-small-latest"))
    }

    @Test
    fun testKeyPrefixDetectionAndSanitization() {
        // Clean prefixes
        assertEquals("gemini", SupportedCompanies.detectCompanyFromKey("AIzaSyB1234567890abcdef")?.id)
        assertEquals("groq", SupportedCompanies.detectCompanyFromKey("gsk_9876543210zyxwv")?.id)
        assertEquals("openrouter", SupportedCompanies.detectCompanyFromKey("sk-or-v1-123456789")?.id)
        assertEquals("anthropic", SupportedCompanies.detectCompanyFromKey("sk-ant-api03-abcdef")?.id)
        assertEquals("perplexity", SupportedCompanies.detectCompanyFromKey("pplx-1234567890")?.id)

        // Sanitization with quotes, spaces, newlines
        val rawPasted = "\n  \"AIzaSyCleanTestKey\"  \r\n"
        val detected = SupportedCompanies.detectCompanyFromKey(rawPasted)
        assertNotNull(detected)
        assertEquals("gemini", detected?.id)
    }

    @Test
    fun testRouterRoleNeedLevels() {
        assertEquals(RoleNeedLevel.HIGH, AiFleetRouter.getRoleNeedLevel(AgentRole.CHIEF_STRATEGIST))
        assertEquals(RoleNeedLevel.HIGH, AiFleetRouter.getRoleNeedLevel(AgentRole.RISK_MANAGEMENT))
        assertEquals(RoleNeedLevel.MEDIUM, AiFleetRouter.getRoleNeedLevel(AgentRole.TECHNICAL))
        assertEquals(RoleNeedLevel.MEDIUM, AiFleetRouter.getRoleNeedLevel(AgentRole.MARKET_MANIPULATION))
        assertEquals(RoleNeedLevel.MEDIUM, AiFleetRouter.getRoleNeedLevel(AgentRole.ORDER_FLOW))
        assertEquals(RoleNeedLevel.LOW, AiFleetRouter.getRoleNeedLevel(AgentRole.SENTIMENT))
    }

    @Test
    fun testAutomaticDowngradeWhenTopTierUnavailable() = runBlocking {
        val router = AiFleetRouter()

        // Company with ONLY a FAST tier model available
        val fastModel = DiscoveredModel(
            id = "groq:llama-3.2-3b",
            companyId = "groq",
            modelName = "llama-3.2-3b",
            tier = ModelTier.FAST,
            isUsable = true
        )
        val company = AiCompanyConfig(
            definition = SupportedCompanies.GROQ,
            apiKey = "gsk_test",
            status = CompanyStatus.CONNECTED,
            detectedModels = listOf(fastModel)
        )

        // Chief Strategist requires HIGH need (TOP tier), but only FAST is available
        val selection = router.selectCandidateModel(AgentRole.CHIEF_STRATEGIST, listOf(company))

        assertNotNull(selection)
        assertEquals("llama-3.2-3b", selection?.model?.modelName)
        assertTrue("Must be marked as downgraded when running on lower tier", selection!!.isDowngraded)
    }

    @Test
    fun testPinnedModelIsPreferredUntilDisabled() = runBlocking {
        val router = AiFleetRouter()

        val flashModel = DiscoveredModel(id = "gemini:gemini-2.5-flash", companyId = "gemini", modelName = "gemini-2.5-flash", tier = ModelTier.STANDARD, isUsable = true)
        val proModel = DiscoveredModel(id = "gemini:gemini-2.5-pro", companyId = "gemini", modelName = "gemini-2.5-pro", tier = ModelTier.TOP, isUsable = true)

        val company = AiCompanyConfig(
            definition = SupportedCompanies.GEMINI,
            apiKey = "AIza_test",
            status = CompanyStatus.CONNECTED,
            pinnedModelId = "gemini-2.5-pro",
            detectedModels = listOf(flashModel, proModel)
        )

        val selection = router.selectCandidateModel(AgentRole.TECHNICAL, listOf(company))
        assertNotNull(selection)
        assertEquals("gemini-2.5-pro", selection?.model?.modelName)
        assertEquals("Pinned model selected", selection?.routingNote)
    }

    @Test
    fun test404DecommissionedModelIsNeverRetried() {
        val model = DiscoveredModel(
            id = "openai:gpt-3.5-turbo-retired",
            companyId = "openai",
            modelName = "gpt-3.5-turbo-retired",
            tier = ModelTier.FAST,
            isUsable = true
        )

        assertTrue("Initially eligible", AiFleetRouter.isModelEligible(model))

        // Mark 404 permanent break
        AiFleetRouter.permanentlyBrokenModels[model.id] = true

        assertFalse("404 model must NEVER be retried", AiFleetRouter.isModelEligible(model))
    }

    @Test
    fun testRateLimitCooldownBlocksModelTemporarily() {
        val model = DiscoveredModel(
            id = "gemini:gemini-2.5-flash",
            companyId = "gemini",
            modelName = "gemini-2.5-flash",
            tier = ModelTier.STANDARD,
            isUsable = true
        )

        assertTrue(AiFleetRouter.isModelEligible(model))

        // 429 sets cooldown for 60s
        AiFleetRouter.cooldownUntilMap[model.id] = System.currentTimeMillis() + 60_000L

        assertFalse("Model in cooldown must not be eligible", AiFleetRouter.isModelEligible(model))

        // Reset cooldown
        AiFleetRouter.cooldownUntilMap[model.id] = System.currentTimeMillis() - 1000L
        assertTrue("Model after cooldown must be eligible again", AiFleetRouter.isModelEligible(model))
    }

    @Test
    fun testSingleCompanyRunsAllRolesAndTwoCompaniesShareLoad() = runBlocking {
        val router = AiFleetRouter()

        val geminiModel = DiscoveredModel("gemini:gemini-2.5-flash", "gemini", "gemini-2.5-flash", ModelTier.STANDARD, true)
        val geminiCompany = AiCompanyConfig(SupportedCompanies.GEMINI, "AIza_key", CompanyStatus.CONNECTED, detectedModels = listOf(geminiModel))

        // 1. Single company: all roles run on it
        val sel1 = router.selectCandidateModel(AgentRole.TECHNICAL, listOf(geminiCompany))
        assertEquals("gemini", sel1?.company?.definition?.id)

        // 2. Add second company: groq
        val groqModel = DiscoveredModel("groq:llama-3.3-70b", "groq", "llama-3.3-70b", ModelTier.STANDARD, true, isFree = true)
        val groqCompany = AiCompanyConfig(SupportedCompanies.GROQ, "gsk_key", CompanyStatus.CONNECTED, detectedModels = listOf(groqModel))

        // In AUTO_CASCADE_FREE_TIER, free model is preferred or workload distributed
        val sel2 = router.selectCandidateModel(AgentRole.TECHNICAL, listOf(geminiCompany, groqCompany), LoadBalancingMode.AUTO_CASCADE_FREE_TIER)
        assertNotNull(sel2)
        assertEquals("groq", sel2?.company?.definition?.id)
    }

    @Test
    fun testEngineOnlyModeProducesCompleteSetupsWithZeroKeys() {
        val candles = generateMockCandles(basePrice = 100.0, count = 150)
        val livePrice = candles.last().close
        val quote = MarketQuote(
            symbol = "SOL",
            name = "Solana",
            assetType = AssetType.CRYPTO,
            currentPrice = livePrice,
            change = 2.5,
            changePercent = 2.5,
            open = 98.0,
            dayHigh = livePrice + 2.0,
            dayLow = 97.0,
            volume = 5000000.0,
            candles = candles,
            timeframe = com.example.domain.model.Timeframe.FIVE_MIN
        )

        // Run pure deterministic multi-agent engine with zero external AI keys
        val synthesis = TradingAgentsEngine.runMultiAgentAnalysis(
            quote = quote,
            news = emptyList()
        )

        assertNotNull(synthesis)
        assertTrue("Engine must produce a valid action", synthesis.action != null)
        assertTrue("Confidence score must be realistic", synthesis.confidenceScore in 1..100)
        assertTrue("Target price must be positive", synthesis.targetPrice > 0.0)
        assertTrue("Stop loss must be positive", synthesis.stopLossPrice > 0.0)
        assertTrue("Agent reports must be populated", synthesis.agentReports.isNotEmpty())

        // Confirm reports are labeled "Rule-based"
        val techReport = synthesis.agentReports.firstOrNull { it.role == AgentRole.TECHNICAL }
        assertNotNull(techReport)
        assertEquals("Rule-based", techReport?.sourceBadge)
    }

    @Test
    fun testGemini429WithStandardTextAndLimit5KeepsCompanyConnected() {
        // Gemini standard 429 error body mentioning "plan and billing details" with limit: 5
        val gemini429Body = """
            {
              "error": {
                "code": 429,
                "message": "Resource has been exhausted (e.g. check quota). Please review your plan and billing details at https://aistudio.google.com.",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.QuotaFailure",
                    "violations": [
                      {
                        "subject": "quota:PerMinute",
                        "description": "Rate limit exceeded: limit: 5, model: gemini-2.5-pro"
                      }
                    ]
                  },
                  {
                    "@type": "type.googleapis.com/google.rpc.RetryInfo",
                    "retryDelay": "12s"
                  }
                ]
              }
            }
        """.trimIndent()

        val errKind = CompanyDiscoveryEngine.classifyError("gemini", 429, gemini429Body, isListCall = false)
        assertEquals(com.example.domain.model.ErrorKind.RATE_LIMITED, errKind)

        val retrySec = CompanyDiscoveryEngine.parseRetryDelaySeconds(gemini429Body)
        assertEquals(12, retrySec)
    }

    @Test
    fun testGeminiLimit0IsModelNotInTierNotAccountBilling() {
        val geminiLimit0Body = """
            {
              "error": {
                "code": 429,
                "message": "Quota exceeded for quota metric 'GenerateContent requests' and limit 'GenerateContent requests per minute' of service 'generativelanguage.googleapis.com' for consumer 'project_number:123'. limit: 0, model: gemini-2.5-pro. Check your plan and billing details.",
                "status": "RESOURCE_EXHAUSTED"
              }
            }
        """.trimIndent()

        val errKind = CompanyDiscoveryEngine.classifyError("gemini", 429, geminiLimit0Body, isListCall = false)
        assertEquals(com.example.domain.model.ErrorKind.MODEL_NOT_IN_TIER, errKind)
    }

    @Test
    fun testAnthropic404OnListCallIsEndpointNotFoundNotInvalidKey() {
        val anthropic404Body = """
            {
              "type": "error",
              "error": {
                "type": "not_found_error",
                "message": "The requested resource could not be found."
              }
            }
        """.trimIndent()

        val listErrKind = CompanyDiscoveryEngine.classifyError("anthropic", 404, anthropic404Body, isListCall = true)
        assertEquals(com.example.domain.model.ErrorKind.ENDPOINT_NOT_FOUND, listErrKind)

        // 404 on a chat execution call is MODEL_NOT_FOUND
        val chatErrKind = CompanyDiscoveryEngine.classifyError("anthropic", 404, anthropic404Body, isListCall = false)
        assertEquals(com.example.domain.model.ErrorKind.MODEL_NOT_FOUND, chatErrKind)
    }

    @Test
    fun testDeepSeek402InsufficientBalanceIsAccountNeedsCredit() {
        val deepSeek402Body = """
            {
              "error": {
                "message": "Insufficient Balance. Please top up your account balance at https://platform.deepseek.com.",
                "type": "insufficient_balance",
                "code": "insufficient_balance"
              }
            }
        """.trimIndent()

        val errKind = CompanyDiscoveryEngine.classifyError("deepseek", 402, deepSeek402Body, isListCall = false)
        assertEquals(com.example.domain.model.ErrorKind.ACCOUNT_NEEDS_CREDIT, errKind)
    }

    @Test
    fun testOpenAiInsufficientQuotaVsRateLimit() {
        val openAiQuotaBody = """
            {
              "error": {
                "message": "You exceeded your current quota, please check your plan and billing details.",
                "type": "insufficient_quota",
                "code": "insufficient_quota"
              }
            }
        """.trimIndent()

        val quotaErr = CompanyDiscoveryEngine.classifyError("openai", 429, openAiQuotaBody, isListCall = false)
        assertEquals(com.example.domain.model.ErrorKind.ACCOUNT_NEEDS_CREDIT, quotaErr)

        val openAiRpmBody = """
            {
              "error": {
                "message": "Rate limit reached for requests",
                "type": "requests",
                "code": "rate_limit_exceeded"
              }
            }
        """.trimIndent()

        val rpmErr = CompanyDiscoveryEngine.classifyError("openai", 429, openAiRpmBody, isListCall = false)
        assertEquals(com.example.domain.model.ErrorKind.RATE_LIMITED, rpmErr)
    }

    private fun generateMockCandles(basePrice: Double, count: Int): List<CandlePoint> {
        val now = System.currentTimeMillis()
        val step = 300_000L // 5m
        return (0 until count).map { i ->
            val p = basePrice + (i * 0.1)
            CandlePoint(
                timestamp = now - ((count - i) * step),
                open = p - 0.05,
                high = p + 0.2,
                low = p - 0.2,
                close = p,
                volume = 10000.0
            )
        }
    }
}
