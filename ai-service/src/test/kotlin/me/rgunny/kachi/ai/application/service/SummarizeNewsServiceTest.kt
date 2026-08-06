package me.rgunny.kachi.ai.application.service

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.`in`.news.SummaryWindowRequest
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRunStatus
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.summary.NewsHash
import me.rgunny.kachi.ai.fake.FakeAiRunPersistencePort
import me.rgunny.kachi.ai.fake.FakeKeywordQuarantinePersistencePort
import me.rgunny.kachi.ai.fake.FakeKeywordReaderPort
import me.rgunny.kachi.ai.fake.FakeLlmProviderPort
import me.rgunny.kachi.ai.fake.FakeNewsReaderPort
import me.rgunny.kachi.ai.fake.FakeNewsSummaryPersistencePort
import me.rgunny.kachi.ai.fake.FakeSummaryWatermarkPersistencePort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("SummarizeNewsService")
class SummarizeNewsServiceTest {
    private val now = AiTestFixture.NOW
    private val clock = AiTestFixture.CLOCK
    private val overlap = Duration.ofMinutes(5)
    private val maxLookback = Duration.ofHours(6)
    private val keywordReader = FakeKeywordReaderPort()
    private val newsReader = FakeNewsReaderPort()
    private val llmProvider = FakeLlmProviderPort()
    private val newsSummaryPersistence = FakeNewsSummaryPersistencePort()
    private val aiRunPersistence = FakeAiRunPersistencePort()
    private val watermarkPersistence = FakeSummaryWatermarkPersistencePort()
    private val quarantinePersistence = FakeKeywordQuarantinePersistencePort()

    @Test
    @DisplayName("키워드별 뉴스를 요약하고 실행 기록을 성공 상태로 완료한다")
    fun summarizeNews() = runBlocking {
        newsReader.articlesByKeyword = mapOf(AiKeyword.of("NVIDIA") to listOf(AiTestFixture.newsArticle()))
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(
                keywords = listOf(AiKeyword.of("NVIDIA")),
                window = explicitWindow(),
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
            newsHash = NewsHash.calculate(keyword = keyword, sourceNewsIds = listOf(article.id))
        )
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(keywords = listOf(keyword), window = explicitWindow())
        )

        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertEquals(1, result.summaries.size)
        assertEquals("NVIDIA 기존 요약", result.summaries.first().title)
        assertEquals(true, result.summaries.first().reused)
        assertEquals(0, llmProvider.summarizeCallCount)
        assertEquals(0, newsSummaryPersistence.savedSummaries.size)
    }

    @Test
    @DisplayName("구간이 달라도 같은 뉴스 묶음이면 기존 요약을 재사용한다")
    fun reuseExistingSummaryWhenWindowDiffers() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        val article = AiTestFixture.newsArticle()
        newsReader.articlesByKeyword = mapOf(keyword to listOf(article))
        newsSummaryPersistence.existingSummaries += AiTestFixture.newsSummary(
            keyword = keyword,
            sourceNewsIds = listOf(article.id),
            newsHash = NewsHash.calculate(keyword = keyword, sourceNewsIds = listOf(article.id))
        )
        val service = service()

        // 실패 후 재시도는 watermark가 유지된 채 구간의 끝만 앞으로 밀린 상태로 다시 들어온다.
        val result = service.summarize(
            SummarizeNewsCommand(
                keywords = listOf(keyword),
                window = SummaryWindowRequest.Explicit(
                    from = now.minus(Duration.ofHours(1)),
                    to = now.plus(Duration.ofMinutes(10))
                )
            )
        )

        assertEquals(true, result.summaries.first().reused)
        assertEquals(0, llmProvider.summarizeCallCount)
    }

    @Test
    @DisplayName("기존 요약이 없으면 LLM 호출 후 저장한다")
    fun callLlmAndSaveWhenSummaryDoesNotExist() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        newsReader.articlesByKeyword = mapOf(keyword to listOf(AiTestFixture.newsArticle()))
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(keywords = listOf(keyword), window = SummaryWindowRequest.Explicit(null, null))
        )

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
        newsReader.articlesByKeyword = mapOf(keyword to listOf(article))
        newsSummaryPersistence.duplicateOnSave = true
        newsSummaryPersistence.existingAfterDuplicate = AiTestFixture.newsSummary(
            keyword = keyword,
            sourceNewsIds = listOf(article.id),
            newsHash = NewsHash.calculate(keyword = keyword, sourceNewsIds = listOf(article.id))
        )
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(keywords = listOf(keyword), window = SummaryWindowRequest.Explicit(null, null))
        )

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

        val result = service.summarize(
            SummarizeNewsCommand(keywords = emptyList(), window = SummaryWindowRequest.Explicit(null, null))
        )

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
                window = SummaryWindowRequest.Explicit(null, null)
            )
        )

        assertEquals(AiRunStatus.FAILED, result.status)
        assertEquals(0, result.succeededCount)
        assertEquals(1, result.failureCount)
        assertEquals(AiFailureReason.EMPTY_INPUT, aiRunPersistence.savedRuns.last().failureReason)
        assertEquals(0, newsSummaryPersistence.savedSummaries.size)
    }

    @Test
    @DisplayName("watermark가 있으면 그 지점에서 overlap만큼 물러난 구간을 요약한다")
    fun resumeFromWatermark() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        val position = now.minus(Duration.ofMinutes(30))
        watermarkPersistence.watermarks[AiRunTargetType.NEWS_SUMMARY] = AiTestFixture.watermark(position)
        newsReader.articlesByKeyword = mapOf(keyword to listOf(AiTestFixture.newsArticle()))
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(keywords = listOf(keyword), window = watermarkWindow())
        )

        assertEquals(position.minus(overlap), result.windowFrom)
        assertEquals(now, result.windowTo)
    }

    @Test
    @DisplayName("모든 키워드가 성공하면 watermark를 구간의 끝으로 전진시킨다")
    fun advanceWatermarkWhenAllKeywordsSucceed() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        newsReader.articlesByKeyword = mapOf(keyword to listOf(AiTestFixture.newsArticle()))
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(keywords = listOf(keyword), window = watermarkWindow())
        )

        assertTrue(result.watermarkAdvanced)
        assertEquals(now, watermarkPersistence.watermarks[AiRunTargetType.NEWS_SUMMARY]?.position)
        assertTrue(aiRunPersistence.savedRuns.last().watermarkAdvanced)
    }

    @Test
    @DisplayName("watermark가 구간의 끝보다 앞서 있으면 전진하지 않았다고 기록한다")
    fun recordNotAdvancedWhenWatermarkIsAhead() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        val future = now.plus(Duration.ofMinutes(10))
        watermarkPersistence.watermarks[AiRunTargetType.NEWS_SUMMARY] = AiTestFixture.watermark(future)
        newsReader.articlesByKeyword = mapOf(keyword to listOf(AiTestFixture.newsArticle()))
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(keywords = listOf(keyword), window = watermarkWindow())
        )

        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertFalse(result.watermarkAdvanced)
        assertFalse(aiRunPersistence.savedRuns.last().watermarkAdvanced)
        assertEquals(0, watermarkPersistence.saveCount)
        assertEquals(future, watermarkPersistence.watermarks[AiRunTargetType.NEWS_SUMMARY]?.position)
    }

    @Test
    @DisplayName("키워드 하나라도 실패하면 watermark를 유지해 다음 실행이 같은 구간을 다시 처리한다")
    fun keepWatermarkWhenAnyKeywordFails() = runBlocking {
        val succeeded = AiKeyword.of("NVIDIA")
        val failed = AiKeyword.of("TESLA")
        newsReader.articlesByKeyword = mapOf(
            succeeded to listOf(AiTestFixture.newsArticle()),
            failed to emptyList()
        )
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(keywords = listOf(succeeded, failed), window = watermarkWindow())
        )

        assertEquals(AiRunStatus.PARTIALLY_FAILED, result.status)
        assertFalse(result.watermarkAdvanced)
        assertNull(watermarkPersistence.watermarks[AiRunTargetType.NEWS_SUMMARY])
    }

    @Test
    @DisplayName("구간을 직접 지정한 수동 실행은 watermark를 전진시키지 않는다")
    fun keepWatermarkOnExplicitWindow() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        newsReader.articlesByKeyword = mapOf(keyword to listOf(AiTestFixture.newsArticle()))
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(keywords = listOf(keyword), window = explicitWindow())
        )

        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertFalse(result.watermarkAdvanced)
        assertEquals(0, watermarkPersistence.saveCount)
    }

    @Test
    @DisplayName("격리된 키워드는 요약 대상과 watermark 전진 판단에서 모두 제외한다")
    fun excludeQuarantinedKeyword() = runBlocking {
        val healthy = AiKeyword.of("NVIDIA")
        val quarantined = AiKeyword.of("TESLA")
        quarantinePersistence.quarantines += AiTestFixture.quarantine(
            keyword = quarantined,
            consecutiveFailures = AiTestFixture.DEFAULT_QUARANTINE_FAILURE_THRESHOLD
        )
        newsReader.articlesByKeyword = mapOf(
            healthy to listOf(AiTestFixture.newsArticle()),
            quarantined to emptyList()
        )
        val service = service()

        val result = service.summarize(
            SummarizeNewsCommand(keywords = listOf(healthy, quarantined), window = watermarkWindow())
        )

        assertEquals(1, result.requestedKeywords)
        assertEquals(AiRunStatus.SUCCEEDED, result.status)
        assertTrue(result.watermarkAdvanced)
    }

    @Test
    @DisplayName("연속 실패가 임계치에 도달한 키워드를 격리한다")
    fun quarantineKeywordAfterConsecutiveFailures() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        quarantinePersistence.quarantines += AiTestFixture.quarantine(keyword = keyword, consecutiveFailures = 2)
        newsReader.articlesByKeyword = mapOf(keyword to emptyList())
        val service = service()

        service.summarize(SummarizeNewsCommand(keywords = listOf(keyword), window = watermarkWindow()))

        val quarantine = quarantinePersistence.findByKeyword(keyword)
        assertEquals(KeywordQuarantineStatus.QUARANTINED, quarantine?.status)
        assertEquals(AiTestFixture.DEFAULT_QUARANTINE_FAILURE_THRESHOLD, quarantine?.consecutiveFailures)
        assertEquals(AiFailureReason.EMPTY_INPUT, quarantine?.lastFailureReason)
    }

    @Test
    @DisplayName("성공한 키워드의 연속 실패 누적을 되돌린다")
    fun resetFailuresWhenKeywordSucceeds() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        quarantinePersistence.quarantines += AiTestFixture.quarantine(keyword = keyword, consecutiveFailures = 2)
        newsReader.articlesByKeyword = mapOf(keyword to listOf(AiTestFixture.newsArticle()))
        val service = service()

        service.summarize(SummarizeNewsCommand(keywords = listOf(keyword), window = watermarkWindow()))

        assertEquals(0, quarantinePersistence.findByKeyword(keyword)?.consecutiveFailures)
    }

    @Test
    @DisplayName("실패한 적 없는 키워드는 격리 기록을 만들지 않는다")
    fun doNotTrackKeywordWithoutFailure() = runBlocking {
        val keyword = AiKeyword.of("NVIDIA")
        newsReader.articlesByKeyword = mapOf(keyword to listOf(AiTestFixture.newsArticle()))
        val service = service()

        service.summarize(SummarizeNewsCommand(keywords = listOf(keyword), window = watermarkWindow()))

        assertEquals(0, quarantinePersistence.saveCount)
    }

    private fun service(): SummarizeNewsService {
        return SummarizeNewsService(
            keywordReaderPort = keywordReader,
            newsReaderPort = newsReader,
            llmProviderPort = llmProvider,
            newsSummaryPersistencePort = newsSummaryPersistence,
            aiRunPersistencePort = aiRunPersistence,
            summaryWatermarkPersistencePort = watermarkPersistence,
            keywordQuarantinePersistencePort = quarantinePersistence,
            quarantineProperties = AiTestFixture.quarantineProperties(),
            clock = clock
        )
    }

    private fun explicitWindow(
        from: Instant = Instant.parse("2026-06-02T00:00:00Z"),
        to: Instant = Instant.parse("2026-06-03T00:00:00Z")
    ): SummaryWindowRequest.Explicit {
        return SummaryWindowRequest.Explicit(from = from, to = to)
    }

    private fun watermarkWindow(): SummaryWindowRequest.FromWatermark {
        return SummaryWindowRequest.FromWatermark(overlap = overlap, maxLookback = maxLookback)
    }
}
