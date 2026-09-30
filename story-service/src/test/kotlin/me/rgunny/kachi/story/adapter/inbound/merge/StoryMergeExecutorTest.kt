package me.rgunny.kachi.story.adapter.inbound.merge

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.port.inbound.merge.MergeOpenStoriesUseCase
import me.rgunny.kachi.story.application.port.inbound.merge.model.MergeOpenStoriesResult
import me.rgunny.kachi.story.application.port.outbound.lock.model.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockHolder
import me.rgunny.kachi.story.application.port.outbound.lock.model.StoryExecutionLock
import me.rgunny.kachi.story.application.port.outbound.lock.model.UnavailableExecutionLockOutcome
import me.rgunny.kachi.story.fake.FakeExecutionLockPort
import me.rgunny.kachi.story.fixture.StoryTestFixture
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryMergeExecutor")
class StoryMergeExecutorTest {

    @Test
    @DisplayName("lock을 얻으면 병합을 실행하고 결과를 돌려준다")
    fun executeWhenLockAcquired() = runBlocking {
        val useCase = CountingMergeOpenStoriesUseCase()
        val executor = StoryMergeExecutor(useCase, StoryTestFixture.executionLock())

        val execution = executor.execute()

        assertEquals(1, useCase.callCount)
        assertIs<CompletedStoryMergeExecution>(execution)
        assertEquals(useCase.result, execution.result)
    }

    @Test
    @DisplayName("다른 병합이 실행 중이면 건너뛴다")
    fun skipWhenAlreadyRunning() = runBlocking {
        val useCase = CountingMergeOpenStoriesUseCase()
        val lock = FakeExecutionLockPort(
            AlreadyHeldExecutionLockOutcome(ExecutionLockHolder(acquiredAt = NOW, owner = "other"))
        )
        val executor = StoryMergeExecutor(useCase, lock)

        val execution = executor.execute()

        assertEquals(0, useCase.callCount)
        assertIs<AlreadyRunningStoryMergeExecution>(execution)
        assertEquals(NOW, execution.holder.acquiredAt)
        assertEquals(StoryExecutionLock.STORY_MERGE, lock.requestedTarget)
    }

    @Test
    @DisplayName("lock을 확인할 수 없으면 시작하지 않고 원인을 돌려준다")
    fun reportCauseWhenLockUnavailable() = runBlocking {
        val useCase = CountingMergeOpenStoriesUseCase()
        val cause = IllegalStateException("lock store unavailable")
        val executor = StoryMergeExecutor(useCase, FakeExecutionLockPort(UnavailableExecutionLockOutcome(cause)))

        val execution = executor.execute()

        assertEquals(0, useCase.callCount)
        assertIs<UnavailableStoryMergeExecution>(execution)
        assertEquals(cause, execution.cause)
    }
}

/** 호출 수를 세고 고정 결과를 돌려주는 병합 유스케이스. */
private class CountingMergeOpenStoriesUseCase : MergeOpenStoriesUseCase {
    val result = MergeOpenStoriesResult(
        scannedCount = 5,
        mergedCount = 2,
        conflictedCount = 1,
        indexReassignFailureCount = 0
    )

    var callCount: Int = 0
        private set

    override suspend fun mergeOpenStories(): MergeOpenStoriesResult {
        callCount += 1

        return result
    }
}
