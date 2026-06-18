package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import me.rgunny.kachi.notification.domain.NotificationChannel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NotificationDispatchMessageMapper")
class NotificationDispatchMessageMapperTest {

    @Test
    @DisplayName("dispatch payload를 core command로 변환한다")
    fun toCommand() {
        val notificationId = UUID.randomUUID()
        val payload = NotificationDispatchPayload(
            notificationId = notificationId.toString(),
            requestId = "request-1",
            channel = "SLACK",
            recipient = "C123",
            message = "hello",
        )

        val command = NotificationDispatchMessageMapper.toCommand(payload)

        assertEquals(notificationId, command.notificationId.id)
        assertEquals("request-1", command.requestId)
        assertEquals(NotificationChannel.SLACK, command.channel)
        assertEquals("C123", command.recipient)
        assertEquals("hello", command.message)
    }

    @Test
    @DisplayName("지원하지 않는 channel은 거부한다")
    fun rejectUnsupportedChannel() {
        val payload = NotificationDispatchPayload(
            notificationId = UUID.randomUUID().toString(),
            requestId = "request-1",
            channel = "UNKNOWN",
            recipient = "C123",
            message = "hello",
        )

        assertFailsWith<IllegalArgumentException> {
            NotificationDispatchMessageMapper.toCommand(payload)
        }
    }
}
