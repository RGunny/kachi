package me.rgunny.kachi.collector.adapter.inbound.scheduler

import ch.qos.logback.classic.Level
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.adapter.inbound.outbox.CollectorOutboxRelayExecutor
import me.rgunny.kachi.collector.application.port.inbound.outbox.RelayCollectorOutboxUseCase
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RelayCollectorOutboxResult
import me.rgunny.kachi.collector.config.CollectorOutboxRelayConfig
import me.rgunny.kachi.collector.fake.BlockingRelayCollectorOutboxUseCase
import me.rgunny.kachi.collector.fake.FailingRelayCollectorOutboxUseCase
import me.rgunny.kachi.collector.fake.RecordingRelayCollectorOutboxUseCase
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import me.rgunny.kachi.collector.support.RecordingLogAppender
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("CollectorOutboxRelayScheduler")
class CollectorOutboxRelaySchedulerTest {

    @Test
    @DisplayName("처리한 행이 있으면 tick 집계를 남긴다")
    fun logTickSummaryWhenAnythingWasProcessed() = runBlocking {
        val useCase = RecordingRelayCollectorOutboxUseCase(result = relayResult(processed = 2, published = 2))

        val logs = relayWithLogs(schedulerOf(useCase))

        assertEquals(1, logs.filter { it.startsWith("Scheduled outbox relay finished") }.size)
    }

    @Test
    @DisplayName("다룰 행이 없는 tick은 로그를 남기지 않는다")
    fun logNothingWhenNothingWasProcessed() = runBlocking {
        val useCase = RecordingRelayCollectorOutboxUseCase(result = relayResult(processed = 0))

        val logs = relayWithLogs(schedulerOf(useCase))

        assertTrue(logs.isEmpty())
    }

    @Test
    @DisplayName("이미 실행 중인 tick이 있으면 실패로 보지 않고 이번 tick만 건너뛴다")
    fun logSkipWhenAnotherRelayIsRunning() = runBlocking {
        val useCase = BlockingRelayCollectorOutboxUseCase()
        val scheduler = schedulerOf(useCase)

        val running = async { scheduler.relay() }
        useCase.started.await()

        val logs = relayWithLogs(scheduler)
        useCase.release.complete(Unit)
        running.await()

        assertEquals(1, logs.filter { it.startsWith("Skip scheduled outbox relay") }.size)
    }

    @Test
    @DisplayName("relay 실행이 실패해도 scheduler 루프가 중단되지 않도록 예외를 전파하지 않는다")
    fun swallowExecutionFailure() = runBlocking {
        val useCase = FailingRelayCollectorOutboxUseCase()

        schedulerOf(useCase).relay()

        assertEquals(1, useCase.invokeCount)
    }

    /**
     * scheduler tick은 반환값이 없어 로그가 유일한 산출물이다.
     */
    private suspend fun relayWithLogs(scheduler: CollectorOutboxRelayScheduler): List<String> {
        return RecordingLogAppender.attachTo(CollectorOutboxRelayScheduler::class).use { appender ->
            scheduler.relay()
            appender.messagesAt(Level.INFO)
        }
    }

    private fun schedulerOf(useCase: RelayCollectorOutboxUseCase): CollectorOutboxRelayScheduler {
        return CollectorOutboxRelayScheduler(
            executor = CollectorOutboxRelayExecutor(useCase, CollectorTestFixture.executionLock()),
            settings = CollectorOutboxRelayConfig().collectorOutboxRelaySettings(CollectorTestFixture.relayProperties())
        )
    }

    private fun relayResult(
        processed: Int,
        published: Int = 0,
        retried: Int = 0,
        dead: Int = 0,
        staleRecovered: Int = 0
    ): RelayCollectorOutboxResult {
        return RelayCollectorOutboxResult(
            processed = processed,
            published = published,
            retried = retried,
            dead = dead,
            staleRecovered = staleRecovered,
            completedAt = CollectorTestFixture.NOW
        )
    }
}
