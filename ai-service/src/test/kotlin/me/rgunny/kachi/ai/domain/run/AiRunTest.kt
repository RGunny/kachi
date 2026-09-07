package me.rgunny.kachi.ai.domain.run

import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("AiRun")
class AiRunTest {

    private val startedAt = AiTestFixture.NOW.minus(Duration.ofDays(1))
    private val finishedAt = startedAt.plusSeconds(1)

    // 요약 구간은 실행이 시작되는 시점에서 끝난다.
    private val windowTo = startedAt
    private val windowFrom = windowTo.minus(Duration.ofMinutes(5))

    @Test
    @DisplayName("AI 실행을 RUNNING 상태로 시작한다")
    fun start() {
        val run = runningRun()

        assertEquals(AiRunStatus.RUNNING, run.status)
        assertEquals(2, run.requestedKeywords)
        assertEquals(0, run.succeededCount)
        assertEquals(0, run.failureCount)
    }

    @Test
    @DisplayName("모든 키워드가 성공하면 SUCCEEDED 상태로 완료한다")
    fun completeAsSucceeded() {
        val completed = runningRun().complete(
            succeededCount = 2,
            failureCount = 0,
            failureReason = null,
            provider = LlmProvider.GROQ,
            model = "gpt-4.1-mini",
            promptVersion = PromptVersion.of("news-summary-v1"),
            finishedAt = finishedAt
        )

        assertEquals(AiRunStatus.SUCCEEDED, completed.status)
        assertEquals(null, completed.failureReason)
        assertEquals(LlmProvider.GROQ, completed.provider)
        assertEquals("gpt-4.1-mini", completed.model)
        assertEquals(PromptVersion.of("news-summary-v1"), completed.promptVersion)
    }

    @Test
    @DisplayName("일부 키워드만 실패하면 PARTIALLY_FAILED 상태로 완료한다")
    fun completeAsPartiallyFailed() {
        val completed = runningRun().complete(
            succeededCount = 1,
            failureCount = 1,
            failureReason = AiFailureReason.INVALID_RESPONSE,
            provider = LlmProvider.GROQ,
            model = "gpt-4.1-mini",
            promptVersion = PromptVersion.of("news-summary-v1"),
            finishedAt = finishedAt
        )

        assertEquals(AiRunStatus.PARTIALLY_FAILED, completed.status)
        assertEquals(AiFailureReason.INVALID_RESPONSE, completed.failureReason)
    }

    @Test
    @DisplayName("성공 건수가 없으면 FAILED 상태로 완료한다")
    fun completeAsFailed() {
        val completed = runningRun().complete(
            succeededCount = 0,
            failureCount = 2,
            failureReason = AiFailureReason.RATE_LIMITED,
            provider = null,
            model = null,
            promptVersion = null,
            finishedAt = finishedAt
        )

        assertEquals(AiRunStatus.FAILED, completed.status)
        assertEquals(AiFailureReason.RATE_LIMITED, completed.failureReason)
    }

    @Test
    @DisplayName("전부 건너뛰고 끝나도 실패가 없으면 SUCCEEDED 상태로 완료한다")
    fun completeAsSucceededWhenAllKeywordsAreSkipped() {
        val completed = runningRun().complete(
            succeededCount = 0,
            failureCount = 0,
            failureReason = null,
            provider = null,
            model = null,
            promptVersion = null,
            finishedAt = finishedAt,
            skippedCount = 2,
            skipReason = AiSkipReason.NO_INPUT
        )

        assertEquals(AiRunStatus.SUCCEEDED, completed.status)
        assertEquals(2, completed.skippedCount)
        assertEquals(AiSkipReason.NO_INPUT, completed.skipReason)
        assertEquals(null, completed.failureReason)
    }

    @Test
    @DisplayName("건너뛴 건수가 없으면 skip 사유를 남기지 않는다")
    fun dropSkipReasonWhenNothingIsSkipped() {
        val completed = runningRun().complete(
            succeededCount = 2,
            failureCount = 0,
            failureReason = null,
            provider = null,
            model = null,
            promptVersion = null,
            finishedAt = finishedAt,
            skippedCount = 0,
            skipReason = AiSkipReason.NO_INPUT
        )

        assertEquals(0, completed.skippedCount)
        assertEquals(null, completed.skipReason)
    }

    @Test
    @DisplayName("건너뛴 건수는 음수일 수 없다")
    fun rejectNegativeSkippedCount() {
        assertFailsWith<IllegalArgumentException> {
            runningRun().complete(
                succeededCount = 1,
                failureCount = 0,
                failureReason = null,
                provider = null,
                model = null,
                promptVersion = null,
                finishedAt = finishedAt,
                skippedCount = -1
            )
        }
    }

    @Test
    @DisplayName("완료 시각이 시작 시각보다 이전이면 완료할 수 없다")
    fun rejectFinishedAtBeforeStartedAt() {
        assertFailsWith<IllegalArgumentException> {
            runningRun().complete(
                succeededCount = 1,
                failureCount = 0,
                failureReason = null,
                provider = LlmProvider.GROQ,
                model = "gpt-4.1-mini",
                promptVersion = PromptVersion.of("news-summary-v1"),
                finishedAt = startedAt.minusSeconds(1)
            )
        }
    }

    @Test
    @DisplayName("처리한 구간과 watermark 전진 여부를 실행 기록에 남긴다")
    fun keepWindowAndWatermarkAdvanced() {
        val completed = AiRun.start(
            targetType = AiRunTargetType.NEWS_SUMMARY,
            requestedKeywords = 1,
            startedAt = windowTo,
            windowFrom = windowFrom,
            windowTo = windowTo
        ).complete(
            succeededCount = 1,
            failureCount = 0,
            failureReason = null,
            provider = null,
            model = null,
            promptVersion = null,
            finishedAt = finishedAt,
            watermarkAdvanced = true
        )

        assertEquals(windowFrom, completed.windowFrom)
        assertEquals(windowTo, completed.windowTo)
        assertTrue(completed.watermarkAdvanced)
    }

    @Test
    @DisplayName("구간 시작이 종료보다 이후이면 실행을 시작할 수 없다")
    fun rejectInvertedWindow() {
        assertFailsWith<IllegalArgumentException> {
            AiRun.start(
                targetType = AiRunTargetType.NEWS_SUMMARY,
                requestedKeywords = 1,
                startedAt = startedAt,
                windowFrom = startedAt,
                windowTo = windowFrom
            )
        }
    }

    private fun runningRun(): AiRun {
        return AiRun.start(
            targetType = AiRunTargetType.NEWS_SUMMARY,
            requestedKeywords = 2,
            startedAt = startedAt
        )
    }
}
