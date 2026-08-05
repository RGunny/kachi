package me.rgunny.kachi.ai.adapter.`in`.scheduler

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.adapter.`in`.news.AiNewsSummaryExecutor
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsResult
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsUseCase
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("AiNewsSummaryScheduler")
class AiNewsSummarySchedulerTest {
    private val now = AiTestFixture.NOW
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
    @DisplayName("lookback 기간을 요약 대상 window로 계산해서 전달한다")
    fun passLookbackWindowToCommand() = runBlocking {
        val useCase = RecordingSummarizeNewsUseCase()
        val scheduler = schedulerOf(useCase, properties(lookback = Duration.ofMinutes(30)))

        scheduler.summarizeNews()

        val command = requireNotNull(useCase.lastCommand)
        assertEquals(now.minus(Duration.ofMinutes(30)), command.from)
        assertEquals(now, command.to)
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
    @DisplayName("lookback이 실행 주기보다 짧으면 tick 사이 뉴스 누락 가능성을 알린다")
    fun detectWindowGap() {
        val gapped = properties(fixedDelay = Duration.ofMinutes(10), lookback = Duration.ofMinutes(5))
        val overlapped = properties(fixedDelay = Duration.ofMinutes(10), lookback = Duration.ofMinutes(30))

        assertTrue(gapped.hasWindowGap())
        assertFalse(overlapped.hasWindowGap())
    }

    @Test
    @DisplayName("lookback이 0 이하이면 설정을 만들 수 없다")
    fun rejectNonPositiveLookback() {
        assertFailsWith<IllegalArgumentException> {
            properties(lookback = Duration.ZERO)
        }
        assertFailsWith<IllegalArgumentException> {
            properties(lookback = Duration.ofMinutes(-1))
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
            properties = properties,
            clock = clock
        )
    }

    private fun properties(
        enabled: Boolean = true,
        fixedDelay: Duration = Duration.ofMinutes(10),
        initialDelay: Duration = Duration.ofSeconds(30),
        lookback: Duration = Duration.ofMinutes(30),
        maxArticlesPerKeyword: Int = SummarizeNewsCommand.DEFAULT_MAX_ARTICLES_PER_KEYWORD
    ): AiNewsSummarySchedulerProperties {
        return AiNewsSummarySchedulerProperties(
            enabled = enabled,
            fixedDelay = fixedDelay,
            initialDelay = initialDelay,
            lookback = lookback,
            maxArticlesPerKeyword = maxArticlesPerKeyword
        )
    }

    private class RecordingSummarizeNewsUseCase : SummarizeNewsUseCase {
        var invokeCount = 0
        var lastCommand: SummarizeNewsCommand? = null

        override suspend fun summarize(command: SummarizeNewsCommand): SummarizeNewsResult {
            invokeCount += 1
            lastCommand = command

            return SummarizeNewsResult.from(completedRun(command.keywords.size))
        }
    }

    private class FailingSummarizeNewsUseCase : SummarizeNewsUseCase {
        var invokeCount = 0

        override suspend fun summarize(command: SummarizeNewsCommand): SummarizeNewsResult {
            invokeCount += 1
            throw IllegalStateException("news summary failed")
        }
    }

    private companion object {
        fun completedRun(requestedKeywords: Int): AiRun {
            return AiTestFixture.completedRun(
                targetType = AiRunTargetType.NEWS_SUMMARY,
                requestedKeywords = requestedKeywords
            )
        }
    }
}
