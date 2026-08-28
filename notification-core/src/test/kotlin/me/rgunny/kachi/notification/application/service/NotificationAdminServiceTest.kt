package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.inbound.admin.model.DeadNotificationQuery
import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationHistoryQuery
import me.rgunny.kachi.notification.application.port.inbound.admin.model.RecoverDeadNotificationCommand
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.exception.InvalidNotificationStateException
import me.rgunny.kachi.notification.fake.FakeEventSerializer
import me.rgunny.kachi.notification.fake.FakeNotificationAdminPersistencePort
import me.rgunny.kachi.notification.fake.FakeNotificationPersistencePort
import me.rgunny.kachi.notification.fake.FakeOutboxPersistencePort
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.CLOCK
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.MESSAGE
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.NOW
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.REQUESTER
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.notification.domain.NotificationOrigin

@DisplayName("NotificationAdminService")
class NotificationAdminServiceTest {

    @Test
    @DisplayName("DEAD notification 목록을 조회한다")
    fun findDead() = runSuspend {
        val dead = deadNotification()
        val requested = requestedNotification("request-live")
        val notificationPersistence = FakeNotificationPersistencePort().also {
            it.put(dead)
            it.put(requested)
        }
        val adminPersistence = FakeNotificationAdminPersistencePort(
            notificationPersistence,
            FakeOutboxPersistencePort(),
        )
        val service = service(notificationPersistence, adminPersistence, FakeEventSerializer())

        val result = service.findDead(DeadNotificationQuery(batchSize = 10))

        assertEquals(1, result.notifications.size)
        assertEquals(dead.id, result.notifications.single().notificationId)
        assertEquals(NotificationStatus.DEAD, result.notifications.single().status)
    }

    @Test
    @DisplayName("notification 상태 전이 history를 조회한다")
    fun findHistories() = runSuspend {
        val notification = deadNotification()
        val notificationPersistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val adminPersistence = FakeNotificationAdminPersistencePort(
            notificationPersistence,
            FakeOutboxPersistencePort(),
        ).also {
            it.histories[notification.id] = notification.uncommittedHistories.toMutableList()
        }
        val service = service(notificationPersistence, adminPersistence, FakeEventSerializer())

        val result = service.findHistories(NotificationHistoryQuery(notification.id, batchSize = 10))

        assertEquals(4, result.histories.size)
        assertEquals(NotificationStatus.REQUESTED, result.histories.first().fromStatus)
        assertEquals(NotificationStatus.DEAD, result.histories.last().toStatus)
    }

    @Test
    @DisplayName("DEAD notification을 REQUESTED로 되살리고 새 outbox를 생성한다")
    fun recoverDead() = runSuspend {
        val notification = deadNotification()
        val notificationPersistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val outboxPersistence = FakeOutboxPersistencePort()
        val adminPersistence = FakeNotificationAdminPersistencePort(notificationPersistence, outboxPersistence)
        val serializer = FakeEventSerializer()
        val service = service(notificationPersistence, adminPersistence, serializer)

        val result = service.recoverDead(
            RecoverDeadNotificationCommand(notification.id, "operator retry")
        )

        assertEquals(NotificationStatus.REQUESTED, result.status)
        assertEquals(NotificationStatus.REQUESTED, notificationPersistence.saved.single().status)
        assertEquals(null, notificationPersistence.saved.single().failureReason)
        assertEquals(0, notificationPersistence.saved.single().dispatchAttempts)
        assertEquals(1, outboxPersistence.saved.size)
        assertEquals(notification.id, outboxPersistence.saved.single().notificationId)
        assertEquals("notification.dispatch", outboxPersistence.saved.single().topic)
        assertEquals("payload-${notification.requestId}", outboxPersistence.saved.single().eventPayload)
        assertEquals(notification.id, serializer.messages.single().notificationId)
    }

    @Test
    @DisplayName("저장 시점에 DEAD 조건이 불일치하면 실패한다")
    fun recoverDeadMismatch() {
        val notification = deadNotification()
        val notificationPersistence = FakeNotificationPersistencePort().also { it.put(notification) }
        val outboxPersistence = FakeOutboxPersistencePort()
        val adminPersistence = FakeNotificationAdminPersistencePort(notificationPersistence, outboxPersistence).also {
            it.forceMismatch = true
        }
        val service = service(notificationPersistence, adminPersistence, FakeEventSerializer())

        assertFailsWith<InvalidNotificationStateException> {
            runSuspend {
                service.recoverDead(
                    RecoverDeadNotificationCommand(notification.id, "operator retry")
                )
            }
        }

        assertEquals(emptyList(), notificationPersistence.saved)
        assertEquals(emptyList(), outboxPersistence.saved)
    }

    private fun service(
        notificationPersistence: FakeNotificationPersistencePort,
        adminPersistence: FakeNotificationAdminPersistencePort,
        serializer: FakeEventSerializer,
    ): NotificationAdminService {
        return NotificationAdminService(
            notificationPersistencePort = notificationPersistence,
            adminPersistencePort = adminPersistence,
            eventSerializer = serializer,
            policy = RequestNotificationPolicy(
                dedupeTtl = java.time.Duration.ofMinutes(10),
                dispatchTopic = "notification.dispatch",
            ),
            clock = CLOCK,
        )
    }

    private fun deadNotification(): Notification {
        return requestedNotification("request-dead")
            .markPublished(NOW.plusSeconds(1))
            .markProcessing(NOW.plusSeconds(2), "worker-1")
            .markFailed(NOW.plusSeconds(3), "invalid recipient")
            .markDead(NOW.plusSeconds(4), "invalid recipient")
    }

    private fun requestedNotification(requestId: String): Notification {
        return Notification.request(
            requestId = requestId,
            requester = REQUESTER,
            channel = NotificationChannel.SLACK,
            recipientId = "user-1",
            message = MESSAGE,
            origin = NotificationOrigin.NONE,
            now = NOW,
        )
    }
}
