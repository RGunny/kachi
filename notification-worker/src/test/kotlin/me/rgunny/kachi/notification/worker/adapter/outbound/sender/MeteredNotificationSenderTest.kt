package me.rgunny.kachi.notification.worker.adapter.outbound.sender

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationResult
import me.rgunny.kachi.notification.application.port.outbound.sender.NotificationSender
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.worker.adapter.outbound.monitoring.NotificationWorkerMetricContract
import me.rgunny.kachi.notification.worker.adapter.outbound.monitoring.NotificationWorkerMetrics
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("MeteredNotificationSender")
class MeteredNotificationSenderTest {

    @Test
    @DisplayName("delegate 결과를 그대로 반환하고 sender metric을 기록한다")
    fun recordDelegateResult() = runBlocking {
        val registry = SimpleMeterRegistry()
        val sender = sender(registry) { SendNotificationResult.Success("message-1") }

        val result = sender.send(COMMAND)

        assertEquals(SendNotificationResult.Success("message-1"), result)
        assertEquals(true, sender.supports(NotificationChannel.SLACK))
        assertEquals(
            1.0,
            registry.get(NotificationWorkerMetricContract.Names.SENDER)
                .tags("channel", "SLACK", "result", "success", "failure_category", "NONE")
                .counter()
                .count(),
        )
    }

    @Test
    @DisplayName("예상하지 못한 delegate 예외를 기록하고 그대로 전파한다")
    fun recordUnexpectedException() {
        val registry = SimpleMeterRegistry()
        val sender = sender(registry) { throw IllegalStateException("send failed") }

        assertFailsWith<IllegalStateException> {
            runBlocking { sender.send(COMMAND) }
        }

        assertEquals(
            1.0,
            registry.get(NotificationWorkerMetricContract.Names.SENDER)
                .tags("channel", "SLACK", "result", "unexpected", "failure_category", "UNKNOWN")
                .counter()
                .count(),
        )
    }

    @Test
    @DisplayName("coroutine 취소는 실패로 기록하지 않고 전파한다")
    fun propagateCancellation() {
        val registry = SimpleMeterRegistry()
        val sender = sender(registry) { throw CancellationException("cancelled") }

        assertFailsWith<CancellationException> {
            runBlocking { sender.send(COMMAND) }
        }

        assertTrue(registry.find(NotificationWorkerMetricContract.Names.SENDER).counters().isEmpty())
    }

    private fun sender(
        registry: SimpleMeterRegistry,
        action: suspend () -> SendNotificationResult,
    ): MeteredNotificationSender {
        return MeteredNotificationSender(
            delegate = StubNotificationSender(action),
            metrics = NotificationWorkerMetrics(registry),
        )
    }

    private class StubNotificationSender(
        private val action: suspend () -> SendNotificationResult,
    ) : NotificationSender {
        override fun supports(channel: NotificationChannel): Boolean = channel == NotificationChannel.SLACK

        override suspend fun send(command: SendNotificationCommand): SendNotificationResult = action()
    }

    private companion object {
        val COMMAND = SendNotificationCommand(
            notificationId = NotificationId.newId(),
            channel = NotificationChannel.SLACK,
            recipientId = "user-1",
            message = "hello",
            idempotencyKey = "idempotency-1",
        )
    }
}
