package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.dto.RequestNotificationCommand
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.fake.FakeDeduplicationPort
import me.rgunny.kachi.notification.fake.FakeEventSerializer
import me.rgunny.kachi.notification.fake.FakeNotificationPersistencePort
import me.rgunny.kachi.notification.fake.FakeOutboxPersistencePort
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.CLOCK
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.DEDUPE_TTL
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.DISPATCH_TOPIC
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.MESSAGE
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.NOW
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.RECIPIENT
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.REQUESTER
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.REQUEST_ID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("RequestNotificationService")
class RequestNotificationServiceTest {
    private val now = NOW
    private val clock = CLOCK

    @Test
    @DisplayName("신규 요청은 notification과 dispatch outbox를 저장한다")
    fun request() = runSuspend {
        val notificationPersistence = FakeNotificationPersistencePort()
        val outboxPersistence = FakeOutboxPersistencePort()
        val deduplication = FakeDeduplicationPort()
        val serializer = FakeEventSerializer()
        val service = service(notificationPersistence, outboxPersistence, deduplication, serializer)

        val result = service.request(command())

        assertEquals(NotificationStatus.REQUESTED, result.status)
        assertFalse(result.duplicated)
        assertEquals(now, result.acceptedAt)
        assertEquals(1, notificationPersistence.saved.size)
        assertEquals(1, outboxPersistence.saved.size)
        assertEquals(DISPATCH_TOPIC, outboxPersistence.saved.first().topic)
        assertEquals(RECIPIENT, outboxPersistence.saved.first().partitionKey)
        assertEquals("payload-$REQUEST_ID", outboxPersistence.saved.first().eventPayload)
        assertEquals(command().requestId, serializer.messages.single().requestId)
    }

    @Test
    @DisplayName("중복 요청은 기존 notification 결과를 반환하고 새로 저장하지 않는다")
    fun duplicatedRequest() = runSuspend {
        val existing = Notification.request(
            requestId = REQUEST_ID,
            requester = REQUESTER,
            channel = NotificationChannel.SLACK,
            recipient = RECIPIENT,
            message = MESSAGE,
            now = now.minusSeconds(10),
        )
        val notificationPersistence = FakeNotificationPersistencePort().also {
            it.put(existing)
        }
        val outboxPersistence = FakeOutboxPersistencePort()
        val deduplication = FakeDeduplicationPort(acquireResult = false)
        val service = service(notificationPersistence, outboxPersistence, deduplication, FakeEventSerializer())

        val result = service.request(command())

        assertEquals(existing.id, result.notificationId)
        assertEquals(NotificationStatus.REQUESTED, result.status)
        assertTrue(result.duplicated)
        assertEquals(existing.requestedAt, result.acceptedAt)
        assertTrue(notificationPersistence.saved.isEmpty())
        assertTrue(outboxPersistence.saved.isEmpty())
    }

    @Test
    @DisplayName("신규 요청 저장 중 실패하면 멱등 마커를 해제하고 예외를 전파한다")
    fun releaseDedupeWhenRequestFails() {
        val notificationPersistence = FakeNotificationPersistencePort().also {
            it.saveFailure = IllegalStateException("db-down")
        }
        val deduplication = FakeDeduplicationPort()
        val service = service(
            notificationPersistence,
            FakeOutboxPersistencePort(),
            deduplication,
            FakeEventSerializer(),
        )

        assertFailsWith<IllegalStateException> {
            runSuspend { service.request(command()) }
        }

        assertEquals(listOf("notification:request:$REQUEST_ID"), deduplication.releasedKeys)
    }

    private fun service(
        notificationPersistence: FakeNotificationPersistencePort,
        outboxPersistence: FakeOutboxPersistencePort,
        deduplication: FakeDeduplicationPort,
        serializer: FakeEventSerializer,
    ): RequestNotificationService {
        return RequestNotificationService(
            notificationPersistencePort = notificationPersistence,
            outboxPersistencePort = outboxPersistence,
            deduplicationPort = deduplication,
            eventSerializer = serializer,
            policy = RequestNotificationPolicy(
                dedupeTtl = DEDUPE_TTL,
                dispatchTopic = DISPATCH_TOPIC,
            ),
            clock = clock,
        )
    }

    private fun command(): RequestNotificationCommand {
        return RequestNotificationCommand(
            requestId = REQUEST_ID,
            requester = REQUESTER,
            channel = NotificationChannel.SLACK,
            recipient = RECIPIENT,
            message = MESSAGE,
        )
    }
}
