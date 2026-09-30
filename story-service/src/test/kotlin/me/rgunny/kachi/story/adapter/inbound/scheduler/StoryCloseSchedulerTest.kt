package me.rgunny.kachi.story.adapter.inbound.scheduler

import java.time.Duration
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.inbound.close.StoryCloseExecutor
import me.rgunny.kachi.story.application.port.inbound.close.CloseIdleStoriesUseCase
import me.rgunny.kachi.story.application.port.inbound.close.model.CloseIdleStoriesResult
import me.rgunny.kachi.story.fixture.StoryTestFixture
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryCloseScheduler")
class StoryCloseSchedulerTest {

    @Test
    @DisplayName("enabled=false이면 닫기를 실행하지 않는다")
    fun doNotCloseWhenDisabled() = runBlocking {
        val useCase = CountingCloseUseCase()
        val scheduler = scheduler(useCase, enabled = false)

        scheduler.closeIdleStories()

        assertEquals(0, useCase.callCount)
    }

    @Test
    @DisplayName("enabled=true이면 닫기를 실행한다")
    fun closeWhenEnabled() = runBlocking {
        val useCase = CountingCloseUseCase()
        val scheduler = scheduler(useCase, enabled = true)

        scheduler.closeIdleStories()

        assertEquals(1, useCase.callCount)
    }

    @Test
    @DisplayName("닫기 예외를 scheduler 밖으로 전파하지 않는다")
    fun swallowCloseFailure() = runBlocking {
        val scheduler = scheduler(FailingCloseUseCase(), enabled = true)

        scheduler.closeIdleStories()
    }

    private fun scheduler(useCase: CloseIdleStoriesUseCase, enabled: Boolean): StoryCloseScheduler {
        return StoryCloseScheduler(
            executor = StoryCloseExecutor(useCase, StoryTestFixture.executionLock()),
            settings = StoryCloseSchedulerSettings(
                enabled = enabled,
                interval = Duration.ofMinutes(10),
                initialDelay = Duration.ZERO,
                closeAfter = Duration.ofHours(48),
                batchLimit = 100
            )
        )
    }
}

/** 호출 수만 세는 닫기 유스케이스. */
private class CountingCloseUseCase : CloseIdleStoriesUseCase {
    var callCount: Int = 0
        private set

    override suspend fun closeIdleStories(): CloseIdleStoriesResult {
        callCount += 1

        return CloseIdleStoriesResult(
            threshold = NOW.minus(Duration.ofHours(48)),
            closedCount = 0,
            conflictedCount = 0,
            indexDeleteFailureCount = 0
        )
    }
}

/** 항상 실패하는 닫기 유스케이스. */
private class FailingCloseUseCase : CloseIdleStoriesUseCase {

    override suspend fun closeIdleStories(): CloseIdleStoriesResult {
        throw IllegalStateException("close failed")
    }
}
