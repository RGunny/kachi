package me.rgunny.kachi.ai.application.service

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsCommand
import me.rgunny.kachi.ai.application.port.out.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.application.port.out.llm.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.out.llm.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.out.llm.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.out.news.NewsArticle
import me.rgunny.kachi.ai.application.port.out.persistence.AiRunPersistencePort
import me.rgunny.kachi.ai.application.port.out.persistence.KeywordExpansionPersistencePort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunId
import me.rgunny.kachi.ai.domain.run.AiRunStatus
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

@DisplayName("ExpandKeywordsService")
class ExpandKeywordsServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-03T00:00:00Z"), ZoneOffset.UTC)
    private val keywordReader = FakeKeywordReaderPort()
    private val llmProvider = FakeLlmProviderPort()
    private val keywordExpansionPersistence = FakeKeywordExpansionPersistencePort()
    private val aiRunPersistence = FakeAiRunPersistencePort()

    @Test
    @DisplayName("키워드를 확장하고 실행 기록을 성공 상태로 완료한다")
    fun expandKeywords() = runBlocking {
        val service = service()

        val result = service.expand(
            ExpandKeywordsCommand(
                keywords = listOf(AiKeyword.of("NVIDIA")),
                maxExpansionsPerKeyword = 3
            )
        )

        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertEquals(1, result.requestedKeywords)
        assertEquals(1, result.succeededCount)
        assertEquals(0, result.failureCount)
        assertEquals(1, keywordExpansionPersistence.savedExpansions.size)
        assertEquals(listOf("AI 반도체", "GPU"), keywordExpansionPersistence.savedExpansions.first().expandedKeywords.map { it.value })
        assertEquals(2, aiRunPersistence.savedRuns.size)
        assertEquals(LlmProviderName.of("openrouter"), aiRunPersistence.savedRuns.last().provider)
        assertEquals(LlmModelName.of("test-model"), aiRunPersistence.savedRuns.last().model)
        assertEquals(PromptVersion.of("keyword-expansion-v1"), aiRunPersistence.savedRuns.last().promptVersion)
    }

    @Test
    @DisplayName("command에 키워드가 없으면 활성 키워드를 읽어 확장한다")
    fun readActiveKeywordsWhenCommandKeywordsAreEmpty() = runBlocking {
        keywordReader.keywords = listOf(AiKeyword.of("NVIDIA"))
        val service = service()

        val result = service.expand(ExpandKeywordsCommand(keywords = emptyList()))

        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertEquals(1, keywordReader.readCount)
        assertEquals(1, result.requestedKeywords)
    }

    @Test
    @DisplayName("일부 키워드 확장에 실패하면 부분 실패 상태로 완료한다")
    fun completeAsPartiallyFailed() = runBlocking {
        llmProvider.failedKeywords = setOf(AiKeyword.of("TESLA"))
        val service = service()

        val result = service.expand(
            ExpandKeywordsCommand(
                keywords = listOf(AiKeyword.of("NVIDIA"), AiKeyword.of("TESLA"))
            )
        )

        assertEquals(AiRunStatus.PARTIALLY_FAILED, result.status)
        assertEquals(1, result.succeededCount)
        assertEquals(1, result.failureCount)
        assertEquals(1, keywordExpansionPersistence.savedExpansions.size)
    }

    @Test
    @DisplayName("처리할 키워드가 없으면 실패 상태로 완료한다")
    fun completeAsFailedWhenKeywordsAreEmpty() = runBlocking {
        val service = service()

        val result = service.expand(ExpandKeywordsCommand(keywords = emptyList()))

        assertEquals(AiRunStatus.FAILED, result.status)
        assertEquals(0, result.requestedKeywords)
        assertEquals(0, result.succeededCount)
        assertEquals(0, result.failureCount)
    }

    private fun service(): ExpandKeywordsService {
        return ExpandKeywordsService(
            keywordReaderPort = keywordReader,
            llmProviderPort = llmProvider,
            keywordExpansionPersistencePort = keywordExpansionPersistence,
            aiRunPersistencePort = aiRunPersistence,
            clock = clock
        )
    }

    private class FakeKeywordReaderPort : KeywordReaderPort {
        var keywords: List<AiKeyword> = emptyList()
        var readCount: Int = 0

        override suspend fun findActiveKeywords(): List<AiKeyword> {
            readCount += 1
            return keywords
        }
    }

    private class FakeLlmProviderPort : LlmProviderPort {
        var failedKeywords: Set<AiKeyword> = emptySet()

        override suspend fun expandKeyword(
            keyword: AiKeyword,
            maxExpansions: Int
        ): LlmKeywordExpansionResult {
            if (keyword in failedKeywords) {
                throw IllegalStateException("LLM failure")
            }

            return LlmKeywordExpansionResult(
                expandedKeywords = listOf(
                    ExpandedKeyword.of("AI 반도체"),
                    ExpandedKeyword.of("GPU")
                ).take(maxExpansions),
                metadata = LlmGenerationMetadata(
                    provider = LlmProviderName.of("openrouter"),
                    model = LlmModelName.of("test-model"),
                    promptVersion = PromptVersion.of("keyword-expansion-v1"),
                    tokenUsage = TokenUsage(inputTokens = 10, outputTokens = 20)
                )
            )
        }

        override suspend fun summarizeNews(
            keyword: AiKeyword,
            articles: List<NewsArticle>
        ): LlmNewsSummaryResult {
            return LlmNewsSummaryResult(
                title = "요약",
                content = "본문",
                sentiment = NewsSummarySentiment.UNKNOWN,
                metadata = LlmGenerationMetadata(
                    provider = LlmProviderName.of("openrouter"),
                    model = LlmModelName.of("test-model"),
                    promptVersion = PromptVersion.of("news-summary-v1"),
                    tokenUsage = TokenUsage(inputTokens = 0, outputTokens = 0)
                )
            )
        }
    }

    private class FakeKeywordExpansionPersistencePort : KeywordExpansionPersistencePort {
        val savedExpansions: MutableList<KeywordExpansion> = mutableListOf()

        override suspend fun save(keywordExpansion: KeywordExpansion): KeywordExpansion {
            savedExpansions.add(keywordExpansion)
            return keywordExpansion
        }
    }

    private class FakeAiRunPersistencePort : AiRunPersistencePort {
        val savedRuns: MutableList<AiRun> = mutableListOf()

        override suspend fun findById(id: AiRunId): AiRun? {
            return savedRuns.firstOrNull { it.id == id }
        }

        override suspend fun save(aiRun: AiRun): AiRun {
            savedRuns.add(aiRun)
            return aiRun
        }
    }
}
