package me.rgunny.kachi.ai.adapter.`in`.keyword

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsCommand
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsResult
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsUseCase
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

@DisplayName("AiKeywordExpansionExecutor")
class AiKeywordExpansionExecutorTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-03T00:00:00Z"), ZoneOffset.UTC)

    @Test
    @DisplayName("실행 중인 키워드 확장이 있으면 중복 요청을 건너뛴다")
    fun skipWhenKeywordExpansionIsAlreadyRunning() = runBlocking {
        val useCase = BlockingExpandKeywordsUseCase()
        val executor = AiKeywordExpansionExecutor(useCase, clock)
        val command = ExpandKeywordsCommand(
            keywords = listOf(AiKeyword.of("NVIDIA"))
        )

        val firstExecution = async {
            executor.execute(command)
        }
        useCase.started.await()

        val secondExecution = executor.execute(command)
        useCase.release.complete(Unit)

        assertIs<AiKeywordExpansionExecutionResult.AlreadyRunning>(secondExecution)
        assertEquals(Instant.parse("2026-06-03T00:00:00Z"), secondExecution.runningExpansion.startedAt)
        assertIs<AiKeywordExpansionExecutionResult.Started>(firstExecution.await())
        assertEquals(1, useCase.executeCount)
    }

    private class BlockingExpandKeywordsUseCase : ExpandKeywordsUseCase {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var executeCount = 0

        override suspend fun expand(command: ExpandKeywordsCommand): ExpandKeywordsResult {
            executeCount += 1
            started.complete(Unit)
            release.await()

            return ExpandKeywordsResult.from(
                AiRun.start(
                    targetType = AiRunTargetType.KEYWORD_EXPANSION,
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
