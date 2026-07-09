package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.retry.RetryPolicy
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxStatus
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.fake.FakeDispatchPublisher
import me.rgunny.kachi.notification.fake.FakeNotificationPersistencePort
import me.rgunny.kachi.notification.fake.FakeNotificationPublishPersistencePort
import me.rgunny.kachi.notification.fake.FakeOutboxPersistencePort
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.CLOCK
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.DISPATCH_TOPIC
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.MESSAGE
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.NOW
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.RECIPIENT
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.REQUESTER
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.REQUEST_ID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("PublishNotificationDispatchService")
class PublishNotificationDispatchServiceTest {
    private val now = NOW
    private val clock = CLOCK

    @Test
    @DisplayName("publishable outbox 발행 성공 시 outbox와 notification을 발행 완료 처리한다")
    fun publishPending() = runSuspend {
        val notification = requestedNotification()
        val outbox = outbox(notification.id)
        val notificationPersistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val outboxPersistence = FakeOutboxPersistencePort(publishable = listOf(outbox))
        val publisher = FakeDispatchPublisher()
        val service = service(notificationPersistence, outboxPersistence, publisher)

        val result = service.publishPending()

        assertEquals(1, result.processed)
        assertEquals(1, result.published)
        assertEquals(0, result.failed)
        assertEquals(NotificationOutboxStatus.PUBLISHED, outboxPersistence.saved.last().outboxStatus)
        assertEquals(NotificationStatus.PUBLISHED, notificationPersistence.saved.last().status)
        assertEquals(1, publisher.publishCount)
    }

    @Test
    @DisplayName("publishable outbox claim에 실패하면 처리하지 않는다")
    fun skipWhenClaimFails() = runSuspend {
        val notification = requestedNotification()
        val outbox = outbox(notification.id)
        val notificationPersistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val outboxPersistence = FakeOutboxPersistencePort(
            publishable = listOf(outbox),
            claimEnabled = false,
        )
        val publisher = FakeDispatchPublisher()
        val service = service(notificationPersistence, outboxPersistence, publisher)

        val result = service.publishPending()

        assertEquals(0, result.processed)
        assertEquals(0, result.published)
        assertEquals(0, result.failed)
        assertEquals(0, publisher.publishCount)
        assertTrue(outboxPersistence.saved.isEmpty())
    }

    @Test
    @DisplayName("publisher 예외는 outbox retry와 notification publish failure로 기록한다")
    fun publishFailure() = runSuspend {
        val notification = requestedNotification()
        val outbox = outbox(notification.id)
        val notificationPersistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val outboxPersistence = FakeOutboxPersistencePort(publishable = listOf(outbox))
        val publisher = FakeDispatchPublisher(failure = IllegalStateException("broker-down"))
        val service = service(notificationPersistence, outboxPersistence, publisher)

        val result = service.publishPending()

        assertEquals(1, result.processed)
        assertEquals(0, result.published)
        assertEquals(1, result.failed)
        assertEquals(NotificationOutboxStatus.PENDING, outboxPersistence.saved.last().outboxStatus)
        assertEquals(1, outboxPersistence.saved.last().retryCount)
        assertEquals("broker-down", outboxPersistence.saved.last().lastError)
        assertEquals(NotificationStatus.PUBLISH_FAILED, notificationPersistence.saved.last().status)
        assertEquals("broker-down", notificationPersistence.saved.last().failureReason)
    }

    @Test
    @DisplayName("stale PUBLISHING outbox는 timeout 실패로 재시도 대상에 포함한다")
    fun recoverStalePublishing() = runSuspend {
        val notification = requestedNotification()
        val staleOutbox = outbox(notification.id).also {
            it.markPublishing(now.minusSeconds(60), "publisher-old")
        }
        val notificationPersistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val outboxPersistence = FakeOutboxPersistencePort(stale = listOf(staleOutbox))
        val service = service(notificationPersistence, outboxPersistence, FakeDispatchPublisher())

        val result = service.publishPending()

        assertEquals(1, result.processed)
        assertEquals(0, result.published)
        assertEquals(1, result.failed)
        assertEquals(NotificationOutboxStatus.PENDING, outboxPersistence.saved.last().outboxStatus)
        assertEquals("publishing-timeout", outboxPersistence.saved.last().lastError)
        assertEquals(NotificationStatus.PUBLISH_FAILED, notificationPersistence.saved.last().status)
        assertEquals("publishing-timeout", notificationPersistence.saved.last().failureReason)
    }

    @Test
    @DisplayName("publisher 실패가 retry 한도에 도달하면 outbox는 DEAD가 된다")
    fun publishFailureExhausted() = runSuspend {
        val notification = requestedNotification()
        val outbox = outbox(notification.id)
        val notificationPersistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val outboxPersistence = FakeOutboxPersistencePort(publishable = listOf(outbox))
        val publisher = FakeDispatchPublisher(failure = IllegalStateException("broker-down"))
        val service = service(
            notificationPersistence = notificationPersistence,
            outboxPersistence = outboxPersistence,
            publisher = publisher,
            maxAttempts = 1,
        )

        val result = service.publishPending()

        assertEquals(1, result.failed)
        assertEquals(NotificationOutboxStatus.DEAD, outboxPersistence.saved.last().outboxStatus)
        assertEquals(NotificationStatus.PUBLISH_FAILED, notificationPersistence.saved.last().status)
    }

    private fun service(
        notificationPersistence: FakeNotificationPersistencePort,
        outboxPersistence: FakeOutboxPersistencePort,
        publisher: FakeDispatchPublisher,
        maxAttempts: Int = 3,
    ): PublishNotificationDispatchService {
        val publishPersistence = FakeNotificationPublishPersistencePort(
            notificationPersistencePort = notificationPersistence,
            outboxPersistencePort = outboxPersistence,
        )

        return PublishNotificationDispatchService(
            outboxPersistencePort = outboxPersistence,
            publishPersistencePort = publishPersistence,
            dispatchPublisher = publisher,
            policy = OutboxPublishPolicy(
                batchSize = 10,
                publisherId = "publisher-1",
                retryPolicy = RetryPolicy(
                    maxAttempts = maxAttempts,
                    baseDelay = Duration.ofSeconds(10),
                    maxDelay = Duration.ofMinutes(1),
                ),
                publishingVisibilityTimeout = Duration.ofSeconds(30),
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

    private fun outbox(notificationId: NotificationId): NotificationOutbox {
        return NotificationOutbox.create(
            notificationId = notificationId,
            topic = DISPATCH_TOPIC,
            partitionKey = RECIPIENT,
            eventPayload = "{}",
            now = now.minusSeconds(10),
        )
    }
}
