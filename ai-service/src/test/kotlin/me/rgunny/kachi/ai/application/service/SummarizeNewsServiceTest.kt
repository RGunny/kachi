package me.rgunny.kachi.ai.application.service

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.out.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.application.port.out.llm.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.out.llm.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.out.llm.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.out.llm.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.out.llm.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.out.news.NewsArticle
import me.rgunny.kachi.ai.application.port.out.news.NewsReaderPort
import me.rgunny.kachi.ai.application.port.out.persistence.AiRunPersistencePort
import me.rgunny.kachi.ai.application.port.out.persistence.NewsSummaryPersistencePort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunId
import me.rgunny.kachi.ai.domain.run.AiRunStatus
import me.rgunny.kachi.ai.domain.summary.NewsHash
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("SummarizeNewsService")
class SummarizeNewsServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-03T00:00:00Z"), ZoneOffset.UTC)
    private val keywordReader = FakeKeywordReaderPort()
    private val newsReader = FakeNewsReaderPort()
    private val llmProvider = FakeLlmProviderPort()
    private val newsSummaryPersistence = FakeNewsSummaryPersistencePort()
    private val aiRunPersistence = FakeAiRunPersistencePort()

    @Test
    @DisplayName("키워드별 뉴스를 요약하고 실행 기록을 성공 상태로 완료한다")
    fun summarizeNews() = runBlocking {
        newsReader.articlesByKeyword = mapOf(AiKeyword.of("NVIDIA") to listOf(newsArticle()))
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(
                keywords = listOf(AiKeyword.of("NVIDIA")),
                from = Instant.parse("2026-06-02T00:00:00Z"),
                to = Instant.parse("2026-06-03T00:00:00Z"),
                maxArticlesPerKeyword = 20
            )
        )

        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertEquals(1, result.requestedKeywords)
        assertEquals(1, result.succeededCount)
        assertEquals(0, result.failureCount)
        assertEquals(1, result.summaries.size)
        assertEquals("NVIDIA 요약", result.summaries.first().title)
        assertEquals(false, result.summaries.first().reused)
        assertEquals(1, newsSummaryPersistence.savedSummaries.size)
        assertEquals("NVIDIA 요약", newsSummaryPersistence.savedSummaries.first().title)
        assertEquals(2, aiRunPersistence.savedRuns.size)
        assertEquals(LlmProviderName.of("openrouter"), aiRunPersistence.savedRuns.last().provider)
        assertEquals(LlmModelName.of("test-model"), aiRunPersistence.savedRuns.last().model)
        assertEquals(PromptVersion.of("news-summary-v1"), aiRunPersistence.savedRuns.last().promptVersion)
    }

    @Test
    @DisplayName("같은 입력의 기존 요약이 있으면 LLM을 호출하지 않고 성공 처리한다")
    fun reuseExistingSummaryBeforeLlmCall() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        val article = newsArticle()
        newsReader.articlesByKeyword = mapOf(keyword to listOf(article))
        newsSummaryPersistence.existingSummaries += newsSummary(
            keyword = keyword,
            sourceNewsIds = listOf(article.id),
            newsHash = NewsHash.calculate(
                keyword = keyword,
                from = Instant.parse("2026-06-02T00:00:00Z"),
                to = Instant.parse("2026-06-03T00:00:00Z"),
                sourceNewsIds = listOf(article.id)
            )
        )
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(
                keywords = listOf(keyword),
                from = Instant.parse("2026-06-02T00:00:00Z"),
                to = Instant.parse("2026-06-03T00:00:00Z")
            )
        )

        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertEquals(1, result.summaries.size)
        assertEquals("NVIDIA 기존 요약", result.summaries.first().title)
        assertEquals(true, result.summaries.first().reused)
        assertEquals(0, llmProvider.summarizeCallCount)
        assertEquals(0, newsSummaryPersistence.savedSummaries.size)
    }

    @Test
    @DisplayName("기존 요약이 없으면 LLM 호출 후 저장한다")
    fun callLlmAndSaveWhenSummaryDoesNotExist() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        newsReader.articlesByKeyword = mapOf(keyword to listOf(newsArticle()))
        val service = service()

        val result = service.summarize(SummarizeNewsCommand(keywords = listOf(keyword), from = null, to = null))

        assertEquals(1, result.summaries.size)
        assertEquals(false, result.summaries.first().reused)
        assertEquals(1, llmProvider.summarizeCallCount)
        assertEquals(1, newsSummaryPersistence.savedSummaries.size)
    }

    @Test
    @DisplayName("저장 중 중복이 발생하면 기존 요약을 반환해 idempotent하게 처리한다")
    fun handleDuplicateOnSaveIdempotently() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        val article = newsArticle()
        val newsHash = NewsHash.calculate(
            keyword = keyword,
            from = null,
            to = null,
            sourceNewsIds = listOf(article.id)
        )
        newsReader.articlesByKeyword = mapOf(keyword to listOf(article))
        newsSummaryPersistence.duplicateOnSave = true
        newsSummaryPersistence.existingAfterDuplicate = newsSummary(
            keyword = keyword,
            sourceNewsIds = listOf(article.id),
            newsHash = newsHash
        )
        val service = service()

        val result = service.summarize(SummarizeNewsCommand(keywords = listOf(keyword), from = null, to = null))

        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertEquals(1, result.summaries.size)
        assertEquals(true, result.summaries.first().reused)
        assertEquals(1, llmProvider.summarizeCallCount)
        assertEquals(1, newsSummaryPersistence.saveOrFindExistingCallCount)
    }

    @Test
    @DisplayName("command에 키워드가 없으면 활성 키워드를 읽어 요약한다")
    fun readActiveKeywordsWhenCommandKeywordsAreEmpty() = runBlocking {
        keywordReader.keywords = listOf(AiKeyword.of("NVIDIA"))
        newsReader.articlesByKeyword = mapOf(AiKeyword.of("NVIDIA") to listOf(newsArticle()))
        val service = service()

        val result = service.summarize(SummarizeNewsCommand(keywords = emptyList(), from = null, to = null))

        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertEquals(1, keywordReader.readCount)
        assertEquals(1, result.requestedKeywords)
    }

    @Test
    @DisplayName("요약 대상 뉴스가 없으면 실패 상태로 완료한다")
    fun completeAsFailedWhenArticlesAreEmpty() = runBlocking {
        newsReader.articlesByKeyword = mapOf(AiKeyword.of("NVIDIA") to emptyList())
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(
                keywords = listOf(AiKeyword.of("NVIDIA")),
                from = null,
                to = null
            )
        )

        assertEquals(AiRunStatus.FAILED, result.status)
        assertEquals(0, result.succeededCount)
        assertEquals(1, result.failureCount)
        assertEquals(AiFailureReason.EMPTY_INPUT, aiRunPersistence.savedRuns.last().failureReason)
        assertEquals(0, newsSummaryPersistence.savedSummaries.size)
    }

    private fun service(): SummarizeNewsService {
        return SummarizeNewsService(
            keywordReaderPort = keywordReader,
            newsReaderPort = newsReader,
            llmProviderPort = llmProvider,
            newsSummaryPersistencePort = newsSummaryPersistence,
            aiRunPersistencePort = aiRunPersistence,
            clock = clock
        )
    }

    private fun newsArticle(): NewsArticle {
        return NewsArticle(
            id = UUID.fromString("018f0000-0000-7000-8000-000000000001"),
            source = "GOOGLE",
            title = "NVIDIA news",
            url = "https://news.example.com/nvidia",
            publishedAt = Instant.parse("2026-06-02T00:00:00Z"),
            collectedAt = Instant.parse("2026-06-02T00:01:00Z"),
            matchedKeywords = listOf("NVIDIA")
        )
    }

    private fun newsSummary(
        keyword: AiKeyword = AiKeyword.of("NVIDIA"),
        sourceNewsIds: List<UUID> = listOf(UUID.fromString("018f0000-0000-7000-8000-000000000001")),
        newsHash: String = "news-hash"
    ): NewsSummary {
        return NewsSummary.create(
            keyword = keyword,
            sourceNewsIds = sourceNewsIds,
            newsHash = newsHash,
            title = "${keyword.value} 기존 요약",
            content = "기존 요약 본문",
            sentiment = NewsSummarySentiment.NEUTRAL,
            provider = LlmProviderName.of("openrouter"),
            model = LlmModelName.of("test-model"),
            promptVersion = PromptVersion.of("news-summary-v1"),
            tokenUsage = TokenUsage(inputTokens = 10, outputTokens = 20),
            createdAt = clock.instant()
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

    private class FakeNewsReaderPort : NewsReaderPort {
        var articlesByKeyword: Map<AiKeyword, List<NewsArticle>> = emptyMap()

        override suspend fun findNews(
            keyword: AiKeyword,
            from: Instant?,
            to: Instant?,
            limit: Int
        ): List<NewsArticle> {
            return articlesByKeyword[keyword].orEmpty().take(limit)
        }
    }

    private class FakeLlmProviderPort : LlmProviderPort {
        var summarizeCallCount: Int = 0

        override fun prepareNewsSummary(): PreparedLlmNewsSummary {
            return object : PreparedLlmNewsSummary {
                override val plan: LlmNewsSummaryPlan = LlmNewsSummaryPlan(
                    provider = LlmProviderName.of("openrouter"),
                    model = LlmModelName.of("test-model"),
                    promptVersion = PromptVersion.of("news-summary-v1")
                )

                override suspend fun summarize(
                    keyword: AiKeyword,
                    articles: List<NewsArticle>
                ): LlmNewsSummaryResult {
                    return this@FakeLlmProviderPort.summarizeNews(keyword, articles)
                }
            }
        }

        override suspend fun expandKeyword(
            keyword: AiKeyword,
            maxExpansions: Int
        ): LlmKeywordExpansionResult {
            throw UnsupportedOperationException("keyword expansion is not used in this test")
        }

        override suspend fun summarizeNews(
            keyword: AiKeyword,
            articles: List<NewsArticle>
        ): LlmNewsSummaryResult {
            summarizeCallCount += 1

            return LlmNewsSummaryResult(
                title = "${keyword.value} 요약",
                content = "요약 본문",
                sentiment = NewsSummarySentiment.NEUTRAL,
                metadata = LlmGenerationMetadata(
                    provider = LlmProviderName.of("openrouter"),
                    model = LlmModelName.of("test-model"),
                    promptVersion = PromptVersion.of("news-summary-v1"),
                    tokenUsage = TokenUsage(inputTokens = 10, outputTokens = 20)
                )
            )
        }
    }

    private class FakeNewsSummaryPersistencePort : NewsSummaryPersistencePort {
        val savedSummaries: MutableList<NewsSummary> = mutableListOf()
        val existingSummaries: MutableList<NewsSummary> = mutableListOf()
        var duplicateOnSave: Boolean = false
        var existingAfterDuplicate: NewsSummary? = null
        var saveOrFindExistingCallCount: Int = 0

        override suspend fun findByUniqueKey(
            keyword: AiKeyword,
            newsHash: String,
            promptVersion: PromptVersion,
            model: LlmModelName
        ): NewsSummary? {
            return existingSummaries.firstOrNull {
                it.keyword == keyword &&
                    it.newsHash == newsHash &&
                    it.promptVersion == promptVersion &&
                    it.model == model
            }
        }

        override suspend fun save(newsSummary: NewsSummary): NewsSummary {
            savedSummaries.add(newsSummary)
            return newsSummary
        }

        override suspend fun saveOrFindExisting(newsSummary: NewsSummary): NewsSummary {
            saveOrFindExistingCallCount += 1

            if (duplicateOnSave) {
                return existingAfterDuplicate ?: newsSummary
            }

            return save(newsSummary)
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
