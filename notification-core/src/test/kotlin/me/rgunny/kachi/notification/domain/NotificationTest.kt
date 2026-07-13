package me.rgunny.kachi.notification.domain

import me.rgunny.kachi.notification.fixture.NotificationTestFixture.MESSAGE
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.NOW
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.REQUESTER
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.REQUEST_ID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("Notification")
class NotificationTest {
    private val now = NOW

    @Test
    @DisplayName("요청 필수값을 검증하고 REQUESTED 상태로 생성한다")
    fun request() {
        val notification = notification()

        assertEquals(NotificationStatus.REQUESTED, notification.status)
        assertEquals(now, notification.requestedAt)
        assertEquals(now, notification.updatedAt)
        assertEquals(0, notification.dispatchAttempts)
        assertNull(notification.failureReason)
        assertTrue(notification.uncommittedHistories.isEmpty())

        assertFailsWith<IllegalArgumentException> {
            Notification.request("", "api", NotificationChannel.SLACK, "user", "message", now)
        }
        assertFailsWith<IllegalArgumentException> {
            Notification.request(REQUEST_ID, " ", NotificationChannel.SLACK, "user", "message", now)
        }
        assertFailsWith<IllegalArgumentException> {
            Notification.request(REQUEST_ID, REQUESTER, NotificationChannel.SLACK, "", "message", now)
        }
        assertFailsWith<IllegalArgumentException> {
            Notification.request(REQUEST_ID, REQUESTER, NotificationChannel.SLACK, "user", " ", now)
        }
    }

    @Test
    @DisplayName("발행 성공과 실패 상태를 기록하고 허용된 멱등 호출은 무시한다")
    fun publishTransitions() {
        val publishedAt = now.plusSeconds(1)
        val notification = notification()

        notification.markPublished(publishedAt)
        notification.markPublished(publishedAt.plusSeconds(1))

        assertEquals(NotificationStatus.PUBLISHED, notification.status)
        assertEquals(1, notification.uncommittedHistories.size)
        assertNull(notification.failureReason)

        val failed = notification("request-2")
        failed.markPublishFailed(publishedAt, "broker-down")
        failed.markPublishFailed(publishedAt.plusSeconds(1), "ignored")

        assertEquals(NotificationStatus.PUBLISH_FAILED, failed.status)
        assertEquals("broker-down", failed.failureReason)
        assertEquals(1, failed.uncommittedHistories.size)

        failed.markPublished(publishedAt.plusSeconds(2))
        assertEquals(NotificationStatus.PUBLISHED, failed.status)
        assertNull(failed.failureReason)
        assertEquals(2, failed.uncommittedHistories.size)
    }

    @Test
    @DisplayName("발송 성공은 PROCESSING에서 SENT로 전이하고 시도 횟수를 증가시킨다")
    fun sentTransition() {
        val notification = publishedNotification()
        val processedAt = now.plusSeconds(1)
        val sentAt = now.plusSeconds(2)

        notification.markProcessing(processedAt, "worker-1")
        assertEquals(processedAt, notification.claimedAt)
        assertEquals("worker-1", notification.claimedBy)

        notification.markSent(sentAt)

        assertEquals(NotificationStatus.SENT, notification.status)
        assertEquals(1, notification.dispatchAttempts)
        assertNull(notification.claimedAt)
        assertNull(notification.claimedBy)
        assertEquals(sentAt, notification.updatedAt)
        assertEquals(
            listOf(NotificationStatus.REQUESTED, NotificationStatus.PUBLISHED, NotificationStatus.PROCESSING),
            notification.uncommittedHistories.map { it.fromStatus },
        )
        assertEquals(
            listOf(NotificationStatus.PUBLISHED, NotificationStatus.PROCESSING, NotificationStatus.SENT),
            notification.uncommittedHistories.map { it.toStatus },
        )
    }

    @Test
    @DisplayName("재시도 가능한 실패는 FAILED를 거쳐 RETRY_WAIT로 전이한다")
    fun retryWaitTransition() {
        val notification = publishedNotification()

        notification.markProcessing(now.plusSeconds(1), "worker-1")
        notification.markFailed(now.plusSeconds(2), "rate-limited")

        assertEquals(NotificationStatus.FAILED, notification.status)
        assertEquals(1, notification.dispatchAttempts)
        assertEquals("rate-limited", notification.failureReason)
        assertNull(notification.claimedAt)
        assertNull(notification.claimedBy)
        assertTrue(notification.canRetry(maxAttempts = 2))

        notification.markRetryWait(now.plusSeconds(3), "rate-limited")

        assertEquals(NotificationStatus.RETRY_WAIT, notification.status)
        assertEquals(1, notification.dispatchAttempts)
        assertEquals("rate-limited", notification.failureReason)
    }

    @Test
    @DisplayName("재시도 한도에 도달한 실패는 DEAD로 전이하고 DEAD 멱등 호출은 무시한다")
    fun deadTransition() {
        val notification = publishedNotification()

        notification.markProcessing(now.plusSeconds(1), "worker-1")
        notification.markFailed(now.plusSeconds(2), "invalid-recipient")

        assertFalse(notification.canRetry(maxAttempts = 1))

        notification.markDead(now.plusSeconds(3), "invalid-recipient")
        notification.markDead(now.plusSeconds(4), "ignored")

        assertEquals(NotificationStatus.DEAD, notification.status)
        assertEquals("invalid-recipient", notification.failureReason)
        assertEquals(4, notification.uncommittedHistories.size)
    }

    @Test
    @DisplayName("운영자 복구는 DEAD를 REQUESTED로 되돌리고 실패 snapshot을 초기화한다")
    fun recoverDeadToRequested() {
        val notification = publishedNotification()
        notification.markProcessing(now.plusSeconds(2), "worker-1")
        notification.markFailed(now.plusSeconds(3), "invalid-recipient")
        notification.markDead(now.plusSeconds(4), "invalid-recipient")

        notification.recoverDeadToRequested(now.plusSeconds(5), "operator retry")

        assertEquals(NotificationStatus.REQUESTED, notification.status)
        assertNull(notification.failureReason)
        assertEquals(0, notification.dispatchAttempts)
        assertNull(notification.claimedAt)
        assertNull(notification.claimedBy)
        assertEquals(NotificationStatus.DEAD, notification.uncommittedHistories.last().fromStatus)
        assertEquals(NotificationStatus.REQUESTED, notification.uncommittedHistories.last().toStatus)
        assertEquals("operator retry", notification.uncommittedHistories.last().reason)
    }

    @Test
    @DisplayName("허용되지 않은 상태 전이는 예외로 차단한다")
    fun rejectInvalidTransitions() {
        val notification = notification()

        assertFailsWith<IllegalStateException> {
            notification.markProcessing(now.plusSeconds(1), "worker-1")
        }
        assertFailsWith<IllegalStateException> {
            notification.markSent(now.plusSeconds(1))
        }
        assertFailsWith<IllegalStateException> {
            notification.markFailed(now.plusSeconds(1), "failed")
        }
        assertFailsWith<IllegalArgumentException> {
            notification.canRetry(0)
        }
    }

    private fun publishedNotification(): Notification {
        return notification().also { it.markPublished(now.plusSeconds(1)) }
    }

    private fun notification(requestId: String = REQUEST_ID): Notification {
        return Notification.request(
            requestId = requestId,
            requester = REQUESTER,
            channel = NotificationChannel.SLACK,
            recipient = "user-1",
            message = MESSAGE,
            now = now,
        )
    }
}
