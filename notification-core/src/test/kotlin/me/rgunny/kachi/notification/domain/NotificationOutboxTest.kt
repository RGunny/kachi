package me.rgunny.kachi.notification.domain

import me.rgunny.kachi.notification.retry.RetryPolicy
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.DISPATCH_TOPIC
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.NOW
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.RECIPIENT
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("NotificationOutbox")
class NotificationOutboxTest {
    private val now = NOW
    private val retryPolicy = RetryPolicy(
        maxAttempts = 3,
        baseDelay = Duration.ofSeconds(10),
        maxDelay = Duration.ofMinutes(1),
    )

    @Test
    @DisplayName("필수값을 검증하고 PENDING 상태로 생성한다")
    fun create() {
        val outbox = outbox()

        assertEquals(NotificationOutboxStatus.PENDING, outbox.outboxStatus)
        assertEquals(0, outbox.retryCount)
        assertEquals(now, outbox.nextRetryAt)
        assertNull(outbox.lastError)

        assertFailsWith<IllegalArgumentException> {
            NotificationOutbox.create(NotificationId.newId(), "", "recipient", "{}", now)
        }
        assertFailsWith<IllegalArgumentException> {
            NotificationOutbox.create(NotificationId.newId(), "topic", " ", "{}", now)
        }
        assertFailsWith<IllegalArgumentException> {
            NotificationOutbox.create(NotificationId.newId(), "topic", "recipient", "", now)
        }
    }

    @Test
    @DisplayName("발행 claim 후 성공 처리하면 PUBLISHED로 전이하고 claim 정보를 지운다")
    fun publish() {
        val outbox = outbox()
        val claimedAt = now.plusSeconds(1)
        val publishedAt = now.plusSeconds(2)

        outbox.markPublishing(claimedAt, "publisher-1")
        outbox.markPublished(publishedAt)
        outbox.markPublished(publishedAt.plusSeconds(1))

        assertEquals(NotificationOutboxStatus.PUBLISHED, outbox.outboxStatus)
        assertEquals(publishedAt, outbox.publishedAt)
        assertNull(outbox.claimedAt)
        assertNull(outbox.claimedBy)
        assertNull(outbox.lastError)
    }

    @Test
    @DisplayName("발행 실패가 재시도 가능하면 PENDING으로 돌리고 다음 재시도 시각을 계산한다")
    fun retryableFailure() {
        val outbox = outbox()
        val failureAt = now.plusSeconds(3)

        outbox.markPublishing(now.plusSeconds(1), "publisher-1")
        outbox.recordFailure("broker-timeout", retryPolicy, failureAt)

        assertEquals(NotificationOutboxStatus.PENDING, outbox.outboxStatus)
        assertEquals(1, outbox.retryCount)
        assertEquals("broker-timeout", outbox.lastError)
        assertEquals(failureAt.plusSeconds(10), outbox.nextRetryAt)
        assertNull(outbox.claimedAt)
        assertNull(outbox.claimedBy)
    }

    @Test
    @DisplayName("발행 실패가 한도에 도달하면 DEAD로 전이한다")
    fun exhaustedFailure() {
        val outbox = outbox()
        val oneAttemptPolicy = RetryPolicy(
            maxAttempts = 1,
            baseDelay = Duration.ofSeconds(10),
            maxDelay = Duration.ofMinutes(1),
        )

        outbox.markPublishing(now.plusSeconds(1), "publisher-1")
        outbox.recordFailure("broker-down", oneAttemptPolicy, now.plusSeconds(2))

        assertEquals(NotificationOutboxStatus.DEAD, outbox.outboxStatus)
        assertEquals(1, outbox.retryCount)
        assertEquals("broker-down", outbox.lastError)
        assertNull(outbox.claimedAt)
        assertNull(outbox.claimedBy)
    }

    @Test
    @DisplayName("즉시 DEAD 처리와 운영자 복구를 지원한다")
    fun markDeadAndRecover() {
        val outbox = outbox()
        val recoveredAt = now.plusSeconds(10)

        outbox.markPublishing(now.plusSeconds(1), "publisher-1")
        outbox.markDead("non-retryable")

        assertEquals(NotificationOutboxStatus.DEAD, outbox.outboxStatus)
        assertEquals("non-retryable", outbox.lastError)

        outbox.recoverToPending(recoveredAt)

        assertEquals(NotificationOutboxStatus.PENDING, outbox.outboxStatus)
        assertEquals(0, outbox.retryCount)
        assertEquals(recoveredAt, outbox.nextRetryAt)
        assertNull(outbox.lastError)
        assertNull(outbox.publishedAt)
    }

    @Test
    @DisplayName("허용되지 않은 상태 전이는 예외로 차단한다")
    fun rejectInvalidTransitions() {
        val outbox = outbox()

        assertFailsWith<IllegalStateException> {
            outbox.markPublished(now.plusSeconds(1))
        }
        assertFailsWith<IllegalStateException> {
            outbox.recordFailure("failed", retryPolicy, now.plusSeconds(1))
        }
        assertFailsWith<IllegalStateException> {
            outbox.markDead("failed")
        }
        assertFailsWith<IllegalStateException> {
            outbox.recoverToPending(now.plusSeconds(1))
        }
        assertFailsWith<IllegalArgumentException> {
            outbox.markPublishing(now.plusSeconds(1), "")
        }
    }

    private fun outbox(): NotificationOutbox {
        return NotificationOutbox.create(
            notificationId = NotificationId.newId(),
            topic = DISPATCH_TOPIC,
            partitionKey = RECIPIENT,
            eventPayload = "{}",
            now = now,
        )
    }
}
