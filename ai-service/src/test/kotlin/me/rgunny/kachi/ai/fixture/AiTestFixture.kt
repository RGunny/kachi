package me.rgunny.kachi.ai.fixture

import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.dto.llm.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.dto.news.NewsArticle
import me.rgunny.kachi.ai.config.KeywordQuarantineProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.llm.LlmFailure
import me.rgunny.kachi.ai.domain.llm.LlmFailureCategory
import me.rgunny.kachi.ai.domain.llm.LlmFailureSource
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.domain.watermark.SummaryWatermark
import java.time.Clock
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

    val PROVIDER: LlmProviderName = LlmProviderName.of("openrouter")
    val MODEL: LlmModelName = LlmModelName.of("test-model")
    val NEWS_SUMMARY_PROMPT_VERSION: PromptVersion = PromptVersion.of("news-summary-v1")
    val KEYWORD_EXPANSION_PROMPT_VERSION: PromptVersion = PromptVersion.of("keyword-expansion-v1")
    val TOKEN_USAGE: TokenUsage = TokenUsage(inputTokens = 10, outputTokens = 20)

    val NEWS_ID: UUID = UUID.fromString("018f0000-0000-7000-8000-000000000001")

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
            publishedAt = Instant.parse("2026-06-02T00:00:00Z"),
            collectedAt = Instant.parse("2026-06-02T00:01:00Z"),
            matchedKeywords = listOf("NVIDIA")
        )
    }

    fun newsSummary(
        keyword: AiKeyword = keyword(),
        sourceNewsIds: List<UUID> = listOf(NEWS_ID),
        newsHash: String = "news-hash",
        createdAt: Instant = NOW
    ): NewsSummary {
        return NewsSummary.create(
            keyword = keyword,
            sourceNewsIds = sourceNewsIds,
            newsHash = newsHash,
            title = "${keyword.value} 기존 요약",
            content = "기존 요약 본문",
            sentiment = NewsSummarySentiment.NEUTRAL,
            provider = PROVIDER,
            model = MODEL,
            promptVersion = NEWS_SUMMARY_PROMPT_VERSION,
            tokenUsage = TOKEN_USAGE,
            createdAt = createdAt
        )
    }

    fun newsSummaryMetadata(): LlmGenerationMetadata {
        return LlmGenerationMetadata(
            provider = PROVIDER,
            model = MODEL,
            promptVersion = NEWS_SUMMARY_PROMPT_VERSION,
            tokenUsage = TOKEN_USAGE
        )
    }

    fun keywordExpansionMetadata(): LlmGenerationMetadata {
        return LlmGenerationMetadata(
            provider = PROVIDER,
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
     * 분류된 LLM 실패를 만든다. 격리 카운트와 조기 중단 판단이 category에 따라 갈리므로 테스트가 직접 지정한다.
     */
    fun llmFailure(
        category: LlmFailureCategory,
        source: LlmFailureSource = LlmFailureSource.PROVIDER,
        statusCode: Int? = null,
        retryAfterMillis: Long? = null
    ): LlmFailure {
        return LlmFailure(
            code = "TEST_${category.name}",
            message = "test ${category.name} failure",
            source = source,
            category = category,
            provider = PROVIDER,
            statusCode = statusCode,
            retryAfterMillis = retryAfterMillis
        )
    }

    fun llmProviderException(
        category: LlmFailureCategory,
        source: LlmFailureSource = LlmFailureSource.PROVIDER
    ): LlmProviderException {
        return LlmProviderException(llmFailure(category = category, source = source))
    }

    fun quarantineProperties(
        failureThreshold: Int = DEFAULT_QUARANTINE_FAILURE_THRESHOLD
    ): KeywordQuarantineProperties {
        return KeywordQuarantineProperties(failureThreshold = failureThreshold)
    }

    const val DEFAULT_QUARANTINE_FAILURE_THRESHOLD = 3
}
