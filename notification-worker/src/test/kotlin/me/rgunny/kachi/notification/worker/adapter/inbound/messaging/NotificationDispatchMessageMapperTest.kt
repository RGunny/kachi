package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import me.rgunny.kachi.notification.contract.NotificationDispatchEvent
import me.rgunny.kachi.notification.contract.NotificationChannel as ContractNotificationChannel
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
        val payload = NotificationDispatchEvent(
            notificationId = notificationId.toString(),
            requestId = "request-1",
            channel = ContractNotificationChannel.SLACK,
            recipientId = "user-1",
            message = "hello",
        )

        val command = NotificationDispatchMessageMapper.toCommand(payload)

        assertEquals(notificationId, command.notificationId.id)
        assertEquals("request-1", command.requestId)
        assertEquals(NotificationChannel.SLACK, command.channel)
        assertEquals("user-1", command.recipientId)
        assertEquals("hello", command.message)
    }

    @Test
    @DisplayName("contract channel 이름으로 core channel을 변환한다")
    fun mapContractChannelByName() {
        val payload = NotificationDispatchEvent(
            notificationId = UUID.randomUUID().toString(),
            requestId = "request-1",
            channel = ContractNotificationChannel.KAKAO,
            recipientId = "user-1",
            message = "hello",
        )

        val command = NotificationDispatchMessageMapper.toCommand(payload)

        assertEquals(NotificationChannel.KAKAO, command.channel)
    }
}
