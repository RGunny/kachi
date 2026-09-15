package me.rgunny.kachi.story.adapter.inbound.index

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.port.outbound.lock.model.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockHolder
import me.rgunny.kachi.story.application.port.outbound.lock.model.StoryExecutionLock
import me.rgunny.kachi.story.application.port.outbound.lock.model.UnavailableExecutionLockOutcome
import me.rgunny.kachi.story.fake.FakeExecutionLockPort
import me.rgunny.kachi.story.fake.FakeRebuildCandidateIndexUseCase
import me.rgunny.kachi.story.fixture.StoryTestFixture
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("IndexRebuildExecutor")
class IndexRebuildExecutorTest {

    @Test
    @DisplayName("lock을 얻으면 시작을 알리고 본체 완료를 기다리지 않는다")
    fun startWithoutWaitingForCompletion() = runBlocking {
        val useCase = FakeRebuildCandidateIndexUseCase()
        val executor = IndexRebuildExecutor(useCase, StoryTestFixture.executionLock())

        val execution = executor.start()

        assertIs<StartedIndexRebuildExecution>(execution)
        assertFalse(useCase.completed.isCompleted)
        useCase.gate.complete(Unit)
        useCase.completed.await()
        assertEquals(1, useCase.callCount)
    }

    @Test
    @DisplayName("다른 재구축이 실행 중이면 시작하지 않는다")
    fun skipWhenAlreadyRunning() = runBlocking {
        val useCase = FakeRebuildCandidateIndexUseCase()
        val lock = FakeExecutionLockPort(
            AlreadyHeldExecutionLockOutcome(ExecutionLockHolder(acquiredAt = NOW, owner = "other"))
        )
        val executor = IndexRebuildExecutor(useCase, lock)

        val execution = executor.start()

        assertEquals(0, useCase.callCount)
        assertIs<AlreadyRunningIndexRebuildExecution>(execution)
        assertEquals(StoryExecutionLock.INDEX_REBUILD, lock.requestedTarget)
    }

    @Test
    @DisplayName("lock을 확인할 수 없으면 시작하지 않고 원인을 돌려준다")
    fun reportCauseWhenLockUnavailable() = runBlocking {
        val useCase = FakeRebuildCandidateIndexUseCase()
        val cause = IllegalStateException("lock store unavailable")
        val executor = IndexRebuildExecutor(useCase, FakeExecutionLockPort(UnavailableExecutionLockOutcome(cause)))

        val execution = executor.start()

        assertEquals(0, useCase.callCount)
        assertIs<UnavailableIndexRebuildExecution>(execution)
        assertEquals(cause, execution.cause)
    }
}
