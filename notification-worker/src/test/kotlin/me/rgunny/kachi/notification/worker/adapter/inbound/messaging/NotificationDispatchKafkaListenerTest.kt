package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import me.rgunny.kachi.notification.application.port.dto.DispatchFailureClassification
import me.rgunny.kachi.notification.application.port.dto.DispatchNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.DispatchNotificationResult
import me.rgunny.kachi.notification.application.port.inbound.DispatchNotificationUseCase
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryFailureCode
import me.rgunny.kachi.notification.worker.adapter.inbound.messaging.exception.InvalidDispatchMessageException
import me.rgunny.kachi.notification.worker.adapter.inbound.messaging.exception.RetryableDispatchMessageException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NotificationDispatchKafkaListener")
class NotificationDispatchKafkaListenerTest {

    @Test
    @DisplayName("NONE 결과는 ack 처리한다")
    fun ackWhenNone() {
        val useCase = StubDispatchNotificationUseCase(
            result = result(DispatchFailureClassification.NONE),
        )
        val acknowledgment = FakeAcknowledgment()
        val listener = listener(useCase)

        listener.consume(payload(), acknowledgment)

        assertEquals(true, acknowledgment.acked)
    }

    @Test
    @DisplayName("RETRYABLE 결과는 retry 예외로 변환하고 ack하지 않는다")
    fun throwWhenRetryable() {
        val useCase = StubDispatchNotificationUseCase(
            result = result(DispatchFailureClassification.RETRYABLE),
        )
        val acknowledgment = FakeAcknowledgment()
        val listener = listener(useCase)

        assertFailsWith<RetryableDispatchMessageException> {
            listener.consume(payload(), acknowledgment)
        }
        assertEquals(false, acknowledgment.acked)
    }

    @Test
    @DisplayName("잘못된 payload는 invalid dispatch message 예외로 분류하고 ack하지 않는다")
    fun rejectInvalidPayload() {
        val acknowledgment = FakeAcknowledgment()
        val listener = listener(StubDispatchNotificationUseCase(result(DispatchFailureClassification.NONE)))

        assertFailsWith<InvalidDispatchMessageException> {
            listener.consume("{ invalid-json", acknowledgment)
        }
        assertEquals(false, acknowledgment.acked)
    }

    private fun listener(useCase: DispatchNotificationUseCase): NotificationDispatchKafkaListener {
        return NotificationDispatchKafkaListener(
            dispatchUseCase = useCase,
            jsonMapper = JsonMapper.builder().findAndAddModules().build(),
        )
    }

    private fun payload(): String {
        return """
            {
              "notificationId": "${UUID.randomUUID()}",
              "requestId": "request-1",
              "channel": "SLACK",
              "recipient": "C123",
              "message": "hello"
            }
        """.trimIndent()
    }

    private fun result(classification: DispatchFailureClassification): DispatchNotificationResult {
        return DispatchNotificationResult(
            notificationId = NotificationId.newId(),
            status = when (classification) {
                DispatchFailureClassification.RETRYABLE -> NotificationStatus.RETRY_WAIT
                else -> NotificationStatus.SENT
            },
            duplicated = false,
            dispatchAttempted = true,
            handledAt = Instant.parse("2026-06-17T00:00:00Z"),
            failureClassification = classification,
            failure = if (classification == DispatchFailureClassification.RETRYABLE) {
                RetryFailure.of(
                    code = RetryFailureCode.VENDOR_TIMEOUT,
                    message = "timeout",
                )
            } else {
                null
            },
        )
    }

    private class StubDispatchNotificationUseCase(
        private val result: DispatchNotificationResult,
    ) : DispatchNotificationUseCase {
        override suspend fun dispatch(command: DispatchNotificationCommand): DispatchNotificationResult {
            return result
        }
    }

    private class FakeAcknowledgment : Acknowledgment {
        var acked: Boolean = false
            private set

        override fun acknowledge() {
            acked = true
        }
    }
}
