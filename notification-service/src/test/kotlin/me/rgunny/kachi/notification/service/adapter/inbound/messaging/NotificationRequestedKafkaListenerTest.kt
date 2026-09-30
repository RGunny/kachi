package me.rgunny.kachi.notification.service.adapter.inbound.messaging

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import me.rgunny.kachi.notification.application.port.inbound.request.model.RequestNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.request.model.RequestNotificationResult
import me.rgunny.kachi.notification.application.port.inbound.request.RequestNotificationUseCase
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.service.adapter.monitoring.NotificationServiceMetricContract
import me.rgunny.kachi.notification.service.adapter.monitoring.NotificationServiceMetrics
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NotificationRequestedKafkaListener")
class NotificationRequestedKafkaListenerTest {

    private val useCase = CapturingRequestNotificationUseCase()
    private val registry = SimpleMeterRegistry()
    private val listener = NotificationRequestedKafkaListener(
        requestNotificationUseCase = useCase,
        jsonMapper = JsonMapper.builder().findAndAddModules().build(),
        metrics = NotificationServiceMetrics(registry),
    )

    @Test
    @DisplayName("정상 payload는 request use case로 전달한다")
    fun consume() {
        listener.consume(
            """
            {
              "schemaVersion": 1,
              "requestId": "request-1",
              "requester": "collector-service",
              "channel": "SLACK",
              "recipientId": "user-1",
              "message": "hello"
            }
            """.trimIndent()
        )

        val command = useCase.lastCommand
        requireNotNull(command)
        assertEquals("request-1", command.requestId)
        assertEquals("collector-service", command.requester)
        assertEquals("user-1", command.recipientId)
        assertEquals("hello", command.message)
        assertEquals(
            1.0,
            registry.get(NotificationServiceMetricContract.Names.REQUEST)
                .tags("source", "kafka", "channel", "SLACK", "result", "accepted")
                .counter()
                .count(),
        )
    }

    @Test
    @DisplayName("JSON 파싱 실패는 invalid requested message 예외로 분류한다")
    fun rejectInvalidJson() {
        assertFailsWith<InvalidNotificationRequestedMessageException> {
            listener.consume("{ invalid-json")
        }
        assertEquals(
            1.0,
            registry.get(NotificationServiceMetricContract.Names.REQUEST)
                .tags("source", "kafka", "channel", "UNKNOWN", "result", "invalid")
                .counter()
                .count(),
        )
    }

    @Test
    @DisplayName("지원하지 않는 schemaVersion은 invalid requested message 예외로 분류한다")
    fun rejectUnsupportedSchemaVersion() {
        assertFailsWith<InvalidNotificationRequestedMessageException> {
            listener.consume(
                """
                {
                  "schemaVersion": 999,
                  "requestId": "request-1",
                  "requester": "collector-service",
                  "channel": "SLACK",
                  "recipientId": "user-1",
                  "message": "hello"
                }
                """.trimIndent()
            )
        }
    }

    private class CapturingRequestNotificationUseCase : RequestNotificationUseCase {
        var lastCommand: RequestNotificationCommand? = null
            private set

        override suspend fun request(command: RequestNotificationCommand): RequestNotificationResult {
            lastCommand = command
            return RequestNotificationResult(
                notificationId = NotificationId.newId(),
                status = NotificationStatus.REQUESTED,
                duplicated = false,
                acceptedAt = Instant.parse("2026-06-17T00:00:00Z"),
            )
        }
    }
}
