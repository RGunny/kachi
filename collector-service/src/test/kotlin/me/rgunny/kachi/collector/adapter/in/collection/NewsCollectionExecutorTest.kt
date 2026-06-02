package me.rgunny.kachi.collector.adapter.`in`.collection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.application.port.`in`.CollectNewsCommand
import me.rgunny.kachi.collector.application.port.`in`.CollectNewsUseCase
import me.rgunny.kachi.collector.application.port.`in`.CollectionRunResult
import me.rgunny.kachi.collector.domain.CollectionRunId
import me.rgunny.kachi.collector.domain.CollectionRunStatus
import me.rgunny.kachi.collector.domain.CollectionTargetType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs

@DisplayName("NewsCollectionExecutor")
class NewsCollectionExecutorTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-30T00:00:00Z"), ZoneOffset.UTC)

    @Test
    @DisplayName("수집 실행 중이면 중복 실행을 거절한다")
    fun rejectWhenCollectionIsAlreadyRunning() = runBlocking {
        val useCase = BlockingCollectNewsUseCase()
        val executor = NewsCollectionExecutor(useCase, clock)
        val first = async { executor.execute(CollectNewsCommand(keywords = emptyList())) }

        useCase.started.await()
        val second = executor.execute(CollectNewsCommand(keywords = emptyList()))
        useCase.complete.complete(Unit)

        assertIs<NewsCollectionExecutionResult.AlreadyRunning>(second)
        assertEquals(Instant.parse("2026-05-30T00:00:00Z"), second.runningCollection.startedAt)
        assertIs<NewsCollectionExecutionResult.Started>(first.await())
        assertEquals(1, useCase.callCount)
    }

    @Test
    @DisplayName("수집이 끝나면 다음 실행을 허용한다")
    fun releaseLockAfterCollectionFinished() = runBlocking {
        val useCase = SuccessfulCollectNewsUseCase()
        val executor = NewsCollectionExecutor(useCase, clock)

        val first = executor.execute(CollectNewsCommand(keywords = emptyList()))
        val second = executor.execute(CollectNewsCommand(keywords = emptyList()))

        assertIs<NewsCollectionExecutionResult.Started>(first)
        assertIs<NewsCollectionExecutionResult.Started>(second)
        assertEquals(2, useCase.callCount)
    }

    private class BlockingCollectNewsUseCase : CollectNewsUseCase {
        val started = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        var callCount = 0

        override suspend fun collect(command: CollectNewsCommand): CollectionRunResult {
            callCount += 1
            started.complete(Unit)
            complete.await()
            return result()
        }
    }

    private class SuccessfulCollectNewsUseCase : CollectNewsUseCase {
        var callCount = 0

        override suspend fun collect(command: CollectNewsCommand): CollectionRunResult {
            callCount += 1
            return result()
        }
    }

    private companion object {
        fun result(): CollectionRunResult {
            val now = Instant.parse("2026-05-30T00:00:00Z")

            return CollectionRunResult(
                id = CollectionRunId.newId(),
                targetType = CollectionTargetType.NEWS,
                status = CollectionRunStatus.SUCCEEDED,
                startedAt = now,
                finishedAt = now,
                requestedKeywords = 0,
                collectedCount = 0,
                duplicateCount = 0,
                failureCount = 0,
                failureReason = null
            )
        }
    }
}
