package me.rgunny.kachi.notification.service.adapter.inbound.scheduler

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.notification.application.port.inbound.dispatch.model.PublishNotificationDispatchResult
import me.rgunny.kachi.notification.application.port.inbound.dispatch.PublishNotificationDispatchUseCase
import me.rgunny.kachi.notification.service.adapter.monitoring.NotificationServiceMetricContract
import me.rgunny.kachi.notification.service.adapter.monitoring.NotificationServiceMetrics
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("NotificationOutboxPublishScheduler")
class NotificationOutboxPublishSchedulerTest {

    @Test
    @DisplayName("publish 결과와 tick 처리 시간을 기록한다")
    fun recordPublishResult() {
        val registry = SimpleMeterRegistry()
        val scheduler = scheduler(
            registry = registry,
            action = {
                PublishNotificationDispatchResult(
                    processed = 3,
                    published = 2,
                    failed = 1,
                    publishTickCompletedAt = NOW,
                )
            },
        )

        scheduler.publishPending()

        assertEquals(
            2.0,
            registry.get(NotificationServiceMetricContract.Names.OUTBOX_PUBLISH)
                .tag("result", "published")
                .counter()
                .count(),
        )
        assertEquals(
            1L,
            registry.get(NotificationServiceMetricContract.Names.OUTBOX_PUBLISH_DURATION)
                .tag("result", "completed")
                .timer()
                .count(),
        )
    }

    @Test
    @DisplayName("publish 실패는 실패 처리 시간을 기록하고 다음 tick을 위해 반환한다")
    fun recordPublishFailure() {
        val registry = SimpleMeterRegistry()
        val scheduler = scheduler(registry) { throw IllegalStateException("publish failed") }

        scheduler.publishPending()

        assertEquals(
            1L,
            registry.get(NotificationServiceMetricContract.Names.OUTBOX_PUBLISH_DURATION)
                .tag("result", "tick_failed")
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
            scheduler.publishPending()
        }

        assertTrue(
            registry.find(NotificationServiceMetricContract.Names.OUTBOX_PUBLISH_DURATION)
                .timers()
                .isEmpty(),
        )
    }

    private fun scheduler(
        registry: SimpleMeterRegistry,
        action: suspend () -> PublishNotificationDispatchResult,
    ): NotificationOutboxPublishScheduler {
        return NotificationOutboxPublishScheduler(
            publishUseCase = StubPublishNotificationDispatchUseCase(action),
            metrics = NotificationServiceMetrics(registry),
        )
    }

    private class StubPublishNotificationDispatchUseCase(
        private val action: suspend () -> PublishNotificationDispatchResult,
    ) : PublishNotificationDispatchUseCase {
        override suspend fun publishPending(): PublishNotificationDispatchResult = action()
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-20T00:00:00Z")
    }
}
