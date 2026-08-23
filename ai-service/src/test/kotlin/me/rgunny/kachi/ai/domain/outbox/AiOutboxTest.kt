package me.rgunny.kachi.ai.domain.outbox

import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("AiOutbox")
class AiOutboxTest {
    private val now = AiTestFixture.NOW
    private val later = now.plus(Duration.ofSeconds(30))
    // 전이 결과만 보는 테스트라 backoff를 결정적으로 두려고 jitter를 끈다. jitter 자체는 AiOutboxRetryPolicyTest가 본다.
    private val retryPolicy = AiOutboxRetryPolicy(
        maxAttempts = 3,
        baseDelay = Duration.ofSeconds(1),
        maxDelay = Duration.ofMinutes(1),
        multiplier = 2.0,
        jitterRatio = 0.0
    )

    @Nested
    @DisplayName("create()")
    inner class Create {

        @Test
        @DisplayName("발행 대기 상태로 생성한다")
        fun createAsPending() {
            val outbox = AiTestFixture.outbox()

            assertEquals(AiOutboxEventType.SUMMARY_CREATED, outbox.eventType)
            assertEquals(AiTestFixture.OUTBOX_EVENT_KEY, outbox.eventKey)
            assertEquals("NVIDIA", outbox.partitionKey)
            assertEquals(AiTestFixture.OUTBOX_PAYLOAD, outbox.payload)
            assertEquals(AiOutboxStatus.PENDING, outbox.status)
            assertEquals(0, outbox.retryCount)
            // 생성 즉시 발행 대상이라 첫 tick이 바로 집어갈 수 있어야 한다.
            assertEquals(now, outbox.nextRetryAt)
            assertNull(outbox.lastError)
            assertNull(outbox.publishedAt)
            assertNull(outbox.claim)
            assertEquals(now, outbox.createdAt)
            assertEquals(now, outbox.updatedAt)
        }

        @Test
        @DisplayName("키와 payload가 비어 있으면 생성할 수 없다")
        fun rejectBlankKeysAndPayload() {
            assertFailsWith<IllegalArgumentException> { AiTestFixture.outbox(eventKey = " ") }
            assertFailsWith<IllegalArgumentException> { AiTestFixture.outbox(partitionKey = " ") }
            assertFailsWith<IllegalArgumentException> { AiTestFixture.outbox(payload = " ") }
        }
    }

    @Nested
    @DisplayName("markPublishing()")
    inner class MarkPublishing {

        @Test
        @DisplayName("발행을 시작하며 소유권을 잡는다")
        fun claimOwnership() {
            val pending = AiTestFixture.outbox()

            val publishing = pending.markPublishing(now = later, claimedBy = "relay-1")

            assertEquals(AiOutboxStatus.PUBLISHING, publishing.status)
            assertEquals(AiOutboxClaim(claimedBy = "relay-1", claimedAt = later), publishing.claim)
            assertEquals(later, publishing.updatedAt)
            // 불변이므로 원본은 그대로다.
            assertEquals(AiOutboxStatus.PENDING, pending.status)
            assertNull(pending.claim)
        }

        @ParameterizedTest
        @EnumSource(value = AiOutboxStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["PENDING"])
        @DisplayName("PENDING이 아니면 발행을 시작할 수 없다")
        fun rejectNonPending(status: AiOutboxStatus) {
            assertFailsWith<IllegalStateException> {
                restored(status).markPublishing(now = later, claimedBy = "relay-1")
            }
        }

        @Test
        @DisplayName("소유자가 비어 있으면 발행을 시작할 수 없다")
        fun rejectBlankClaimedBy() {
            assertFailsWith<IllegalArgumentException> {
                AiTestFixture.outbox().markPublishing(now = later, claimedBy = " ")
            }
        }
    }

    @Nested
    @DisplayName("markPublished()")
    inner class MarkPublished {

        @Test
        @DisplayName("발행에 성공하면 소유권을 놓고 종료 상태가 된다")
        fun publish() {
            val publishing = AiTestFixture.outbox().markPublishing(now = now, claimedBy = "relay-1")

            val published = publishing.markPublished(later)

            assertEquals(AiOutboxStatus.PUBLISHED, published.status)
            assertEquals(later, published.publishedAt)
            assertNull(published.lastError)
            assertNull(published.claim)
            assertEquals(later, published.updatedAt)
        }

        @ParameterizedTest
        @EnumSource(value = AiOutboxStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["PUBLISHING"])
        @DisplayName("PUBLISHING이 아니면 발행 완료할 수 없다")
        fun rejectNonPublishing(status: AiOutboxStatus) {
            assertFailsWith<IllegalStateException> {
                restored(status).markPublished(later)
            }
        }
    }

    @Nested
    @DisplayName("recordFailure()")
    inner class RecordFailure {

        @Test
        @DisplayName("한도 미만이면 backoff 뒤 다시 발행 대상이 된다")
        fun retryWithBackoff() {
            val publishing = AiTestFixture.outbox().markPublishing(now = now, claimedBy = "relay-1")

            val failed = publishing.recordFailure("broker timeout", retryPolicy, later)

            assertEquals(AiOutboxStatus.PENDING, failed.status)
            assertEquals(1, failed.retryCount)
            assertEquals(later.plus(retryPolicy.backoff(1)), failed.nextRetryAt)
            assertEquals("broker timeout", failed.lastError)
            assertNull(failed.claim)
            assertEquals(later, failed.updatedAt)
        }

        @Test
        @DisplayName("한도에 도달하면 DEAD로 보낸다")
        fun deadWhenRetryExhausted() {
            val publishing = restored(AiOutboxStatus.PUBLISHING, retryCount = retryPolicy.maxAttempts - 1)

            val failed = publishing.recordFailure("broker timeout", retryPolicy, later)

            assertEquals(AiOutboxStatus.DEAD, failed.status)
            assertEquals(retryPolicy.maxAttempts, failed.retryCount)
            assertEquals("broker timeout", failed.lastError)
            assertNull(failed.claim)
            // 더 볼 일이 없으므로 다음 시도 시각은 건드리지 않는다.
            assertEquals(publishing.nextRetryAt, failed.nextRetryAt)
        }

        @ParameterizedTest
        @EnumSource(value = AiOutboxStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["PUBLISHING"])
        @DisplayName("PUBLISHING이 아니면 실패를 기록할 수 없다")
        fun rejectNonPublishing(status: AiOutboxStatus) {
            assertFailsWith<IllegalStateException> {
                restored(status).recordFailure("broker timeout", retryPolicy, later)
            }
        }

        @Test
        @DisplayName("사유가 비어 있으면 실패를 기록할 수 없다")
        fun rejectBlankReason() {
            assertFailsWith<IllegalArgumentException> {
                restored(AiOutboxStatus.PUBLISHING).recordFailure(" ", retryPolicy, later)
            }
        }
    }

    @Nested
    @DisplayName("markDead()")
    inner class MarkDead {

        @Test
        @DisplayName("재시도해도 소용없는 실패는 한도와 무관하게 즉시 DEAD로 보낸다")
        fun deadImmediately() {
            val publishing = restored(AiOutboxStatus.PUBLISHING, retryCount = 1)

            val dead = publishing.markDead("payload too large", later)

            assertEquals(AiOutboxStatus.DEAD, dead.status)
            assertEquals(1, dead.retryCount)
            assertEquals("payload too large", dead.lastError)
            assertNull(dead.claim)
            assertEquals(later, dead.updatedAt)
        }

        @ParameterizedTest
        @EnumSource(value = AiOutboxStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["PUBLISHING"])
        @DisplayName("PUBLISHING이 아니면 DEAD로 보낼 수 없다")
        fun rejectNonPublishing(status: AiOutboxStatus) {
            assertFailsWith<IllegalStateException> {
                restored(status).markDead("payload too large", later)
            }
        }

        @Test
        @DisplayName("사유가 비어 있으면 DEAD로 보낼 수 없다")
        fun rejectBlankReason() {
            assertFailsWith<IllegalArgumentException> {
                restored(AiOutboxStatus.PUBLISHING).markDead(" ", later)
            }
        }
    }

    @Nested
    @DisplayName("recoverToPending()")
    inner class RecoverToPending {

        @Test
        @DisplayName("운영자 복구는 재시도 누적까지 초기화한다")
        fun recover() {
            val dead = restored(AiOutboxStatus.DEAD, retryCount = 3, lastError = "broker timeout")

            val recovered = dead.recoverToPending(later)

            assertEquals(AiOutboxStatus.PENDING, recovered.status)
            assertEquals(0, recovered.retryCount)
            assertEquals(later, recovered.nextRetryAt)
            assertNull(recovered.lastError)
            assertNull(recovered.publishedAt)
            assertNull(recovered.claim)
        }

        @ParameterizedTest
        @EnumSource(value = AiOutboxStatus::class, mode = EnumSource.Mode.EXCLUDE, names = ["DEAD"])
        @DisplayName("DEAD가 아니면 복구할 수 없다")
        fun rejectNonDead(status: AiOutboxStatus) {
            assertFailsWith<IllegalStateException> {
                restored(status).recoverToPending(later)
            }
        }
    }

    @Nested
    @DisplayName("restore()")
    inner class Restore {

        @Test
        @DisplayName("claim은 PUBLISHING 상태와 함께만 존재한다")
        fun rejectClaimStatusMismatch() {
            assertFailsWith<IllegalArgumentException> {
                restore(status = AiOutboxStatus.PUBLISHING, claim = null)
            }
            assertFailsWith<IllegalArgumentException> {
                restore(status = AiOutboxStatus.PENDING, claim = AiOutboxClaim("relay-1", now))
            }
        }

        @Test
        @DisplayName("PUBLISHED는 발행 시각을 가져야 한다")
        fun rejectPublishedWithoutPublishedAt() {
            assertFailsWith<IllegalArgumentException> {
                restore(status = AiOutboxStatus.PUBLISHED, publishedAt = null)
            }
        }

        @Test
        @DisplayName("필드 값이 유효하지 않으면 복원할 수 없다")
        fun rejectInvalidFields() {
            assertFailsWith<IllegalArgumentException> { restore(retryCount = -1) }
            assertFailsWith<IllegalArgumentException> { restore(lastError = " ") }
            assertFailsWith<IllegalArgumentException> { restore(eventKey = " ") }
            assertFailsWith<IllegalArgumentException> { restore(partitionKey = " ") }
            assertFailsWith<IllegalArgumentException> { restore(payload = " ") }
        }

        @Test
        @DisplayName("복원한 outbox도 상태 전이를 이어갈 수 있다")
        fun transitionAfterRestore() {
            val publishing = restored(AiOutboxStatus.PUBLISHING)

            val published = publishing.markPublished(later)

            assertEquals(AiOutboxStatus.PUBLISHED, published.status)
            assertNotNull(published.publishedAt)
        }
    }

    private fun restored(
        status: AiOutboxStatus,
        retryCount: Int = 0,
        lastError: String? = null
    ): AiOutbox {
        return restore(
            status = status,
            retryCount = retryCount,
            lastError = lastError,
            publishedAt = if (status == AiOutboxStatus.PUBLISHED) now else null,
            claim = if (status == AiOutboxStatus.PUBLISHING) AiOutboxClaim("relay-1", now) else null
        )
    }

    private fun restore(
        eventKey: String = AiTestFixture.OUTBOX_EVENT_KEY,
        partitionKey: String = "NVIDIA",
        payload: String = AiTestFixture.OUTBOX_PAYLOAD,
        status: AiOutboxStatus = AiOutboxStatus.PENDING,
        retryCount: Int = 0,
        nextRetryAt: Instant = now,
        lastError: String? = null,
        publishedAt: Instant? = null,
        claim: AiOutboxClaim? = null
    ): AiOutbox {
        return AiOutbox.restore(
            id = AiOutboxId.newId(),
            eventType = AiOutboxEventType.SUMMARY_CREATED,
            eventKey = eventKey,
            partitionKey = partitionKey,
            payload = payload,
            status = status,
            retryCount = retryCount,
            nextRetryAt = nextRetryAt,
            lastError = lastError,
            publishedAt = publishedAt,
            claim = claim,
            createdAt = now,
            updatedAt = now
        )
    }
}
