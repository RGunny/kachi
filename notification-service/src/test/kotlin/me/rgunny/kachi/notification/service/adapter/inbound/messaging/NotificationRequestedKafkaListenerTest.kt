package me.rgunny.kachi.notification.service.adapter.inbound.messaging

import me.rgunny.kachi.notification.application.port.dto.RequestNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.RequestNotificationResult
import me.rgunny.kachi.notification.application.port.inbound.RequestNotificationUseCase
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NotificationRequestedKafkaListener")
class NotificationRequestedKafkaListenerTest {

    private val useCase = CapturingRequestNotificationUseCase()
    private val listener = NotificationRequestedKafkaListener(
        requestNotificationUseCase = useCase,
        jsonMapper = JsonMapper.builder().findAndAddModules().build(),
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
              "recipient": "C123",
              "message": "hello"
            }
            """.trimIndent()
        )

        val command = useCase.lastCommand
        requireNotNull(command)
        assertEquals("request-1", command.requestId)
        assertEquals("collector-service", command.requester)
        assertEquals("C123", command.recipient)
        assertEquals("hello", command.message)
    }

    @Test
    @DisplayName("JSON 파싱 실패는 invalid requested message 예외로 분류한다")
    fun rejectInvalidJson() {
        assertFailsWith<InvalidNotificationRequestedMessageException> {
            listener.consume("{ invalid-json")
        }
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
                  "recipient": "C123",
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
