package me.rgunny.kachi.ai.adapter.`in`.news

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsResult
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsUseCase
import me.rgunny.kachi.ai.application.port.`in`.news.SummaryWindowRequest
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs

@DisplayName("AiNewsSummaryExecutor")
class AiNewsSummaryExecutorTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-03T00:00:00Z"), ZoneOffset.UTC)

    @Test
    @DisplayName("실행 중인 뉴스 요약이 있으면 중복 요청을 건너뛴다")
    fun skipWhenNewsSummaryIsAlreadyRunning() = runBlocking {
        val useCase = BlockingSummarizeNewsUseCase()
        val executor = AiNewsSummaryExecutor(useCase, clock)
        val command = SummarizeNewsCommand(
            keywords = listOf(AiKeyword.of("NVIDIA")),
            window = SummaryWindowRequest.Explicit(from = null, to = null)
        )

        val firstExecution = async {
            executor.execute(command)
        }
        useCase.started.await()

        val secondExecution = executor.execute(command)
        useCase.release.complete(Unit)

        assertIs<AiNewsSummaryExecutionResult.AlreadyRunning>(secondExecution)
        assertEquals(Instant.parse("2026-06-03T00:00:00Z"), secondExecution.runningSummary.startedAt)
        assertIs<AiNewsSummaryExecutionResult.Started>(firstExecution.await())
        assertEquals(1, useCase.executeCount)
    }

    private class BlockingSummarizeNewsUseCase : SummarizeNewsUseCase {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var executeCount = 0

        override suspend fun summarize(command: SummarizeNewsCommand): SummarizeNewsResult {
            executeCount += 1
            started.complete(Unit)
            release.await()

            return SummarizeNewsResult.from(
                AiRun.start(
                    targetType = AiRunTargetType.NEWS_SUMMARY,
                    requestedKeywords = command.keywords.size,
                    startedAt = Instant.parse("2026-06-03T00:00:00Z")
                ).complete(
                    succeededCount = command.keywords.size,
                    failureCount = 0,
                    failureReason = null,
                    provider = null,
                    model = null,
                    promptVersion = null,
                    finishedAt = Instant.parse("2026-06-03T00:00:01Z")
                )
            )
        }
    }
}
