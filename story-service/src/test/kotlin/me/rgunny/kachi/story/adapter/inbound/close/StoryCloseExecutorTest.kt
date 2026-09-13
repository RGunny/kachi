package me.rgunny.kachi.story.adapter.inbound.close

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.port.inbound.close.CloseIdleStoriesUseCase
import me.rgunny.kachi.story.application.port.inbound.close.model.CloseIdleStoriesResult
import me.rgunny.kachi.story.application.port.outbound.lock.model.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockHolder
import me.rgunny.kachi.story.application.port.outbound.lock.model.StoryExecutionLock
import me.rgunny.kachi.story.application.port.outbound.lock.model.UnavailableExecutionLockOutcome
import me.rgunny.kachi.story.fake.FakeExecutionLockPort
import me.rgunny.kachi.story.fixture.StoryTestFixture
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryCloseExecutor")
class StoryCloseExecutorTest {

    @Test
    @DisplayName("lock을 얻으면 닫기를 실행하고 결과를 돌려준다")
    fun executeWhenLockAcquired() = runBlocking {
        val useCase = CountingCloseIdleStoriesUseCase()
        val executor = StoryCloseExecutor(useCase, StoryTestFixture.executionLock())

        val execution = executor.execute()

        assertEquals(1, useCase.callCount)
        assertIs<CompletedStoryCloseExecution>(execution)
        assertEquals(useCase.result, execution.result)
    }

    @Test
    @DisplayName("다른 닫기가 실행 중이면 건너뛴다")
    fun skipWhenAlreadyRunning() = runBlocking {
        val useCase = CountingCloseIdleStoriesUseCase()
        val lock = FakeExecutionLockPort(
            AlreadyHeldExecutionLockOutcome(ExecutionLockHolder(acquiredAt = NOW, owner = "other"))
        )
        val executor = StoryCloseExecutor(useCase, lock)

        val execution = executor.execute()

        assertEquals(0, useCase.callCount)
        assertIs<AlreadyRunningStoryCloseExecution>(execution)
        assertEquals(NOW, execution.holder.acquiredAt)
        assertEquals(StoryExecutionLock.STORY_CLOSE, lock.requestedTarget)
    }

    @Test
    @DisplayName("lock을 확인할 수 없으면 시작하지 않고 원인을 돌려준다")
    fun reportCauseWhenLockUnavailable() = runBlocking {
        val useCase = CountingCloseIdleStoriesUseCase()
        val cause = IllegalStateException("lock store unavailable")
        val executor = StoryCloseExecutor(useCase, FakeExecutionLockPort(UnavailableExecutionLockOutcome(cause)))

        val execution = executor.execute()

        assertEquals(0, useCase.callCount)
        assertIs<UnavailableStoryCloseExecution>(execution)
        assertEquals(cause, execution.cause)
    }
}

/** 호출 수를 세고 고정 결과를 돌려주는 닫기 유스케이스. */
private class CountingCloseIdleStoriesUseCase : CloseIdleStoriesUseCase {
    val result = CloseIdleStoriesResult(
        threshold = NOW.minus(Duration.ofHours(48)),
        closedCount = 2,
        conflictedCount = 1,
        indexDeleteFailureCount = 0
    )

    var callCount: Int = 0
        private set

    override suspend fun closeIdleStories(): CloseIdleStoriesResult {
        callCount += 1

        return result
    }
}
