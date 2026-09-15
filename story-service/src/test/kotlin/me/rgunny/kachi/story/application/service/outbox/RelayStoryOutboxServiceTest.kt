package me.rgunny.kachi.story.application.service.outbox

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.exception.StoryOutboxErrorCode
import me.rgunny.kachi.story.application.exception.StoryOutboxPublishException
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxClaim
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus
import me.rgunny.kachi.story.fake.FakeStoryOutboxPersistencePort
import me.rgunny.kachi.story.fake.FakeStoryOutboxPublisherPort
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RelayStoryOutboxService")
class RelayStoryOutboxServiceTest {
    private val persistencePort = FakeStoryOutboxPersistencePort()
    private val publisherPort = FakeStoryOutboxPublisherPort()

    @Test
    @DisplayName("발행할 차례가 된 행을 소유권을 잡아 발행하고 발행 완료로 확정한다")
    fun publishPendingOutbox() = runBlocking {
        persistencePort.store(StoryTestFixture.restoredOutbox())

        val result = relay()

        assertEquals(1, publisherPort.published.size)
        assertEquals(1, result.processed)
        assertEquals(1, result.published)

        val (finalized, expectedClaim) = persistencePort.finalizeCalls.single()
        assertEquals(StoryOutboxStatus.PUBLISHED, finalized.status)
        assertEquals(StoryTestFixture.NOW, finalized.publishedAt)
        assertEquals(claimOf(publisherPort.published.single()), expectedClaim)
    }

    @Test
    @DisplayName("소유권 확정에는 저장소가 돌려준 점유 시각을 그대로 쓴다")
    fun finalizeWithStoredClaim() = runBlocking {
        // 저장소는 점유 시각을 밀리초로 자른다. relay가 자기 시계로 소유권을 다시 만들면 확정 조건이 영원히 어긋난다.
        val now = StoryTestFixture.NOW.plusNanos(123_456)
        persistencePort.store(StoryTestFixture.restoredOutbox())

        relay(clock = Clock.fixed(now, ZoneOffset.UTC))

        assertEquals(StoryTestFixture.NOW, persistencePort.finalizeCalls.single().second.claimedAt)
    }

    @Test
    @DisplayName("소유권을 잡을 때 이 인스턴스의 발행자 이름을 남긴다")
    fun claimWithPublisherId() = runBlocking {
        persistencePort.store(StoryTestFixture.restoredOutbox())

        relay()

        assertEquals(StoryTestFixture.RELAY_PUBLISHER_ID, persistencePort.claimCalls.single().second)
    }

    @Test
    @DisplayName("재시도할 수 있는 발행 실패는 다음 차례를 예약한다")
    fun scheduleRetryOnRetryableFailure() = runBlocking {
        persistencePort.store(StoryTestFixture.restoredOutbox())
        publisherPort.failure = publishException(retryable = true)

        val result = relay()

        assertEquals(1, result.retried)
        assertEquals(0, result.published)

        val finalized = persistencePort.finalizeCalls.single().first
        assertEquals(StoryOutboxStatus.PENDING, finalized.status)
        assertEquals(1, finalized.retryCount)
        assertEquals(StoryTestFixture.NOW.plusSeconds(1), finalized.nextRetryAt)
    }

    @Test
    @DisplayName("재시도 한도에 도달한 발행 실패는 더 보내지 않고 남긴다")
    fun markDeadWhenRetriesAreExhausted() = runBlocking {
        persistencePort.store(StoryTestFixture.restoredOutbox(retryCount = 4))
        publisherPort.failure = publishException(retryable = true)

        val result = relay()

        assertEquals(1, result.dead)
        assertEquals(0, result.retried)

        val finalized = persistencePort.finalizeCalls.single().first
        assertEquals(StoryOutboxStatus.DEAD, finalized.status)
        assertEquals(5, finalized.retryCount)
    }

    @Test
    @DisplayName("재시도해도 결과가 같은 발행 실패는 한도와 무관하게 즉시 남긴다")
    fun markDeadImmediatelyOnNonRetryableFailure() = runBlocking {
        persistencePort.store(StoryTestFixture.restoredOutbox(retryCount = 2))
        publisherPort.failure = publishException(retryable = false)

        val result = relay()

        assertEquals(1, result.dead)

        val finalized = persistencePort.finalizeCalls.single().first
        assertEquals(StoryOutboxStatus.DEAD, finalized.status)
        assertEquals(2, finalized.retryCount)
    }

    @Test
    @DisplayName("발행 예외가 아닌 실패는 원인을 알 수 없으므로 재시도 대상으로 본다")
    fun treatUnknownFailureAsRetryable() = runBlocking {
        persistencePort.store(StoryTestFixture.restoredOutbox())
        publisherPort.failure = IllegalStateException("broker connection reset")

        val result = relay()

        assertEquals(1, result.retried)
        assertEquals(StoryOutboxStatus.PENDING, persistencePort.finalizeCalls.single().first.status)
    }

    @Test
    @DisplayName("취소는 발행 실패가 아니므로 확정하지 않고 그대로 전파한다")
    fun propagateCancellation() {
        persistencePort.store(StoryTestFixture.restoredOutbox())
        publisherPort.failure = CancellationException("relay cancelled")

        assertFailsWith<CancellationException> {
            runBlocking { relay() }
        }

        assertTrue(persistencePort.finalizeCalls.isEmpty())
    }

    @Test
    @DisplayName("다른 인스턴스가 먼저 가져간 행은 발행하지 않고 집계에도 넣지 않는다")
    fun skipOutboxClaimedByAnotherRelay() = runBlocking {
        persistencePort.store(StoryTestFixture.restoredOutbox())
        persistencePort.claimResult = { null }

        val result = relay()

        assertTrue(publisherPort.published.isEmpty())
        assertEquals(0, result.processed)
    }

    @Test
    @DisplayName("발행 중에 멈춘 행은 원 소유자의 소유권으로 회수한다")
    fun recoverStalePublishingOutbox() = runBlocking {
        val stale = stalePublishing()
        persistencePort.store(stale)

        val result = relay()

        assertEquals(1, result.staleRecovered)
        assertEquals(1, result.processed)
        assertTrue(publisherPort.published.isEmpty())

        val (finalized, expectedClaim) = persistencePort.finalizeCalls.single()
        assertEquals(StoryOutboxStatus.PENDING, finalized.status)
        assertEquals("publishing-timeout", finalized.lastError)
        assertEquals(claimOf(stale), expectedClaim)
    }

    @Test
    @DisplayName("회수한 행이 재시도 한도에 도달했으면 더 보내지 않고 남긴다")
    fun markRecoveredOutboxDeadWhenRetriesAreExhausted() = runBlocking {
        persistencePort.store(stalePublishing(retryCount = 4))

        val result = relay()

        assertEquals(1, result.staleRecovered)
        assertEquals(1, result.dead)
        assertEquals(StoryOutboxStatus.DEAD, persistencePort.finalizeCalls.single().first.status)
    }

    @Test
    @DisplayName("소유권을 잃은 행의 결과는 저장되지 않고 집계에서 빠진다")
    fun discardResultWhenClaimIsLost() = runBlocking {
        persistencePort.store(StoryTestFixture.restoredOutbox())
        persistencePort.finalizeResult = false

        val result = relay()

        assertEquals(1, result.processed)
        assertEquals(0, result.published)
        assertEquals(0, result.retried)
        assertEquals(0, result.dead)
    }

    @Test
    @DisplayName("한 행의 저장소 실패는 그 행만 건너뛰고 나머지 행을 계속 처리한다")
    fun continueAfterRowFailure() = runBlocking {
        persistencePort.store(
            StoryTestFixture.restoredOutbox(eventKey = "story-1:news-1", createdAt = StoryTestFixture.NOW.minusSeconds(2)),
            StoryTestFixture.restoredOutbox(eventKey = "story-1:news-2", createdAt = StoryTestFixture.NOW.minusSeconds(1))
        )
        persistencePort.finalizeFailures.addAll(listOf(IllegalStateException("finalize failed"), null))

        val result = relay()

        assertEquals(2, result.processed)
        assertEquals(1, result.published)
        assertEquals(2, publisherPort.published.size)
    }

    @Test
    @DisplayName("조회에는 이번 tick의 시각과 batch 크기, 점유 만료 기준을 넘긴다")
    fun passQueryArguments() = runBlocking {
        val policy = StoryTestFixture.relayPolicy(batchSize = 7, publishingVisibilityTimeout = Duration.ofSeconds(30))

        relay(policy = policy)

        assertEquals(listOf(StoryTestFixture.NOW to 7), persistencePort.publishableCalls)
        assertEquals(listOf(StoryTestFixture.NOW.minusSeconds(30) to 7), persistencePort.staleCalls)
    }

    @Test
    @DisplayName("회수와 발행이 함께 있으면 회수를 먼저 처리한다")
    fun recoverStaleBeforePublishing() = runBlocking {
        persistencePort.store(stalePublishing(), StoryTestFixture.restoredOutbox(eventKey = "story-1:news-2"))

        relay()

        assertEquals(
            listOf(
                FakeStoryOutboxPersistencePort.CALL_STALE,
                FakeStoryOutboxPersistencePort.CALL_FINALIZE,
                FakeStoryOutboxPersistencePort.CALL_PUBLISHABLE,
                FakeStoryOutboxPersistencePort.CALL_CLAIM,
                FakeStoryOutboxPersistencePort.CALL_FINALIZE
            ),
            persistencePort.callOrder
        )
    }

    @Test
    @DisplayName("다룰 행이 없으면 집계는 전부 0이다")
    fun returnEmptyResultWhenNothingToRelay() = runBlocking {
        val result = relay()

        assertEquals(0, result.processed)
        assertEquals(0, result.published)
        assertEquals(0, result.retried)
        assertEquals(0, result.dead)
        assertEquals(0, result.staleRecovered)
        assertEquals(StoryTestFixture.NOW, result.completedAt)
    }

    @Test
    @DisplayName("실패 사유는 메시지를, 메시지가 없으면 예외 이름을, 이름도 없으면 고정값을 남긴다")
    fun recordFailureReason() = runBlocking {
        assertEquals("broker connection reset", reasonOf(IllegalStateException("broker connection reset")))
        assertEquals("IllegalStateException", reasonOf(IllegalStateException("   ")))
        assertEquals("publish-failed", reasonOf(object : RuntimeException() {}))
    }

    /**
     * 발행 실패 하나가 어떤 사유로 기록되는지만 본다. fake는 호출을 누적하므로 호출마다 새로 만든다.
     */
    private suspend fun reasonOf(error: Throwable): String {
        val persistencePort = FakeStoryOutboxPersistencePort()
        val publisherPort = FakeStoryOutboxPublisherPort()
        persistencePort.store(StoryTestFixture.restoredOutbox())
        publisherPort.failure = error

        serviceOf(persistencePort, publisherPort).relay()

        return persistencePort.finalizeCalls.single().first.lastError.orEmpty()
    }

    private suspend fun relay(
        clock: Clock = StoryTestFixture.CLOCK,
        policy: StoryOutboxRelayPolicy = StoryTestFixture.relayPolicy()
    ) = serviceOf(persistencePort, publisherPort, clock, policy).relay()

    private fun serviceOf(
        persistencePort: FakeStoryOutboxPersistencePort,
        publisherPort: FakeStoryOutboxPublisherPort,
        clock: Clock = StoryTestFixture.CLOCK,
        policy: StoryOutboxRelayPolicy = StoryTestFixture.relayPolicy()
    ): RelayStoryOutboxService {
        return RelayStoryOutboxService(
            outboxPersistencePort = persistencePort,
            publisherPort = publisherPort,
            policy = policy,
            clock = clock
        )
    }

    /**
     * 점유 만료 기준(60초)보다 오래 발행 중으로 남은 행.
     */
    private fun stalePublishing(retryCount: Int = 0, claimedAt: Instant = StoryTestFixture.NOW.minusSeconds(120)): StoryOutbox {
        return StoryTestFixture.restoredOutbox(
            status = StoryOutboxStatus.PUBLISHING,
            retryCount = retryCount,
            claim = StoryOutboxClaim(claimedBy = "story-service-1", claimedAt = claimedAt)
        )
    }

    private fun claimOf(outbox: StoryOutbox): StoryOutboxClaim {
        return requireNotNull(outbox.claim)
    }

    private fun publishException(retryable: Boolean): StoryOutboxPublishException {
        return StoryOutboxPublishException(
            errorCode = StoryOutboxErrorCode.OUTBOX_PUBLISH_FAILED,
            retryable = retryable,
            detail = "broker unavailable"
        )
    }
}
