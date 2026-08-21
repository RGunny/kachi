package me.rgunny.kachi.ai.adapter.inbound.scheduler

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.adapter.inbound.news.AiNewsSummaryExecutor
import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.inbound.news.SummarizeNewsUseCase
import me.rgunny.kachi.ai.application.port.inbound.news.model.SummaryWindowRequest
import me.rgunny.kachi.ai.fake.FailingSummarizeNewsUseCase
import me.rgunny.kachi.ai.fake.RecordingSummarizeNewsUseCase
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@DisplayName("AiNewsSummaryScheduler")
class AiNewsSummarySchedulerTest {
    private val clock = AiTestFixture.CLOCK

    @Test
    @DisplayName("scheduler가 비활성화되어 있으면 뉴스 요약을 실행하지 않는다")
    fun skipWhenSchedulerIsDisabled() = runBlocking {
        val useCase = RecordingSummarizeNewsUseCase()
        val scheduler = schedulerOf(useCase, properties(enabled = false))

        scheduler.summarizeNews()

        assertEquals(0, useCase.invokeCount)
    }

    @Test
    @DisplayName("구간 정책을 watermark 기반 요청으로 전달한다")
    fun passWatermarkWindowRequest() = runBlocking {
        val useCase = RecordingSummarizeNewsUseCase()
        val scheduler = schedulerOf(
            useCase,
            properties(overlap = Duration.ofMinutes(5), maxLookback = Duration.ofHours(6))
        )

        scheduler.summarizeNews()

        val window = assertIs<SummaryWindowRequest.FromWatermark>(requireNotNull(useCase.lastCommand).window)
        assertEquals(Duration.ofMinutes(5), window.overlap)
        assertEquals(Duration.ofHours(6), window.maxLookback)
    }

    @Test
    @DisplayName("요약 대상 키워드를 비워 보내 활성 키워드 전체를 대상으로 삼는다")
    fun requestAllActiveKeywords() = runBlocking {
        val useCase = RecordingSummarizeNewsUseCase()
        val scheduler = schedulerOf(useCase, properties(maxArticlesPerKeyword = 15))

        scheduler.summarizeNews()

        val command = requireNotNull(useCase.lastCommand)
        assertTrue(command.keywords.isEmpty())
        assertEquals(15, command.maxArticlesPerKeyword)
    }

    @Test
    @DisplayName("요약 실행이 실패해도 scheduler 루프가 중단되지 않도록 예외를 전파하지 않는다")
    fun swallowExecutionFailure() = runBlocking {
        val useCase = FailingSummarizeNewsUseCase()
        val scheduler = schedulerOf(useCase, properties())

        scheduler.summarizeNews()

        assertEquals(1, useCase.invokeCount)
    }

    @Test
    @DisplayName("overlap이 maxLookback보다 길면 매 실행이 잘린 구간을 만들므로 설정을 만들 수 없다")
    fun rejectOverlapLongerThanMaxLookback() {
        assertFailsWith<IllegalArgumentException> {
            properties(overlap = Duration.ofHours(7), maxLookback = Duration.ofHours(6))
        }
    }

    @Test
    @DisplayName("overlap이 음수이거나 maxLookback이 0 이하이면 설정을 만들 수 없다")
    fun rejectInvalidWindowPolicy() {
        assertFailsWith<IllegalArgumentException> {
            properties(overlap = Duration.ofMinutes(-1))
        }
        assertFailsWith<IllegalArgumentException> {
            properties(maxLookback = Duration.ZERO)
        }
    }

    @Test
    @DisplayName("키워드별 최대 뉴스 개수가 허용 범위를 벗어나면 설정을 만들 수 없다")
    fun rejectInvalidMaxArticles() {
        assertFailsWith<IllegalArgumentException> {
            properties(maxArticlesPerKeyword = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            properties(maxArticlesPerKeyword = SummarizeNewsCommand.MAX_ARTICLES_PER_KEYWORD + 1)
        }
    }

    private fun schedulerOf(
        useCase: SummarizeNewsUseCase,
        properties: AiNewsSummarySchedulerProperties
    ): AiNewsSummaryScheduler {
        return AiNewsSummaryScheduler(
            executor = AiNewsSummaryExecutor(useCase, clock),
            properties = properties
        )
    }

    private fun properties(
        enabled: Boolean = true,
        fixedDelay: Duration = Duration.ofMinutes(10),
        initialDelay: Duration = Duration.ofSeconds(30),
        overlap: Duration = Duration.ofMinutes(5),
        maxLookback: Duration = Duration.ofHours(6),
        maxArticlesPerKeyword: Int = SummarizeNewsCommand.DEFAULT_MAX_ARTICLES_PER_KEYWORD
    ): AiNewsSummarySchedulerProperties {
        return AiNewsSummarySchedulerProperties(
            enabled = enabled,
            fixedDelay = fixedDelay,
            initialDelay = initialDelay,
            overlap = overlap,
            maxLookback = maxLookback,
            maxArticlesPerKeyword = maxArticlesPerKeyword
        )
    }

}
