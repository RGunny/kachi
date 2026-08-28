package me.rgunny.kachi.notification.service.adapter.inbound.messaging

import me.rgunny.kachi.notification.contract.NotificationRequestedEvent
import me.rgunny.kachi.notification.contract.NotificationRequestedOrigin
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationOrigin
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
        assertEquals(NotificationOrigin.NONE, command.origin)
    }

    @Test
    @DisplayName("계약의 origin을 NotificationOrigin으로 옮긴다")
    fun toCommandWithOrigin() {
        val event = NotificationRequestedEvent(
            requestId = "sum:summary-1:u:user-1:c:SLACK",
            requester = "notification-routing",
            channel = me.rgunny.kachi.notification.contract.NotificationChannel.SLACK,
            recipient = "ref-1",
            message = "hello",
            origin = NotificationRequestedOrigin(summaryId = "summary-1", keyword = "tesla", userId = "user-1"),
        )

        val command = NotificationRequestedEventMapper.toCommand(event)

        assertEquals(NotificationOrigin("summary-1", "tesla", "user-1"), command.origin)
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
