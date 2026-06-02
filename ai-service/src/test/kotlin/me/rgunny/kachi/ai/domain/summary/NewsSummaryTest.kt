package me.rgunny.kachi.ai.domain.summary

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NewsSummary")
class NewsSummaryTest {

    @Test
    @DisplayName("요약 대상 뉴스 id 중복을 제거하고 제목과 본문을 정규화한다")
    fun create() {
        val newsId = UUID.fromString("018f0000-0000-7000-8000-000000000001")

        val summary = NewsSummary.create(
            keyword = AiKeyword.of("NVIDIA"),
            sourceNewsIds = listOf(newsId, newsId),
            sourceWindowHash = "hash",
            title = " NVIDIA 요약 ",
            content = " 실적 발표 요약 ",
            sentiment = NewsSummarySentiment.POSITIVE,
            provider = provider,
            model = model,
            promptVersion = promptVersion,
            tokenUsage = TokenUsage(inputTokens = 10, outputTokens = 20),
            createdAt = now
        )

        assertEquals(listOf(newsId), summary.sourceNewsIds)
        assertEquals("NVIDIA 요약", summary.title)
        assertEquals("실적 발표 요약", summary.content)
        assertEquals(30, summary.tokenUsage.totalTokens)
    }

    @Test
    @DisplayName("요약 대상 뉴스가 없으면 생성할 수 없다")
    fun rejectEmptySourceNewsIds() {
        assertFailsWith<IllegalArgumentException> {
            NewsSummary.create(
                keyword = AiKeyword.of("NVIDIA"),
                sourceNewsIds = emptyList(),
                sourceWindowHash = "hash",
                title = "요약",
                content = "본문",
                sentiment = NewsSummarySentiment.NEUTRAL,
                provider = provider,
                model = model,
                promptVersion = promptVersion,
                tokenUsage = TokenUsage(inputTokens = 0, outputTokens = 0),
                createdAt = now
            )
        }
    }

    private companion object {
        val provider = LlmProviderName.of("openai")
        val model = LlmModelName.of("gpt-4.1-mini")
        val promptVersion = PromptVersion.of("news-summary-v1")
        val now = Instant.parse("2026-06-02T00:00:00Z")
    }
}
