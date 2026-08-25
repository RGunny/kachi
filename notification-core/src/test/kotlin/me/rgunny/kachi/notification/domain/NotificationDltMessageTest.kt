package me.rgunny.kachi.notification.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("NotificationDltMessage")
class NotificationDltMessageTest {
    private val now = Instant.parse("2026-07-13T00:00:00Z")

    @Test
    @DisplayName("폐기하면 DISCARDED 인스턴스를 새로 만들고 원본은 그대로 둔다")
    fun discard() {
        val message = message()
        val discardedAt = now.plusSeconds(10)

        val discarded = message.discard(discardedAt, "operator discard")

        assertEquals(NotificationDltMessageStatus.DISCARDED, discarded.status)
        assertEquals(discardedAt, discarded.discardedAt)
        assertEquals("operator discard", discarded.discardReason)

        assertEquals(NotificationDltMessageStatus.PENDING, message.status)
        assertNull(message.discardedAt)
        assertNull(message.discardReason)
    }

    @Test
    @DisplayName("허용되지 않은 상태 전이는 예외로 차단한다")
    fun rejectInvalidTransitions() {
        val discarded = message().discard(now, "operator discard")

        assertFailsWith<IllegalStateException> {
            discarded.discard(now.plusSeconds(1), "operator discard")
        }
        assertFailsWith<IllegalArgumentException> {
            message().discard(now, " ")
        }
    }

    private fun message(): NotificationDltMessage {
        return NotificationDltMessage.record(
            originalTopic = "notification.dispatch",
            originalPartition = 0,
            originalOffset = 100,
            originalTimestamp = now.minusSeconds(1),
            dltTopic = "notification.dispatch.dlt",
            dltPartition = 0,
            dltOffset = 200,
            consumerGroup = "notification-worker",
            messageKey = "key-1",
            payload = """{"notificationId":"n1"}""",
            exceptionFqcn = "java.net.SocketTimeoutException",
            exceptionMessage = "timeout",
            deadLetteredAt = now,
            storedAt = now.plusSeconds(1),
        )
    }
}
