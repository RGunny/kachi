package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageQuery
import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.exception.NotificationDltMessageNotFoundException
import me.rgunny.kachi.notification.fake.FakeNotificationDltMessageAdminPersistencePort
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NotificationDltMessageAdminService")
class NotificationDltMessageAdminServiceTest {

    @Test
    @DisplayName("상태별 DLT 메시지 목록을 조회한다")
    fun find() = runSuspend {
        val message = message()
        val persistence = FakeNotificationDltMessageAdminPersistencePort(listOf(message))
        val service = NotificationDltMessageAdminService(persistence)

        val result = service.find(
            NotificationDltMessageQuery(
                status = NotificationDltMessageStatus.PENDING,
                batchSize = 10,
            )
        )

        assertEquals(NotificationDltMessageStatus.PENDING, persistence.lastStatus)
        assertEquals(10, persistence.lastBatchSize)
        assertEquals(1, result.messages.size)
        assertEquals(message.id, result.messages.single().messageId)
        assertEquals("java.net.SocketTimeoutException", result.messages.single().exceptionFqcn)
    }

    @Test
    @DisplayName("DLT 메시지 상세를 조회한다")
    fun get() = runSuspend {
        val message = message()
        val service = NotificationDltMessageAdminService(
            FakeNotificationDltMessageAdminPersistencePort(listOf(message))
        )

        val result = service.get(message.id)

        assertEquals(message.id, result.messageId)
        assertEquals("""{"notificationId":"n1"}""", result.payload)
        assertEquals("timeout", result.exceptionMessage)
    }

    @Test
    @DisplayName("존재하지 않는 DLT 메시지 상세 조회는 실패한다")
    fun getNotFound() {
        val service = NotificationDltMessageAdminService(FakeNotificationDltMessageAdminPersistencePort())

        assertFailsWith<NotificationDltMessageNotFoundException> {
            runSuspend {
                service.get(NotificationDltMessageId.fromOriginalRecord("notification.dispatch", 0, 999))
            }
        }
    }

    private fun message(): NotificationDltMessage {
        return NotificationDltMessage.record(
            originalTopic = "notification.dispatch",
            originalPartition = 0,
            originalOffset = 100,
            dltTopic = "notification.dispatch.dlt",
            dltPartition = 0,
            dltOffset = 200,
            consumerGroup = "notification-worker",
            messageKey = "key-1",
            payload = """{"notificationId":"n1"}""",
            exceptionFqcn = "java.net.SocketTimeoutException",
            exceptionMessage = "timeout",
            failedAt = Instant.parse("2026-07-13T00:00:00Z"),
            receivedAt = Instant.parse("2026-07-13T00:00:01Z"),
        )
    }
}
