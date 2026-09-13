package me.rgunny.kachi.story.adapter.inbound.cleanup

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.port.inbound.cleanup.CleanupCandidateIndexUseCase
import me.rgunny.kachi.story.application.port.inbound.cleanup.model.CleanupCandidateIndexResult
import me.rgunny.kachi.story.application.port.outbound.lock.model.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockHolder
import me.rgunny.kachi.story.application.port.outbound.lock.model.StoryExecutionLock
import me.rgunny.kachi.story.application.port.outbound.lock.model.UnavailableExecutionLockOutcome
import me.rgunny.kachi.story.fake.FakeExecutionLockPort
import me.rgunny.kachi.story.fixture.StoryTestFixture
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("IndexCleanupExecutor")
class IndexCleanupExecutorTest {

    @Test
    @DisplayName("lock을 얻으면 색인 정리를 실행하고 결과를 돌려준다")
    fun executeWhenLockAcquired() = runBlocking {
        val useCase = CountingCleanupCandidateIndexUseCase()
        val executor = IndexCleanupExecutor(useCase, StoryTestFixture.executionLock())

        val execution = executor.execute()

        assertEquals(1, useCase.callCount)
        assertIs<CompletedIndexCleanupExecution>(execution)
        assertEquals(useCase.result, execution.result)
    }

    @Test
    @DisplayName("다른 색인 정리가 실행 중이면 건너뛴다")
    fun skipWhenAlreadyRunning() = runBlocking {
        val useCase = CountingCleanupCandidateIndexUseCase()
        val lock = FakeExecutionLockPort(
            AlreadyHeldExecutionLockOutcome(ExecutionLockHolder(acquiredAt = NOW, owner = "other"))
        )
        val executor = IndexCleanupExecutor(useCase, lock)

        val execution = executor.execute()

        assertEquals(0, useCase.callCount)
        assertIs<AlreadyRunningIndexCleanupExecution>(execution)
        assertEquals(StoryExecutionLock.INDEX_CLEANUP, lock.requestedTarget)
    }

    @Test
    @DisplayName("lock을 확인할 수 없으면 시작하지 않고 원인을 돌려준다")
    fun reportCauseWhenLockUnavailable() = runBlocking {
        val useCase = CountingCleanupCandidateIndexUseCase()
        val cause = IllegalStateException("lock store unavailable")
        val executor = IndexCleanupExecutor(useCase, FakeExecutionLockPort(UnavailableExecutionLockOutcome(cause)))

        val execution = executor.execute()

        assertEquals(0, useCase.callCount)
        assertIs<UnavailableIndexCleanupExecution>(execution)
        assertEquals(cause, execution.cause)
    }
}

/** 호출 수를 세고 고정 결과를 돌려주는 색인 정리 유스케이스. */
private class CountingCleanupCandidateIndexUseCase : CleanupCandidateIndexUseCase {
    val result = CleanupCandidateIndexResult(threshold = NOW.minus(Duration.ofHours(72)))

    var callCount: Int = 0
        private set

    override suspend fun cleanup(): CleanupCandidateIndexResult {
        callCount += 1

        return result
    }
}
