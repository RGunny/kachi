package me.rgunny.kachi.notification.worker.adapter.inbound.scheduler

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.notification.application.port.dto.RecoverStaleProcessingDispatchResult
import me.rgunny.kachi.notification.application.port.inbound.RecoverStaleProcessingDispatchUseCase
import me.rgunny.kachi.notification.worker.adapter.outbound.monitoring.NotificationWorkerMetricContract
import me.rgunny.kachi.notification.worker.adapter.outbound.monitoring.NotificationWorkerMetrics
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("NotificationDispatchRecoveryScheduler")
class NotificationDispatchRecoverySchedulerTest {

    @Test
    @DisplayName("recovery 결과와 tick 처리 시간을 기록한다")
    fun recordRecoveryResult() {
        val registry = SimpleMeterRegistry()
        val scheduler = scheduler(
            registry = registry,
            action = {
                RecoverStaleProcessingDispatchResult(
                    staleProcessingFound = 3,
                    staleProcessingRecovered = 2,
                    recoveredToRetryWait = 1,
                    recoveredToDead = 1,
                    staleProcessingSkipped = 1,
                    recoveryTickCompletedAt = NOW,
                )
            },
        )

        scheduler.recoverStaleProcessing()

        assertEquals(
            1.0,
            registry.get(NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY)
                .tag("result", "retry_wait")
                .counter()
                .count(),
        )
        assertEquals(
            1L,
            registry.get(NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY_DURATION)
                .tag("result", "completed")
                .timer()
                .count(),
        )
    }

    @Test
    @DisplayName("recovery 실패는 실패 처리 시간을 기록하고 다음 tick을 위해 반환한다")
    fun recordRecoveryFailure() {
        val registry = SimpleMeterRegistry()
        val scheduler = scheduler(registry) { throw IllegalStateException("recovery failed") }

        scheduler.recoverStaleProcessing()

        assertEquals(
            1L,
            registry.get(NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY_DURATION)
                .tag("result", "failed")
                .timer()
                .count(),
        )
    }

    @Test
    @DisplayName("coroutine 취소는 실패로 기록하지 않고 전파한다")
    fun propagateCancellation() {
        val registry = SimpleMeterRegistry()
        val scheduler = scheduler(registry) { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> {
            scheduler.recoverStaleProcessing()
        }

        assertTrue(
            registry.find(NotificationWorkerMetricContract.Names.PROCESSING_RECOVERY_DURATION)
                .timers()
                .isEmpty(),
        )
    }

    private fun scheduler(
        registry: SimpleMeterRegistry,
        action: suspend () -> RecoverStaleProcessingDispatchResult,
    ): NotificationDispatchRecoveryScheduler {
        return NotificationDispatchRecoveryScheduler(
            recoverUseCase = StubRecoverStaleProcessingDispatchUseCase(action),
            metrics = NotificationWorkerMetrics(registry),
        )
    }

    private class StubRecoverStaleProcessingDispatchUseCase(
        private val action: suspend () -> RecoverStaleProcessingDispatchResult,
    ) : RecoverStaleProcessingDispatchUseCase {
        override suspend fun recoverStaleProcessing(): RecoverStaleProcessingDispatchResult = action()
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-20T00:00:00Z")
    }
}
