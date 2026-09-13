package me.rgunny.kachi.story.adapter.inbound.scheduler

import java.time.Duration
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.inbound.cleanup.IndexCleanupExecutor
import me.rgunny.kachi.story.application.port.inbound.cleanup.CleanupCandidateIndexUseCase
import me.rgunny.kachi.story.application.port.inbound.cleanup.model.CleanupCandidateIndexResult
import me.rgunny.kachi.story.fixture.StoryTestFixture
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("IndexCleanupScheduler")
class IndexCleanupSchedulerTest {

    @Test
    @DisplayName("enabled=false이면 색인 정리를 실행하지 않는다")
    fun doNotCleanupWhenDisabled() = runBlocking {
        val useCase = CountingCleanupUseCase()
        val scheduler = scheduler(useCase, enabled = false)

        scheduler.cleanupCandidateIndex()

        assertEquals(0, useCase.callCount)
    }

    @Test
    @DisplayName("enabled=true이면 색인 정리를 실행한다")
    fun cleanupWhenEnabled() = runBlocking {
        val useCase = CountingCleanupUseCase()
        val scheduler = scheduler(useCase, enabled = true)

        scheduler.cleanupCandidateIndex()

        assertEquals(1, useCase.callCount)
    }

    @Test
    @DisplayName("색인 정리 예외를 scheduler 밖으로 전파하지 않는다")
    fun swallowCleanupFailure() = runBlocking {
        val scheduler = scheduler(FailingCleanupUseCase(), enabled = true)

        scheduler.cleanupCandidateIndex()
    }

    private fun scheduler(useCase: CleanupCandidateIndexUseCase, enabled: Boolean): IndexCleanupScheduler {
        return IndexCleanupScheduler(
            executor = IndexCleanupExecutor(useCase, StoryTestFixture.executionLock()),
            settings = IndexCleanupSchedulerSettings(
                enabled = enabled,
                interval = Duration.ofHours(1),
                initialDelay = Duration.ZERO
            )
        )
    }
}

/** 호출 수만 세는 색인 정리 유스케이스. */
private class CountingCleanupUseCase : CleanupCandidateIndexUseCase {
    var callCount: Int = 0
        private set

    override suspend fun cleanup(): CleanupCandidateIndexResult {
        callCount += 1

        return CleanupCandidateIndexResult(threshold = NOW.minus(Duration.ofHours(72)))
    }
}

/** 항상 실패하는 색인 정리 유스케이스. */
private class FailingCleanupUseCase : CleanupCandidateIndexUseCase {

    override suspend fun cleanup(): CleanupCandidateIndexResult {
        throw IllegalStateException("cleanup failed")
    }
}
