package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.dto.dlt.PersistNotificationDltMessageCommand
import me.rgunny.kachi.notification.application.port.dto.dlt.PersistNotificationDltMessageResult
import me.rgunny.kachi.notification.application.port.inbound.PersistNotificationDltMessageUseCase
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.worker.adapter.outbound.monitoring.NotificationWorkerMetricContract
import me.rgunny.kachi.notification.worker.adapter.outbound.monitoring.NotificationWorkerMetrics
import me.rgunny.kachi.notification.worker.fake.FakeAcknowledgment
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NotificationDispatchDltKafkaListener")
class NotificationDispatchDltKafkaListenerTest {

    @Test
    @DisplayName("DLT 메시지를 저장한 뒤 ack 처리한다")
    fun consume() {
        val useCase = CapturingPersistNotificationDltMessageUseCase()
        val registry = SimpleMeterRegistry()
        val listener = NotificationDispatchDltKafkaListener(
            persistDltMessageUseCase = useCase,
            mapper = NotificationDispatchDltMessageMapper(),
            metrics = NotificationWorkerMetrics(registry),
        )
        val acknowledgment = FakeAcknowledgment()
        val record = ConsumerRecord("notification.dispatch.dlt", 0, 10, "key-1", """{"notificationId":"n1"}""")

        listener.consume(record, record.value(), acknowledgment)

        assertEquals(true, acknowledgment.acked)
        assertEquals("notification.dispatch.dlt", useCase.lastCommand?.dltTopic)
        assertEquals(10, useCase.lastCommand?.dltOffset)
        assertEquals(
            1.0,
            registry.get(NotificationWorkerMetricContract.Names.DLT_PERSIST)
                .tag("result", "persisted")
                .counter()
                .count(),
        )
    }

    @Test
    @DisplayName("DLT 저장 실패는 실패 metric을 기록하고 ack하지 않는다")
    fun persistFailure() {
        val registry = SimpleMeterRegistry()
        val listener = NotificationDispatchDltKafkaListener(
            persistDltMessageUseCase = FailingPersistNotificationDltMessageUseCase(),
            mapper = NotificationDispatchDltMessageMapper(),
            metrics = NotificationWorkerMetrics(registry),
        )
        val acknowledgment = FakeAcknowledgment()
        val record = ConsumerRecord("notification.dispatch.dlt", 0, 10, "key-1", "payload")

        assertFailsWith<IllegalStateException> {
            listener.consume(record, record.value(), acknowledgment)
        }

        assertEquals(false, acknowledgment.acked)
        assertEquals(
            1.0,
            registry.get(NotificationWorkerMetricContract.Names.DLT_PERSIST)
                .tag("result", "persist_failed")
                .counter()
                .count(),
        )
    }

    private class CapturingPersistNotificationDltMessageUseCase : PersistNotificationDltMessageUseCase {
        var lastCommand: PersistNotificationDltMessageCommand? = null
            private set

        override suspend fun persist(
            command: PersistNotificationDltMessageCommand
        ): PersistNotificationDltMessageResult {
            lastCommand = command
            return PersistNotificationDltMessageResult(
                messageId = NotificationDltMessageId.fromOriginalRecord(
                    command.originalTopic,
                    command.originalPartition,
                    command.originalOffset,
                ),
                status = NotificationDltMessageStatus.PENDING,
            )
        }
    }

    private class FailingPersistNotificationDltMessageUseCase : PersistNotificationDltMessageUseCase {
        override suspend fun persist(
            command: PersistNotificationDltMessageCommand
        ): PersistNotificationDltMessageResult {
            throw IllegalStateException("persist failed")
        }
    }
}
