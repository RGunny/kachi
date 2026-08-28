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
import me.rgunny.kachi.notification.domain.NotificationOrigin
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.ORIGIN
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

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
            Notification.request("", "api", NotificationChannel.SLACK, "user", "message", NotificationOrigin.NONE, now)
        }
        assertFailsWith<IllegalArgumentException> {
            Notification.request(REQUEST_ID, " ", NotificationChannel.SLACK, "user", "message", NotificationOrigin.NONE, now)
        }
        assertFailsWith<IllegalArgumentException> {
            Notification.request(REQUEST_ID, REQUESTER, NotificationChannel.SLACK, "", "message", NotificationOrigin.NONE, now)
        }
        assertFailsWith<IllegalArgumentException> {
            Notification.request(REQUEST_ID, REQUESTER, NotificationChannel.SLACK, "user", " ", NotificationOrigin.NONE, now)
        }
    }

    @Test
    @DisplayName("발행 성공과 실패 상태를 기록하고 허용된 멱등 호출은 무시한다")
    fun publishTransitions() {
        val publishedAt = now.plusSeconds(1)
        val notification = notification()

        val published = notification
            .markPublished(publishedAt)
            .markPublished(publishedAt.plusSeconds(1))

        assertEquals(NotificationStatus.PUBLISHED, published.status)
        assertEquals(1, published.uncommittedHistories.size)
        assertNull(published.failureReason)
        assertEquals(NotificationStatus.REQUESTED, notification.status)
        assertTrue(notification.uncommittedHistories.isEmpty())

        val failed = notification("request-2")
            .markPublishFailed(publishedAt, "broker-down")
            .markPublishFailed(publishedAt.plusSeconds(1), "ignored")

        assertEquals(NotificationStatus.PUBLISH_FAILED, failed.status)
        assertEquals("broker-down", failed.failureReason)
        assertEquals(1, failed.uncommittedHistories.size)

        val republished = failed.markPublished(publishedAt.plusSeconds(2))
        assertEquals(NotificationStatus.PUBLISHED, republished.status)
        assertNull(republished.failureReason)
        assertEquals(2, republished.uncommittedHistories.size)
        assertEquals(NotificationStatus.PUBLISH_FAILED, failed.status)
        assertEquals("broker-down", failed.failureReason)
    }

    @Test
    @DisplayName("발송 성공은 PROCESSING에서 SENT로 전이하고 시도 횟수를 증가시킨다")
    fun sentTransition() {
        val notification = publishedNotification()
        val processedAt = now.plusSeconds(1)
        val sentAt = now.plusSeconds(2)

        val processing = notification.markProcessing(processedAt, "worker-1")
        assertEquals(processedAt, processing.claimedAt)
        assertEquals("worker-1", processing.claimedBy)
        assertNull(notification.claimedAt)
        assertNull(notification.claimedBy)

        val sent = processing.markSent(sentAt)

        assertEquals(NotificationStatus.SENT, sent.status)
        assertEquals(1, sent.dispatchAttempts)
        assertNull(sent.claimedAt)
        assertNull(sent.claimedBy)
        assertEquals(sentAt, sent.updatedAt)
        assertEquals(
            listOf(NotificationStatus.REQUESTED, NotificationStatus.PUBLISHED, NotificationStatus.PROCESSING),
            sent.uncommittedHistories.map { it.fromStatus },
        )
        assertEquals(
            listOf(NotificationStatus.PUBLISHED, NotificationStatus.PROCESSING, NotificationStatus.SENT),
            sent.uncommittedHistories.map { it.toStatus },
        )
        assertEquals(NotificationStatus.PROCESSING, processing.status)
        assertEquals(0, processing.dispatchAttempts)
        assertEquals(processedAt, processing.claimedAt)
        assertEquals(2, processing.uncommittedHistories.size)
    }

    @Test
    @DisplayName("재시도 가능한 실패는 FAILED를 거쳐 RETRY_WAIT로 전이한다")
    fun retryWaitTransition() {
        val processing = publishedNotification().markProcessing(now.plusSeconds(1), "worker-1")

        val failed = processing.markFailed(now.plusSeconds(2), "rate-limited")

        assertEquals(NotificationStatus.FAILED, failed.status)
        assertEquals(1, failed.dispatchAttempts)
        assertEquals("rate-limited", failed.failureReason)
        assertNull(failed.claimedAt)
        assertNull(failed.claimedBy)
        assertTrue(failed.canRetry(maxAttempts = 2))
        assertEquals(NotificationStatus.PROCESSING, processing.status)
        assertNull(processing.failureReason)

        val retryWait = failed.markRetryWait(now.plusSeconds(3), "rate-limited")

        assertEquals(NotificationStatus.RETRY_WAIT, retryWait.status)
        assertEquals(1, retryWait.dispatchAttempts)
        assertEquals("rate-limited", retryWait.failureReason)
        assertEquals(NotificationStatus.FAILED, failed.status)
    }

    @Test
    @DisplayName("재시도 한도에 도달한 실패는 DEAD로 전이하고 DEAD 멱등 호출은 무시한다")
    fun deadTransition() {
        val failed = publishedNotification()
            .markProcessing(now.plusSeconds(1), "worker-1")
            .markFailed(now.plusSeconds(2), "invalid-recipient")

        assertFalse(failed.canRetry(maxAttempts = 1))

        val dead = failed
            .markDead(now.plusSeconds(3), "invalid-recipient")
            .markDead(now.plusSeconds(4), "ignored")

        assertEquals(NotificationStatus.DEAD, dead.status)
        assertEquals("invalid-recipient", dead.failureReason)
        assertEquals(4, dead.uncommittedHistories.size)
        assertEquals(NotificationStatus.FAILED, failed.status)
        assertEquals(3, failed.uncommittedHistories.size)
    }

    @Test
    @DisplayName("운영자 복구는 DEAD를 REQUESTED로 되돌리고 실패 snapshot을 초기화한다")
    fun recoverDeadToRequested() {
        val dead = publishedNotification()
            .markProcessing(now.plusSeconds(2), "worker-1")
            .markFailed(now.plusSeconds(3), "invalid-recipient")
            .markDead(now.plusSeconds(4), "invalid-recipient")

        val recovered = dead.recoverDeadToRequested(now.plusSeconds(5), "operator retry")

        assertEquals(NotificationStatus.REQUESTED, recovered.status)
        assertNull(recovered.failureReason)
        assertEquals(0, recovered.dispatchAttempts)
        assertNull(recovered.claimedAt)
        assertNull(recovered.claimedBy)
        assertEquals(NotificationStatus.DEAD, recovered.uncommittedHistories.last().fromStatus)
        assertEquals(NotificationStatus.REQUESTED, recovered.uncommittedHistories.last().toStatus)
        assertEquals("operator retry", recovered.uncommittedHistories.last().reason)
        assertEquals(NotificationStatus.DEAD, dead.status)
        assertEquals("invalid-recipient", dead.failureReason)
        assertEquals(1, dead.dispatchAttempts)
        assertEquals(4, dead.uncommittedHistories.size)
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
        assertFailsWith<IllegalStateException> {
            notification.recoverDeadToRequested(now.plusSeconds(1), "operator retry")
        }
        assertFailsWith<IllegalArgumentException> {
            notification.canRetry(0)
        }
    }

    @Test
    @DisplayName("요청 출처를 보존한다")
    fun keepsOrigin() {
        val notification = Notification.request(
            requestId = REQUEST_ID,
            requester = REQUESTER,
            channel = NotificationChannel.SLACK,
            recipientId = "user-1",
            message = MESSAGE,
            origin = ORIGIN,
            now = now,
        )

        assertEquals(ORIGIN, notification.origin)
        assertEquals(ORIGIN, notification.markPublished(now.plusSeconds(1)).origin)
    }

    @Test
    @DisplayName("수신처가 유효하지 않으면 PROCESSING에서 SUPPRESSED로 전이하고 이력을 남긴다")
    fun suppressedTransition() {
        val suppressedAt = now.plusSeconds(3)
        val processing = publishedNotification().markProcessing(now.plusSeconds(2), "worker-1")

        val suppressed = processing.markSuppressed(suppressedAt, "binding revoked")

        assertEquals(NotificationStatus.SUPPRESSED, suppressed.status)
        assertEquals("binding revoked", suppressed.failureReason)
        assertEquals(suppressedAt, suppressed.lastTransitionAt)
        assertNull(suppressed.claimedAt)
        assertNull(suppressed.claimedBy)
        assertEquals(0, suppressed.dispatchAttempts)
        val history = suppressed.uncommittedHistories.last()
        assertEquals(NotificationStatus.PROCESSING, history.fromStatus)
        assertEquals(NotificationStatus.SUPPRESSED, history.toStatus)
        assertFailsWith<IllegalArgumentException> { processing.markSuppressed(suppressedAt, " ") }
    }

    @ParameterizedTest
    @EnumSource(value = NotificationStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["PROCESSING"])
    @DisplayName("PROCESSING이 아니면 SUPPRESSED로 전이할 수 없다")
    fun rejectSuppressedFromOtherStatus(status: NotificationStatus) {
        val notification = Notification.restore(
            id = NotificationId.newId(),
            requestId = REQUEST_ID,
            requester = REQUESTER,
            channel = NotificationChannel.SLACK,
            recipientId = "user-1",
            message = MESSAGE,
            origin = NotificationOrigin.NONE,
            requestedAt = now,
            status = status,
            failureReason = null,
            updatedAt = now,
            lastTransitionAt = now,
            dispatchAttempts = 0,
            claimedAt = null,
            claimedBy = null,
        )

        assertFailsWith<IllegalStateException> { notification.markSuppressed(now.plusSeconds(1), "binding revoked") }
    }

    private fun publishedNotification(): Notification {
        return notification().markPublished(now.plusSeconds(1))
    }

    private fun notification(requestId: String = REQUEST_ID): Notification {
        return Notification.request(
            requestId = requestId,
            requester = REQUESTER,
            channel = NotificationChannel.SLACK,
            recipientId = "user-1",
            message = MESSAGE,
            origin = NotificationOrigin.NONE,
            now = now,
        )
    }
}
