package me.rgunny.kachi.story.adapter.inbound.scheduler

import java.time.Duration
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.inbound.merge.StoryMergeExecutor
import me.rgunny.kachi.story.application.port.inbound.merge.MergeOpenStoriesUseCase
import me.rgunny.kachi.story.application.port.inbound.merge.model.MergeOpenStoriesResult
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryMergeScheduler")
class StoryMergeSchedulerTest {

    @Test
    @DisplayName("enabled=false이면 병합을 실행하지 않는다")
    fun doNotMergeWhenDisabled() = runBlocking {
        val useCase = CountingMergeUseCase()
        val scheduler = scheduler(useCase, enabled = false)

        scheduler.mergeOpenStories()

        assertEquals(0, useCase.callCount)
    }

    @Test
    @DisplayName("enabled=true이면 병합을 실행한다")
    fun mergeWhenEnabled() = runBlocking {
        val useCase = CountingMergeUseCase()
        val scheduler = scheduler(useCase, enabled = true)

        scheduler.mergeOpenStories()

        assertEquals(1, useCase.callCount)
    }

    @Test
    @DisplayName("병합 예외를 scheduler 밖으로 전파하지 않는다")
    fun swallowMergeFailure() = runBlocking {
        val scheduler = scheduler(FailingMergeUseCase(), enabled = true)

        scheduler.mergeOpenStories()
    }

    private fun scheduler(useCase: MergeOpenStoriesUseCase, enabled: Boolean): StoryMergeScheduler {
        return StoryMergeScheduler(
            executor = StoryMergeExecutor(useCase, StoryTestFixture.executionLock()),
            settings = StoryMergeSchedulerSettings(
                enabled = enabled,
                interval = Duration.ofMinutes(10),
                initialDelay = Duration.ZERO,
                scanWindow = Duration.ofHours(24),
                scanLimit = 200
            )
        )
    }
}

/** 호출 수만 세는 병합 유스케이스. */
private class CountingMergeUseCase : MergeOpenStoriesUseCase {
    var callCount: Int = 0
        private set

    override suspend fun mergeOpenStories(): MergeOpenStoriesResult {
        callCount += 1

        return MergeOpenStoriesResult(
            scannedCount = 0,
            mergedCount = 0,
            conflictedCount = 0,
            indexReassignFailureCount = 0
        )
    }
}

/** 항상 실패하는 병합 유스케이스. */
private class FailingMergeUseCase : MergeOpenStoriesUseCase {

    override suspend fun mergeOpenStories(): MergeOpenStoriesResult {
        throw IllegalStateException("merge failed")
    }
}
