package me.rgunny.kachi.ai.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("AiRun")
class AiRunTest {

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
            finishedAt = Instant.parse("2026-06-02T00:00:01Z")
        )

        assertEquals(AiRunStatus.SUCCEEDED, completed.status)
        assertEquals(null, completed.failureReason)
    }

    @Test
    @DisplayName("일부 키워드만 실패하면 PARTIALLY_FAILED 상태로 완료한다")
    fun completeAsPartiallyFailed() {
        val completed = runningRun().complete(
            succeededCount = 1,
            failureCount = 1,
            failureReason = AiFailureReason.INVALID_RESPONSE,
            finishedAt = Instant.parse("2026-06-02T00:00:01Z")
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
            finishedAt = Instant.parse("2026-06-02T00:00:01Z")
        )

        assertEquals(AiRunStatus.FAILED, completed.status)
        assertEquals(AiFailureReason.RATE_LIMITED, completed.failureReason)
    }

    @Test
    @DisplayName("완료 시각이 시작 시각보다 이전이면 완료할 수 없다")
    fun rejectFinishedAtBeforeStartedAt() {
        assertFailsWith<IllegalArgumentException> {
            runningRun().complete(
                succeededCount = 1,
                failureCount = 0,
                failureReason = null,
                finishedAt = Instant.parse("2026-06-01T23:59:59Z")
            )
        }
    }

    private fun runningRun(): AiRun {
        return AiRun.start(
            targetType = AiRunTargetType.NEWS_SUMMARY,
            requestedKeywords = 2,
            provider = LlmProviderName.of("openai"),
            model = LlmModelName.of("gpt-4.1-mini"),
            promptVersion = PromptVersion.of("news-summary-v1"),
            startedAt = Instant.parse("2026-06-02T00:00:00Z")
        )
    }
}
