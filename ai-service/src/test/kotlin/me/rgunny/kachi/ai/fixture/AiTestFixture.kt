package me.rgunny.kachi.ai.fixture

import me.rgunny.kachi.ai.adapter.outbound.lock.InMemoryExecutionLockAdapter
import me.rgunny.kachi.ai.application.port.outbound.lock.ExecutionLockPort
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.application.port.outbound.outbox.model.KeywordQuarantinedEvent
import me.rgunny.kachi.ai.application.port.outbound.outbox.model.SummaryCreatedEvent
import me.rgunny.kachi.ai.application.service.outbox.AiOutboxRelayPolicy
import me.rgunny.kachi.ai.config.AiEventsProperties
import me.rgunny.kachi.ai.config.AiOutboxRelayProperties
import me.rgunny.kachi.ai.config.AiOutboxRetryProperties
import me.rgunny.kachi.ai.config.KeywordQuarantineProperties
import me.rgunny.kachi.ai.config.LlmCircuitBreakerProperties
import me.rgunny.kachi.ai.config.LlmCooldownProperties
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmHold
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmProbeResult
import me.rgunny.kachi.ai.config.LlmHoldProperties
import me.rgunny.kachi.ai.config.LlmProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import me.rgunny.kachi.ai.domain.outbox.AiOutboxClaim
import me.rgunny.kachi.ai.domain.outbox.AiOutboxId
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.llm.LlmFailure
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmBilling
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.LlmUse
import me.rgunny.kachi.ai.domain.llm.KeywordExpansionPrompt
import me.rgunny.kachi.ai.domain.llm.NewsSummaryPrompt
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.outbox.AiOutboxEventType
import me.rgunny.kachi.ai.domain.outbox.AiOutboxRetryPolicy
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.domain.watermark.SummaryWatermark
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * ai-service 테스트가 공유하는 고정값과 도메인 픽스처.
 *
 * 여러 테스트가 같은 provider/model/prompt version을 기대하므로 한곳에서 관리한다.
 */
object AiTestFixture {
    val NOW: Instant = Instant.parse("2026-06-03T00:00:00Z")
    val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

    /**
     * 이 인스턴스 안에서만 유효한 실행 lock.
     *
     * 테스트마다 새로 만들어 앞선 테스트가 쥔 lock이 다음 테스트에 남지 않게 한다.
     */
    fun executionLock(): ExecutionLockPort = InMemoryExecutionLockAdapter(CLOCK)

    /** 후보로 쓰는 모델 상수. 가드·라우터 테스트가 식별자로 쓴다. */
    val LLM_MODEL: LlmModel = LlmModel.GROQ_QWEN3_27B
    val DEFAULT_HOLD_REPROBE_AFTER: Duration = Duration.ofHours(1)
    val PROVIDER: LlmProvider = LLM_MODEL.provider

    /** 응답이 보고한 모델 이름. 요청한 code와 같지 않아도 된다는 것을 드러내려고 다른 값을 쓴다. */
    const val MODEL: String = "test-model"

    /** 요청에 실은 모델 code. */
    val REQUESTED_MODEL: String = LLM_MODEL.code
    val NEWS_SUMMARY_PROMPT_VERSION: PromptVersion = NewsSummaryPrompt.version
    val KEYWORD_EXPANSION_PROMPT_VERSION: PromptVersion = KeywordExpansionPrompt.version
    val TOKEN_USAGE: TokenUsage = TokenUsage(inputTokens = 10, outputTokens = 20)

    val NEWS_ID: UUID = UUID.fromString("018f0000-0000-7000-8000-000000000001")

    const val OUTBOX_EVENT_KEY: String = "018f0000-0000-7000-8000-000000000009"
    const val OUTBOX_PAYLOAD: String = """{"schemaVersion":1,"keyword":"NVIDIA"}"""
    const val RELAY_PUBLISHER_ID: String = "relay-test"

    fun keyword(value: String = "NVIDIA"): AiKeyword {
        return AiKeyword.of(value)
    }

    fun newsArticle(
        id: UUID = NEWS_ID,
        title: String = "NVIDIA news"
    ): NewsArticle {
        return NewsArticle(
            id = id,
            source = "GOOGLE",
            title = title,
            url = "https://news.example.com/nvidia",
            publishedAt = NOW.minus(Duration.ofDays(1)),
            collectedAt = NOW.minus(Duration.ofDays(1)).plusSeconds(60),
            matchedKeywords = listOf("NVIDIA")
        )
    }

    fun outbox(
        eventType: AiOutboxEventType = AiOutboxEventType.SUMMARY_CREATED,
        eventKey: String = OUTBOX_EVENT_KEY,
        partitionKey: String = "NVIDIA",
        payload: String = OUTBOX_PAYLOAD,
        now: Instant = NOW
    ): AiOutbox {
        return AiOutbox.create(
            eventType = eventType,
            eventKey = eventKey,
            partitionKey = partitionKey,
            payload = payload,
            now = now
        )
    }

    /**
     * 저장소에 있던 것처럼 상태·재시도 횟수·소유권을 지정해 복원한 outbox.
     *
     * [outbox]는 생성 직후만 만들 수 있으므로 발행 중이거나 실패가 쌓인 행은 여기서 만든다.
     * 기본값은 [outbox]와 같은 PENDING 행이다.
     */
    fun restoredOutbox(
        id: AiOutboxId = AiOutboxId.newId(),
        eventType: AiOutboxEventType = AiOutboxEventType.SUMMARY_CREATED,
        eventKey: String = OUTBOX_EVENT_KEY,
        partitionKey: String = "NVIDIA",
        payload: String = OUTBOX_PAYLOAD,
        status: AiOutboxStatus = AiOutboxStatus.PENDING,
        retryCount: Int = 0,
        nextRetryAt: Instant = NOW,
        lastError: String? = null,
        publishedAt: Instant? = null,
        claim: AiOutboxClaim? = null,
        createdAt: Instant = NOW,
        updatedAt: Instant = createdAt
    ): AiOutbox {
        return AiOutbox.restore(
            id = id,
            eventType = eventType,
            eventKey = eventKey,
            partitionKey = partitionKey,
            payload = payload,
            status = status,
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
            lastError = lastError,
            publishedAt = publishedAt,
            claim = claim,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    /**
     * 재시도 정책. 분산값은 기본으로 끈다. 켜 두면 다음 차례 시각을 단언할 수 없다.
     */
    fun retryPolicy(
        maxAttempts: Int = 5,
        baseDelay: Duration = Duration.ofSeconds(1),
        maxDelay: Duration = Duration.ofMinutes(1),
        multiplier: Double = 2.0,
        jitterRatio: Double = 0.0
    ): AiOutboxRetryPolicy {
        return AiOutboxRetryPolicy(
            maxAttempts = maxAttempts,
            baseDelay = baseDelay,
            maxDelay = maxDelay,
            multiplier = multiplier,
            jitterRatio = jitterRatio
        )
    }

    fun relayPolicy(
        batchSize: Int = 10,
        publisherId: String = RELAY_PUBLISHER_ID,
        retryPolicy: AiOutboxRetryPolicy = retryPolicy(),
        publishingVisibilityTimeout: Duration = Duration.ofSeconds(60)
    ): AiOutboxRelayPolicy {
        return AiOutboxRelayPolicy(
            batchSize = batchSize,
            publisherId = publisherId,
            retryPolicy = retryPolicy,
            publishingVisibilityTimeout = publishingVisibilityTimeout
        )
    }

    fun relayProperties(
        enabled: Boolean = true,
        publisherId: String = RELAY_PUBLISHER_ID,
        fixedDelay: Duration = Duration.ofSeconds(5),
        initialDelay: Duration = Duration.ofSeconds(15),
        batchSize: Int = 50,
        publishingVisibilityTimeout: Duration = Duration.ofSeconds(60),
        retry: AiOutboxRetryProperties = retryProperties()
    ): AiOutboxRelayProperties {
        return AiOutboxRelayProperties(
            enabled = enabled,
            publisherId = publisherId,
            fixedDelay = fixedDelay,
            initialDelay = initialDelay,
            batchSize = batchSize,
            publishingVisibilityTimeout = publishingVisibilityTimeout,
            retry = retry
        )
    }

    const val EVENT_TOPIC_SUMMARY_CREATED: String = "ai.summary.created"
    const val EVENT_TOPIC_KEYWORD_QUARANTINED: String = "ai.keyword.quarantined"

    fun eventsProperties(
        enabled: Boolean = true,
        summaryCreatedTopic: String = EVENT_TOPIC_SUMMARY_CREATED,
        keywordQuarantinedTopic: String = EVENT_TOPIC_KEYWORD_QUARANTINED
    ): AiEventsProperties {
        return AiEventsProperties(
            enabled = enabled,
            topics = AiEventsProperties.Topics(
                summaryCreated = summaryCreatedTopic,
                keywordQuarantined = keywordQuarantinedTopic
            )
        )
    }

    fun retryProperties(
        maxAttempts: Int = 5,
        baseDelay: Duration = Duration.ofSeconds(1),
        maxDelay: Duration = Duration.ofMinutes(1),
        multiplier: Double = 2.0
    ): AiOutboxRetryProperties {
        return AiOutboxRetryProperties(
            maxAttempts = maxAttempts,
            baseDelay = baseDelay,
            maxDelay = maxDelay,
            multiplier = multiplier
        )
    }

    fun summaryCreatedEvent(summary: NewsSummary = newsSummary()): SummaryCreatedEvent {
        return SummaryCreatedEvent.from(summary)
    }

    /**
     * 격리 상태 기록에서 만든 격리 이벤트. 기본값은 임계치에 막 도달한 기록이다.
     */
    fun keywordQuarantinedEvent(
        quarantine: KeywordQuarantine = quarantine(consecutiveFailures = DEFAULT_QUARANTINE_FAILURE_THRESHOLD)
    ): KeywordQuarantinedEvent {
        return KeywordQuarantinedEvent.from(quarantine)
    }

    fun keywordExpansion(
        keyword: AiKeyword = keyword(),
        expandedKeywords: List<String> = listOf("AI 반도체", "GPU"),
        provider: LlmProvider = PROVIDER,
        model: String = MODEL,
        requestedModel: String = REQUESTED_MODEL,
        createdAt: Instant = NOW
    ): KeywordExpansion {
        return KeywordExpansion.create(
            keyword = keyword,
            expandedKeywords = expandedKeywords.map(ExpandedKeyword::of),
            provider = provider,
            model = model,
            requestedModel = requestedModel,
            promptVersion = KEYWORD_EXPANSION_PROMPT_VERSION,
            createdAt = createdAt
        )
    }

    fun newsSummary(
        keyword: AiKeyword = keyword(),
        sourceNewsIds: List<UUID> = listOf(NEWS_ID),
        newsHash: String = "news-hash",
        provider: LlmProvider = PROVIDER,
        model: String = MODEL,
        requestedModel: String = REQUESTED_MODEL,
        createdAt: Instant = NOW
    ): NewsSummary {
        return NewsSummary.create(
            keyword = keyword,
            sourceNewsIds = sourceNewsIds,
            newsHash = newsHash,
            title = "${keyword.value} 기존 요약",
            content = "기존 요약 본문",
            sentiment = NewsSummarySentiment.NEUTRAL,
            provider = provider,
            model = model,
            requestedModel = requestedModel,
            promptVersion = NEWS_SUMMARY_PROMPT_VERSION,
            tokenUsage = TOKEN_USAGE,
            createdAt = createdAt
        )
    }

    fun newsSummaryMetadata(): LlmGenerationMetadata {
        return LlmGenerationMetadata(
            provider = PROVIDER,
            requestedModel = REQUESTED_MODEL,
            model = MODEL,
            promptVersion = NEWS_SUMMARY_PROMPT_VERSION,
            tokenUsage = TOKEN_USAGE
        )
    }

    fun keywordExpansionMetadata(): LlmGenerationMetadata {
        return LlmGenerationMetadata(
            provider = PROVIDER,
            requestedModel = REQUESTED_MODEL,
            model = MODEL,
            promptVersion = KEYWORD_EXPANSION_PROMPT_VERSION,
            tokenUsage = TOKEN_USAGE
        )
    }

    fun completedRun(
        targetType: AiRunTargetType,
        requestedKeywords: Int,
        succeededCount: Int = requestedKeywords,
        failureCount: Int = 0,
        failureReason: AiFailureReason? = null,
        startedAt: Instant = NOW,
        finishedAt: Instant = NOW.plusSeconds(5),
        windowFrom: Instant? = null,
        windowTo: Instant? = null,
        watermarkAdvanced: Boolean = false
    ): AiRun {
        return AiRun.start(
            targetType = targetType,
            requestedKeywords = requestedKeywords,
            startedAt = startedAt,
            windowFrom = windowFrom,
            windowTo = windowTo
        ).complete(
            succeededCount = succeededCount,
            failureCount = failureCount,
            failureReason = failureReason,
            provider = null,
            model = null,
            promptVersion = null,
            finishedAt = finishedAt,
            watermarkAdvanced = watermarkAdvanced
        )
    }

    fun watermark(
        position: Instant,
        targetType: AiRunTargetType = AiRunTargetType.NEWS_SUMMARY,
        updatedAt: Instant = position
    ): SummaryWatermark {
        return SummaryWatermark.initial(
            targetType = targetType,
            position = position,
            updatedAt = updatedAt
        )
    }

    /**
     * 연속 실패가 [consecutiveFailures]회 누적된 격리 기록을 만든다.
     * [failureThreshold]에 도달하면 격리 상태가 된다.
     */
    fun quarantine(
        keyword: AiKeyword = keyword(),
        consecutiveFailures: Int,
        failureThreshold: Int = DEFAULT_QUARANTINE_FAILURE_THRESHOLD,
        targetType: AiRunTargetType = AiRunTargetType.NEWS_SUMMARY,
        updatedAt: Instant = NOW
    ): KeywordQuarantine {
        return (1..consecutiveFailures).fold(
            KeywordQuarantine.track(targetType = targetType, keyword = keyword, updatedAt = updatedAt)
        ) { quarantine, _ ->
            quarantine.recordFailure(
                reason = AiFailureReason.UNKNOWN,
                failureThreshold = failureThreshold,
                updatedAt = updatedAt
            )
        }
    }

    /**
     * 분류된 LLM 실패를 만든다. 격리 카운트와 조기 중단 판단이 코드에 따라 갈리므로 테스트가 직접 지정한다.
     */
    fun llmFailure(
        code: LlmFailureCode,
        statusCode: Int? = null,
        retryAfterMillis: Long? = null
    ): LlmFailure {
        return LlmFailure(
            code = code,
            provider = PROVIDER,
            message = "test ${code.name} failure",
            statusCode = statusCode,
            retryAfterMillis = retryAfterMillis
        )
    }

    fun llmProviderException(code: LlmFailureCode): LlmProviderException {
        return LlmProviderException(llmFailure(code))
    }

    /**
     * 후보 여럿을 거친 뒤의 실패. 대표 실패는 마지막 시도이고 [codes]가 실제 호출 순서다. 격리 카운트가 전 후보 합의를 보는지 확인하는 데 쓴다.
     */
    fun llmProviderException(vararg codes: LlmFailureCode): LlmProviderException {
        require(codes.isNotEmpty())
        val attempts = codes.map { llmFailure(it) }

        return LlmProviderException(failure = attempts.last(), attempts = attempts)
    }

    fun holdProperties(reprobeAfter: Duration = DEFAULT_HOLD_REPROBE_AFTER): LlmHoldProperties {
        return LlmHoldProperties(reprobeAfter = reprobeAfter)
    }

    fun llmModelStatus(
        model: LlmModel = LLM_MODEL,
        billing: LlmBilling = LlmBilling.FREE_TIER,
        circuitBreakerState: String = "CLOSED",
        cooldownUntil: Instant? = null,
        hold: LlmHold? = null
    ): LlmModelStatus {
        return LlmModelStatus(
            model = model,
            billing = billing,
            circuitBreakerState = circuitBreakerState,
            cooldownUntil = cooldownUntil,
            hold = hold,
            failureRate = 50f,
            slowCallRate = -1f,
            bufferedCalls = 4,
            successfulCalls = 2,
            failedCalls = 2,
            notPermittedCalls = 3
        )
    }

    fun llmProbeResult(
        model: LlmModel = LLM_MODEL,
        billing: LlmBilling = LlmBilling.FREE_TIER,
        latency: Duration = Duration.ofMillis(1234)
    ): LlmProbeResult {
        return LlmProbeResult(
            model = model,
            billing = billing,
            metadata = keywordExpansionMetadata(),
            latency = latency,
            expandedKeywords = listOf(ExpandedKeyword.of("AI 반도체"), ExpandedKeyword.of("GPU"))
        )
    }

    /** 429 응답. [retryAfterMillis]가 null이면 Retry-After 헤더가 없는 응답이다. */
    fun rateLimitedException(retryAfterMillis: Long?): LlmProviderException {
        return LlmProviderException(
            llmFailure(
                code = LlmFailureCode.LLM_RATE_LIMITED,
                statusCode = 429,
                retryAfterMillis = retryAfterMillis
            )
        )
    }

    fun quarantineProperties(
        failureThreshold: Int = DEFAULT_QUARANTINE_FAILURE_THRESHOLD
    ): KeywordQuarantineProperties {
        return KeywordQuarantineProperties(failureThreshold = failureThreshold)
    }

    fun circuitBreakerProperties(
        slidingWindowSize: Int = 6,
        minimumNumberOfCalls: Int = 3,
        slowCallRateThreshold: Float = 80f
    ): LlmCircuitBreakerProperties {
        return LlmCircuitBreakerProperties(
            slidingWindowSize = slidingWindowSize,
            minimumNumberOfCalls = minimumNumberOfCalls,
            failureRateThreshold = 50f,
            slowCallRateThreshold = slowCallRateThreshold,
            waitDurationInOpenState = Duration.ofSeconds(60),
            permittedNumberOfCallsInHalfOpenState = 2
        )
    }

    fun providerProperties(
        billing: LlmBilling = LlmBilling.FREE_TIER,
        apiKey: String = "test-key",
        baseUrl: String = "https://llm.example.com/v1",
        connectTimeout: Duration = Duration.ofSeconds(2)
    ): LlmProperties.ProviderProperties {
        return LlmProperties.ProviderProperties(
            baseUrl = baseUrl,
            apiKey = apiKey,
            billing = billing,
            connectTimeout = connectTimeout
        )
    }

    fun modelProperties(
        timeout: Duration = Duration.ofSeconds(10),
        slowAfter: Duration = Duration.ofSeconds(8)
    ): LlmProperties.ModelProperties {
        return LlmProperties.ModelProperties(timeout = timeout, slowAfter = slowAfter)
    }

    /**
     * 클라우드 둘을 후보로 두고 Ollama는 정의만 있는 설정. 참조되지 않은 항목이 걸러지는지 보는 데 쓴다.
     */
    fun llmProperties(
        providers: Map<LlmProvider, LlmProperties.ProviderProperties> = mapOf(
            LlmProvider.GROQ to providerProperties(),
            LlmProvider.MISTRAL to providerProperties(),
            LlmProvider.OLLAMA to providerProperties(billing = LlmBilling.SELF_HOSTED, apiKey = "")
        ),
        models: Map<LlmModel, LlmProperties.ModelProperties> = mapOf(
            LlmModel.GROQ_QWEN3_27B to modelProperties(),
            LlmModel.MISTRAL_SMALL_2603 to modelProperties(timeout = Duration.ofSeconds(20), slowAfter = Duration.ofSeconds(15)),
            LlmModel.OLLAMA_QWEN3_27B to modelProperties(timeout = Duration.ofSeconds(150), slowAfter = Duration.ofSeconds(120))
        ),
        uses: Map<LlmUse, LlmProperties.UseProperties> = mapOf(
            LlmUse.NEWS_SUMMARY to LlmProperties.UseProperties(listOf(LlmModel.GROQ_QWEN3_27B, LlmModel.MISTRAL_SMALL_2603)),
            LlmUse.KEYWORD_EXPANSION to LlmProperties.UseProperties(listOf(LlmModel.MISTRAL_SMALL_2603))
        )
    ): LlmProperties {
        return LlmProperties(
            providers = providers,
            models = models,
            uses = uses,
            guard = LlmProperties.GuardProperties(
                circuitBreaker = circuitBreakerProperties(),
                cooldown = LlmCooldownProperties(default = Duration.ofSeconds(60), max = Duration.ofMinutes(10)),
                hold = holdProperties()
            )
        )
    }

    const val DEFAULT_QUARANTINE_FAILURE_THRESHOLD = 3
}
