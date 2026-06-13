package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.retry.FailureCategory
import me.rgunny.kachi.notification.retry.FailureSource
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryFailureCode
import me.rgunny.kachi.notification.retry.RetryPolicy
import me.rgunny.kachi.notification.application.port.dto.DispatchNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.SendNotificationResult
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.exception.dispatch.DispatchNotReadyException
import me.rgunny.kachi.notification.exception.sender.NonRetryableSendException
import me.rgunny.kachi.notification.exception.sender.RetryableSendException
import me.rgunny.kachi.notification.fake.FakeDeduplicationPort
import me.rgunny.kachi.notification.fake.FakeIdempotencyKeyPort
import me.rgunny.kachi.notification.fake.FakeNotificationPersistencePort
import me.rgunny.kachi.notification.fake.FakeSender
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.CLOCK
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.DEDUPE_TTL
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.IDEMPOTENCY_KEY_TTL
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.MESSAGE
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.NOW
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.RECIPIENT
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.REQUESTER
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.REQUEST_ID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("DispatchNotificationService")
class DispatchNotificationServiceTest {
    private val now = NOW
    private val clock = CLOCK

    @Test
    @DisplayName("중복 dispatch는 현재 상태를 반환하고 sender를 호출하지 않는다")
    fun duplicatedDispatch() = runSuspend {
        val notification = publishedNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val deduplication = FakeDeduplicationPort(acquireResult = false)
        val sender = FakeSender()
        val service = service(persistence, deduplication, FakeIdempotencyKeyPort(), sender)

        val result = service.dispatch(command(notification.id))

        assertEquals(NotificationStatus.PUBLISHED, result.status)
        assertTrue(result.duplicated)
        assertFalse(result.dispatchAttempted)
        assertEquals(0, sender.sendCount)
    }

    @Test
    @DisplayName("PUBLISHED 알림 claim 후 발송 성공이면 SENT로 완료한다")
    fun dispatchSuccess() = runSuspend {
        val notification = publishedNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val sender = FakeSender(result = SendNotificationResult.Success("provider-1"))
        val service = service(persistence, FakeDeduplicationPort(), FakeIdempotencyKeyPort(), sender)

        val result = service.dispatch(command(notification.id))

        assertEquals(NotificationStatus.SENT, result.status)
        assertFalse(result.duplicated)
        assertTrue(result.dispatchAttempted)
        assertEquals(NotificationStatus.SENT, persistence.saved.last().status)
        assertEquals(1, persistence.saved.last().dispatchAttempts)
        assertEquals(1, sender.sendCount)
        assertEquals("vendor-key", sender.commands.single().idempotencyKey)
    }

    @Test
    @DisplayName("PUBLISHED claim 실패 후 RETRY_WAIT claim에 성공하면 발송한다")
    fun dispatchFromRetryWait() = runSuspend {
        val notification = retryWaitNotification()
        val persistence = FakeNotificationPersistencePort().also {
            it.put(notification)
            it.claimPublishedEnabled = false
        }
        val sender = FakeSender(result = SendNotificationResult.Success())
        val service = service(persistence, FakeDeduplicationPort(), FakeIdempotencyKeyPort(), sender)

        val result = service.dispatch(command(notification.id))

        assertEquals(NotificationStatus.SENT, result.status)
        assertEquals(1, persistence.retryWaitClaimCount)
    }

    @Test
    @DisplayName("rate limit과 transient failure는 재시도 가능하면 RETRY_WAIT로 완료하고 dedupe를 해제한다")
    fun retryableFailure() = runSuspend {
        val notification = publishedNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val deduplication = FakeDeduplicationPort()
        val sender = FakeSender(result = SendNotificationResult.RateLimited("rate-limited"))
        val service = service(
            persistence = persistence,
            deduplication = deduplication,
            idempotency = FakeIdempotencyKeyPort(),
            sender = sender,
            maxAttempts = 2,
        )

        val result = service.dispatch(command(notification.id))

        assertEquals(NotificationStatus.RETRY_WAIT, result.status)
        assertEquals("rate-limited", persistence.saved.last().failureReason)
        assertEquals(1, persistence.saved.last().dispatchAttempts)
        assertEquals(listOf("notification:dispatch:${notification.id.id}"), deduplication.releasedKeys)
    }

    @Test
    @DisplayName("재시도 가능한 실패라도 한도에 도달하면 DEAD로 완료하고 dedupe를 유지한다")
    fun retryableFailureExhausted() = runSuspend {
        val notification = publishedNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val deduplication = FakeDeduplicationPort()
        val sender = FakeSender(result = SendNotificationResult.TransientFailure("vendor-timeout"))
        val service = service(
            persistence = persistence,
            deduplication = deduplication,
            idempotency = FakeIdempotencyKeyPort(),
            sender = sender,
            maxAttempts = 1,
        )

        val result = service.dispatch(command(notification.id))

        assertEquals(NotificationStatus.DEAD, result.status)
        assertEquals("vendor-timeout", persistence.saved.last().failureReason)
        assertTrue(deduplication.releasedKeys.isEmpty())
    }

    @Test
    @DisplayName("permanent failure는 DEAD로 완료한다")
    fun permanentFailure() = runSuspend {
        val notification = publishedNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val sender = FakeSender(result = SendNotificationResult.PermanentFailure("invalid-recipient"))
        val service = service(persistence, FakeDeduplicationPort(), FakeIdempotencyKeyPort(), sender)

        val result = service.dispatch(command(notification.id))

        assertEquals(NotificationStatus.DEAD, result.status)
        assertEquals("invalid-recipient", persistence.saved.last().failureReason)
    }

    @Test
    @DisplayName("claim 실패 상태가 REQUESTED면 dedupe를 해제하고 예외를 전파한다")
    fun requestedNotificationIsNotReady() {
        val notification = requestedNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val deduplication = FakeDeduplicationPort()
        val service = service(persistence, deduplication, FakeIdempotencyKeyPort(), FakeSender())

        assertFailsWith<DispatchNotReadyException> {
            runSuspend { service.dispatch(command(notification.id)) }
        }

        assertEquals(listOf("notification:dispatch:${notification.id.id}"), deduplication.releasedKeys)
    }

    @Test
    @DisplayName("sender 호출 중 예외가 발생하면 dedupe를 해제하고 예외를 전파한다")
    fun senderException() {
        val notification = publishedNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val deduplication = FakeDeduplicationPort()
        val sender = FakeSender(failure = IllegalStateException("sender-down"))
        val service = service(persistence, deduplication, FakeIdempotencyKeyPort(), sender)

        assertFailsWith<IllegalStateException> {
            runSuspend { service.dispatch(command(notification.id)) }
        }

        assertEquals(listOf("notification:dispatch:${notification.id.id}"), deduplication.releasedKeys)
    }

    @Test
    @DisplayName("sender가 retryable 예외를 던지면 RETRY_WAIT로 완료한다")
    fun senderRetryableException() = runSuspend {
        val notification = publishedNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val deduplication = FakeDeduplicationPort()
        val sender = FakeSender(
            failure = RetryableSendException(
                notificationId = notification.id,
                channel = NotificationChannel.SLACK,
                failure = RetryFailure.of(RetryFailureCode.VENDOR_TIMEOUT),
            )
        )
        val service = service(persistence, deduplication, FakeIdempotencyKeyPort(), sender)

        val result = service.dispatch(command(notification.id))

        assertEquals(NotificationStatus.RETRY_WAIT, result.status)
        assertEquals("vendor timeout", persistence.saved.last().failureReason)
        assertEquals(listOf("notification:dispatch:${notification.id.id}"), deduplication.releasedKeys)
    }

    @Test
    @DisplayName("sender가 non-retryable 예외를 던지면 DEAD로 완료한다")
    fun senderNonRetryableException() = runSuspend {
        val notification = publishedNotification()
        val persistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val sender = FakeSender(
            failure = NonRetryableSendException(
                notificationId = notification.id,
                channel = NotificationChannel.SLACK,
                failure = RetryFailure.of(RetryFailureCode.INVALID_RECIPIENT),
            )
        )
        val service = service(persistence, FakeDeduplicationPort(), FakeIdempotencyKeyPort(), sender)

        val result = service.dispatch(command(notification.id))

        assertEquals(NotificationStatus.DEAD, result.status)
        assertEquals("invalid recipient", persistence.saved.last().failureReason)
    }

    private fun service(
        persistence: FakeNotificationPersistencePort,
        deduplication: FakeDeduplicationPort,
        idempotency: FakeIdempotencyKeyPort,
        sender: FakeSender,
        maxAttempts: Int = 3,
    ): DispatchNotificationService {
        return DispatchNotificationService(
            notificationPersistencePort = persistence,
            deduplicationPort = deduplication,
            idempotencyKeyPort = idempotency,
            senderRouter = NotificationSenderRouter(listOf(sender)),
            policy = DispatchNotificationPolicy(
                workerId = "worker-1",
                dedupeTtl = DEDUPE_TTL,
                idempotencyKeyTtl = IDEMPOTENCY_KEY_TTL,
                retryPolicy = RetryPolicy(
                    maxAttempts = maxAttempts,
                    baseDelay = Duration.ofSeconds(10),
                    maxDelay = Duration.ofMinutes(1),
                ),
            ),
            clock = clock,
        )
    }

    private fun requestedNotification(): Notification {
        return Notification.request(
            requestId = REQUEST_ID,
            requester = REQUESTER,
            channel = NotificationChannel.SLACK,
            recipient = RECIPIENT,
            message = MESSAGE,
            now = now.minusSeconds(10),
        )
    }

    private fun publishedNotification(): Notification {
        return requestedNotification().also { it.markPublished(now.minusSeconds(5)) }
    }

    private fun retryWaitNotification(): Notification {
        return publishedNotification().also {
            it.markProcessing(now.minusSeconds(4))
            it.markFailed(now.minusSeconds(3), "rate-limited")
            it.markRetryWait(now.minusSeconds(2), "rate-limited")
        }
    }

    private fun command(notificationId: NotificationId): DispatchNotificationCommand {
        return DispatchNotificationCommand(
            notificationId = notificationId,
            requestId = REQUEST_ID,
            channel = NotificationChannel.SLACK,
            recipient = RECIPIENT,
            message = MESSAGE,
        )
    }
}
