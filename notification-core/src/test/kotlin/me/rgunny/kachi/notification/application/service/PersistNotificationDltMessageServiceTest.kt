package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.dto.dlt.PersistNotificationDltMessageCommand
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.fake.FakeNotificationDltMessagePersistencePort
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

@DisplayName("PersistNotificationDltMessageService")
class PersistNotificationDltMessageServiceTest {

    @Test
    @DisplayName("DLT 메시지를 PENDING 상태로 저장한다")
    fun persist() = runSuspend {
        val persistence = FakeNotificationDltMessagePersistencePort()
        val service = PersistNotificationDltMessageService(persistence)

        val result = service.persist(command())

        assertEquals(NotificationDltMessageStatus.PENDING, result.status)
        assertEquals(1, persistence.saved.size)
        assertEquals("notification.dispatch", persistence.saved.single().originalTopic)
        assertEquals("notification.dispatch.dlt", persistence.saved.single().dltTopic)
        assertEquals("timeout", persistence.saved.single().exceptionMessage)
    }

    private fun command(): PersistNotificationDltMessageCommand {
        return PersistNotificationDltMessageCommand(
            originalTopic = "notification.dispatch",
            originalPartition = 0,
            originalOffset = 10,
            originalTimestamp = Instant.parse("2026-07-12T23:59:59Z"),
            dltTopic = "notification.dispatch.dlt",
            dltPartition = 0,
            dltOffset = 20,
            consumerGroup = "notification-worker",
            messageKey = "key-1",
            payload = """{"notificationId":"n1"}""",
            exceptionFqcn = "java.net.SocketTimeoutException",
            exceptionMessage = "timeout",
            deadLetteredAt = Instant.parse("2026-07-13T00:00:00Z"),
            storedAt = Instant.parse("2026-07-13T00:00:01Z"),
        )
    }
}
