package me.rgunny.kachi.notification.service.adapter.inbound.messaging

import me.rgunny.kachi.notification.contract.NotificationRequestedEvent
import me.rgunny.kachi.notification.domain.NotificationChannel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NotificationRequestedEventMapper")
class NotificationRequestedEventMapperTest {

    @Test
    @DisplayName("notification.requested 계약 이벤트를 core command로 변환한다")
    fun toCommand() {
        val event = NotificationRequestedEvent(
            requestId = "request-1",
            requester = "collector-service",
            channel = me.rgunny.kachi.notification.contract.NotificationChannel.SLACK,
            recipient = "C123",
            message = "hello",
        )

        val command = NotificationRequestedEventMapper.toCommand(event)

        assertEquals("request-1", command.requestId)
        assertEquals("collector-service", command.requester)
        assertEquals(NotificationChannel.SLACK, command.channel)
        assertEquals("C123", command.recipient)
        assertEquals("hello", command.message)
    }

    @Test
    @DisplayName("지원하지 않는 schemaVersion은 거부한다")
    fun rejectUnsupportedSchemaVersion() {
        val event = NotificationRequestedEvent(
            schemaVersion = 999,
            requestId = "request-1",
            requester = "collector-service",
            channel = me.rgunny.kachi.notification.contract.NotificationChannel.SLACK,
            recipient = "C123",
            message = "hello",
        )

        assertFailsWith<IllegalArgumentException> {
            NotificationRequestedEventMapper.toCommand(event)
        }
    }
}
