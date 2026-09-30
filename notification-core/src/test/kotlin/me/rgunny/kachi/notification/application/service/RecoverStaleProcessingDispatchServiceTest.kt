package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.fake.FakeDeduplicationPort
import me.rgunny.kachi.notification.fake.FakeNotificationDispatchPersistencePort
import me.rgunny.kachi.notification.fake.FakeNotificationPersistencePort
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.CLOCK
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.DEDUPE_TTL
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.IDEMPOTENCY_KEY_TTL
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.MESSAGE
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.NOW
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.RECIPIENT_ID
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.REQUESTER
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.REQUEST_ID
import me.rgunny.kachi.notification.domain.retry.RetryFailureCode
import me.rgunny.kachi.notification.domain.retry.RetryPolicy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import me.rgunny.kachi.notification.domain.NotificationOrigin

@DisplayName("RecoverStaleProcessingDispatchService")
class RecoverStaleProcessingDispatchServiceTest {

    @Test
    @DisplayName("visibility timeout을 넘긴 PROCESSING 알림을 RETRY_WAIT로 회수하고 dedupe를 해제한다")
    fun recoverAsRetryWait() = runSuspend {
        val notification = processingNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val deduplication = FakeDeduplicationPort()
        val service = service(persistence, deduplication, maxAttempts = 3)

        val result = service.recoverStaleProcessing()

        assertEquals(1, result.staleProcessingFound)
        assertEquals(1, result.staleProcessingRecovered)
        assertEquals(1, result.recoveredToRetryWait)
        assertEquals(0, result.recoveredToDead)
        assertEquals(0, result.staleProcessingSkipped)
        assertEquals(NotificationStatus.RETRY_WAIT, persistence.saved.last().status)
        assertEquals(RetryFailureCode.DISPATCH_PROCESSING_TIMEOUT.defaultMessage, persistence.saved.last().failureReason)
        assertEquals(1, persistence.saved.last().dispatchAttempts)
        assertEquals(listOf("notification:dispatch:${notification.id.id}"), deduplication.releasedKeys)
    }

    @Test
    @DisplayName("재시도 한도에 도달한 stale PROCESSING 알림은 DEAD로 회수한다")
    fun recoverAsDead() = runSuspend {
        val notification = processingNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val deduplication = FakeDeduplicationPort()
        val service = service(persistence, deduplication, maxAttempts = 1)

        val result = service.recoverStaleProcessing()

        assertEquals(1, result.staleProcessingFound)
        assertEquals(1, result.staleProcessingRecovered)
        assertEquals(0, result.recoveredToRetryWait)
        assertEquals(1, result.recoveredToDead)
        assertEquals(0, result.staleProcessingSkipped)
        assertEquals(NotificationStatus.DEAD, persistence.saved.last().status)
        assertEquals(1, persistence.saved.last().dispatchAttempts)
        assertEquals(emptyList(), deduplication.releasedKeys)
    }

    @Test
    @DisplayName("stale PROCESSING 회수 중 claim 조건이 불일치하면 저장하지 않고 skipped로 집계한다")
    fun skipWhenClaimMismatch() = runSuspend {
        val notification = processingNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val dispatchPersistence = FakeNotificationDispatchPersistencePort(persistence).also {
            it.forceClaimMismatch = true
        }
        val deduplication = FakeDeduplicationPort()
        val service = service(
            persistence = persistence,
            dispatchPersistence = dispatchPersistence,
            deduplication = deduplication,
            maxAttempts = 3,
        )

        val result = service.recoverStaleProcessing()

        assertEquals(1, result.staleProcessingFound)
        assertEquals(0, result.staleProcessingRecovered)
        assertEquals(0, result.recoveredToRetryWait)
        assertEquals(0, result.recoveredToDead)
        assertEquals(1, result.staleProcessingSkipped)
        assertEquals(emptyList(), persistence.saved)
        assertEquals(emptyList(), deduplication.releasedKeys)
    }

    @Test
    @DisplayName("visibility timeout 안의 PROCESSING 알림은 회수하지 않는다")
    fun skipFreshProcessing() = runSuspend {
        val notification = processingNotification(claimedSecondsAgo = 10)
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val deduplication = FakeDeduplicationPort()
        val service = service(persistence, deduplication, maxAttempts = 3)

        val result = service.recoverStaleProcessing()

        assertEquals(0, result.staleProcessingFound)
        assertEquals(0, result.staleProcessingRecovered)
        assertEquals(0, result.staleProcessingSkipped)
        assertEquals(emptyList(), persistence.saved)
    }

    private fun service(
        persistence: FakeNotificationPersistencePort,
        deduplication: FakeDeduplicationPort,
        maxAttempts: Int,
        dispatchPersistence: FakeNotificationDispatchPersistencePort = FakeNotificationDispatchPersistencePort(persistence),
    ): RecoverStaleProcessingDispatchService {
        return RecoverStaleProcessingDispatchService(
            notificationPersistencePort = persistence,
            dispatchPersistencePort = dispatchPersistence,
            deduplicationPort = deduplication,
            policy = DispatchNotificationPolicy(
                workerId = "worker-1",
                dedupeTtl = DEDUPE_TTL,
                idempotencyKeyTtl = IDEMPOTENCY_KEY_TTL,
                retryPolicy = RetryPolicy(
                    maxAttempts = maxAttempts,
                    baseDelay = Duration.ofSeconds(10),
                    maxDelay = Duration.ofMinutes(1),
                ),
                processingVisibilityTimeout = Duration.ofSeconds(30),
                recoveryBatchSize = 10,
            ),
            clock = CLOCK,
        )
    }

    private fun processingNotification(claimedSecondsAgo: Long = 60): Notification {
        return Notification.request(
            requestId = REQUEST_ID,
            requester = REQUESTER,
            channel = NotificationChannel.SLACK,
            recipientId = RECIPIENT_ID,
            message = MESSAGE,
            origin = NotificationOrigin.NONE,
            now = NOW.minusSeconds(120),
        )
            .markPublished(NOW.minusSeconds(90))
            .markProcessing(NOW.minusSeconds(claimedSecondsAgo), "worker-1")
    }
}
