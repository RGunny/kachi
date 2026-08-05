package me.rgunny.kachi.ai.application.service

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsCommand
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRunStatus
import me.rgunny.kachi.ai.domain.summary.NewsHash
import me.rgunny.kachi.ai.fake.FakeAiRunPersistencePort
import me.rgunny.kachi.ai.fake.FakeKeywordReaderPort
import me.rgunny.kachi.ai.fake.FakeLlmProviderPort
import me.rgunny.kachi.ai.fake.FakeNewsReaderPort
import me.rgunny.kachi.ai.fake.FakeNewsSummaryPersistencePort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

@DisplayName("SummarizeNewsService")
class SummarizeNewsServiceTest {
    private val clock = AiTestFixture.CLOCK
    private val keywordReader = FakeKeywordReaderPort()
    private val newsReader = FakeNewsReaderPort()
    private val llmProvider = FakeLlmProviderPort()
    private val newsSummaryPersistence = FakeNewsSummaryPersistencePort()
    private val aiRunPersistence = FakeAiRunPersistencePort()

    @Test
    @DisplayName("키워드별 뉴스를 요약하고 실행 기록을 성공 상태로 완료한다")
    fun summarizeNews() = runBlocking {
        newsReader.articlesByKeyword = mapOf(AiKeyword.of("NVIDIA") to listOf(AiTestFixture.newsArticle()))
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
        assertEquals(AiTestFixture.PROVIDER, aiRunPersistence.savedRuns.last().provider)
        assertEquals(AiTestFixture.MODEL, aiRunPersistence.savedRuns.last().model)
        assertEquals(AiTestFixture.NEWS_SUMMARY_PROMPT_VERSION, aiRunPersistence.savedRuns.last().promptVersion)
    }

    @Test
    @DisplayName("같은 입력의 기존 요약이 있으면 LLM을 호출하지 않고 성공 처리한다")
    fun reuseExistingSummaryBeforeLlmCall() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        val article = AiTestFixture.newsArticle()
        newsReader.articlesByKeyword = mapOf(keyword to listOf(article))
        newsSummaryPersistence.existingSummaries += AiTestFixture.newsSummary(
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
        newsReader.articlesByKeyword = mapOf(keyword to listOf(AiTestFixture.newsArticle()))
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
        val article = AiTestFixture.newsArticle()
        val newsHash = NewsHash.calculate(
            keyword = keyword,
            from = null,
            to = null,
            sourceNewsIds = listOf(article.id)
        )
        newsReader.articlesByKeyword = mapOf(keyword to listOf(article))
        newsSummaryPersistence.duplicateOnSave = true
        newsSummaryPersistence.existingAfterDuplicate = AiTestFixture.newsSummary(
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
        newsReader.articlesByKeyword = mapOf(AiKeyword.of("NVIDIA") to listOf(AiTestFixture.newsArticle()))
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
}
