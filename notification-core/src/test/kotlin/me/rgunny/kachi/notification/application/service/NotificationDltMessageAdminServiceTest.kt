package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.inbound.dlt.model.DiscardNotificationDltMessageCommand
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageQuery
import me.rgunny.kachi.notification.domain.NotificationDltMessage
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.exception.InvalidNotificationDltMessageStateException
import me.rgunny.kachi.notification.exception.NotificationDltMessageNotFoundException
import me.rgunny.kachi.notification.fake.FakeNotificationDltMessageAdminPersistencePort
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.CLOCK
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.NOW
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
        val service = NotificationDltMessageAdminService(persistence, CLOCK)

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
            FakeNotificationDltMessageAdminPersistencePort(listOf(message)),
            CLOCK,
        )

        val result = service.get(message.id)

        assertEquals(message.id, result.messageId)
        assertEquals("""{"notificationId":"n1"}""", result.payload)
        assertEquals("timeout", result.exceptionMessage)
    }

    @Test
    @DisplayName("존재하지 않는 DLT 메시지 상세 조회는 실패한다")
    fun getNotFound() {
        val service = NotificationDltMessageAdminService(FakeNotificationDltMessageAdminPersistencePort(), CLOCK)

        assertFailsWith<NotificationDltMessageNotFoundException> {
            runSuspend {
                service.get(NotificationDltMessageId.fromOriginalRecord("notification.dispatch", 0, 999))
            }
        }
    }

    @Test
    @DisplayName("PENDING DLT 메시지를 DISCARDED로 폐기한다")
    fun discard() = runSuspend {
        val message = message()
        val service = NotificationDltMessageAdminService(
            FakeNotificationDltMessageAdminPersistencePort(listOf(message)),
            CLOCK,
        )

        val result = service.discard(
            DiscardNotificationDltMessageCommand(message.id, "operator discard")
        )

        assertEquals(NotificationDltMessageStatus.DISCARDED, result.status)
        assertEquals(NOW, result.discardedAt)
        assertEquals("operator discard", result.discardReason)
    }

    @Test
    @DisplayName("저장 시점에 PENDING 조건이 불일치하면 폐기 실패한다")
    fun discardMismatch() {
        val message = message()
        val persistence = FakeNotificationDltMessageAdminPersistencePort(listOf(message)).also {
            it.forceDiscardMismatch = true
        }
        val service = NotificationDltMessageAdminService(persistence, CLOCK)

        assertFailsWith<InvalidNotificationDltMessageStateException> {
            runSuspend {
                service.discard(DiscardNotificationDltMessageCommand(message.id, "operator discard"))
            }
        }
    }

    @Test
    @DisplayName("이미 처리된 DLT 메시지는 폐기할 수 없다")
    fun discardAlreadyClosed() {
        val message = message().discard(NOW.minusSeconds(1), "already discarded")
        val service = NotificationDltMessageAdminService(
            FakeNotificationDltMessageAdminPersistencePort(listOf(message)),
            CLOCK,
        )

        val exception = assertFailsWith<InvalidNotificationDltMessageStateException> {
            runSuspend {
                service.discard(DiscardNotificationDltMessageCommand(message.id, "operator discard"))
            }
        }

        assertEquals(NotificationDltMessageStatus.DISCARDED, exception.currentStatus)
    }

    private fun message(): NotificationDltMessage {
        return NotificationDltMessage.record(
            originalTopic = "notification.dispatch",
            originalPartition = 0,
            originalOffset = 100,
            originalTimestamp = Instant.parse("2026-07-12T23:59:59Z"),
            dltTopic = "notification.dispatch.dlt",
            dltPartition = 0,
            dltOffset = 200,
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
