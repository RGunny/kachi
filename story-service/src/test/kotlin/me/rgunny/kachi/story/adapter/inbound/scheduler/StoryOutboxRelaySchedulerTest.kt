package me.rgunny.kachi.story.adapter.inbound.scheduler

import java.time.Duration
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.inbound.outbox.StoryOutboxRelayExecutor
import me.rgunny.kachi.story.application.port.inbound.outbox.RelayStoryOutboxUseCase
import me.rgunny.kachi.story.fake.BlockingRelayStoryOutboxUseCase
import me.rgunny.kachi.story.fake.FailingRelayStoryOutboxUseCase
import me.rgunny.kachi.story.fake.RecordingRelayStoryOutboxUseCase
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryOutboxRelayScheduler")
class StoryOutboxRelaySchedulerTest {

    @Test
    @DisplayName("tick마다 relay를 실행한다")
    fun relayOnTick() = runBlocking {
        val useCase = RecordingRelayStoryOutboxUseCase()
        val scheduler = schedulerOf(useCase)

        scheduler.relay()
        scheduler.relay()

        assertEquals(2, useCase.invokeCount)
    }

    @Test
    @DisplayName("이미 실행 중인 tick이 있으면 실패로 보지 않고 이번 tick만 건너뛴다")
    fun skipWhenAnotherRelayIsRunning() = runBlocking {
        val useCase = BlockingRelayStoryOutboxUseCase()
        val scheduler = schedulerOf(useCase)

        val running = async { scheduler.relay() }
        useCase.started.await()

        scheduler.relay()
        useCase.release.complete(Unit)
        running.await()

        assertEquals(1, useCase.executeCount)
    }

    @Test
    @DisplayName("relay 실행이 실패해도 scheduler 루프가 중단되지 않도록 예외를 전파하지 않는다")
    fun swallowExecutionFailure() = runBlocking {
        val useCase = FailingRelayStoryOutboxUseCase()

        schedulerOf(useCase).relay()

        assertEquals(1, useCase.invokeCount)
    }

    private fun schedulerOf(useCase: RelayStoryOutboxUseCase): StoryOutboxRelayScheduler {
        return StoryOutboxRelayScheduler(
            executor = StoryOutboxRelayExecutor(useCase, StoryTestFixture.executionLock()),
            settings = StoryOutboxRelaySchedulerSettings(
                publisherId = StoryTestFixture.RELAY_PUBLISHER_ID,
                fixedDelay = Duration.ofSeconds(5),
                initialDelay = Duration.ofSeconds(15),
                batchSize = 50,
                publishingVisibilityTimeout = Duration.ofSeconds(60)
            )
        )
    }
}
