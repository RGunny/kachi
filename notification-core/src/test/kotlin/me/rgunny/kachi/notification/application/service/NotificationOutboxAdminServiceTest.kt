package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.inbound.outbox.model.DeadNotificationOutboxQuery
import me.rgunny.kachi.notification.application.port.inbound.outbox.model.RecoverNotificationOutboxCommand
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxStatus
import me.rgunny.kachi.notification.exception.NotificationOutboxNotFoundException
import me.rgunny.kachi.notification.fake.FakeOutboxPersistencePort
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.CLOCK
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.DISPATCH_TOPIC
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.NOW
import me.rgunny.kachi.notification.fixture.NotificationTestFixture.RECIPIENT_ID
import me.rgunny.kachi.notification.domain.retry.RetryPolicy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NotificationOutboxAdminService")
class NotificationOutboxAdminServiceTest {

    @Test
    @DisplayName("DEAD outbox 목록을 조회한다")
    fun findDead() = runSuspend {
        val deadOutbox = deadOutbox()
        val service = NotificationOutboxAdminService(
            outboxPersistencePort = FakeOutboxPersistencePort(dead = listOf(deadOutbox)),
            clock = CLOCK,
        )

        val result = service.findDead(DeadNotificationOutboxQuery(batchSize = 10))

        assertEquals(1, result.outboxes.size)
        assertEquals(deadOutbox.id, result.outboxes.first().outboxId)
        assertEquals(NotificationOutboxStatus.DEAD, result.outboxes.first().status)
    }

    @Test
    @DisplayName("DEAD outbox를 PENDING으로 복구한다")
    fun recover() = runSuspend {
        val deadOutbox = deadOutbox()
        val persistence = FakeOutboxPersistencePort(dead = listOf(deadOutbox))
        val service = NotificationOutboxAdminService(persistence, CLOCK)

        val result = service.recover(RecoverNotificationOutboxCommand(deadOutbox.id))

        assertEquals(NOW, result.recoveredAt)
        assertEquals(NotificationOutboxStatus.PENDING, result.outbox.status)
        assertEquals(0, result.outbox.retryCount)
        assertEquals(NOW, result.outbox.nextRetryAt)
        assertEquals(null, result.outbox.lastError)
        assertEquals(NotificationOutboxStatus.PENDING, persistence.saved.last().outboxStatus)
    }

    @Test
    @DisplayName("존재하지 않는 outbox 복구 요청은 실패한다")
    fun recoverNotFound() = runSuspend {
        val service = NotificationOutboxAdminService(FakeOutboxPersistencePort(), CLOCK)

        assertFailsWith<NotificationOutboxNotFoundException> {
            service.recover(RecoverNotificationOutboxCommand(deadOutbox().id))
        }
        Unit
    }

    private fun deadOutbox(): NotificationOutbox {
        return NotificationOutbox.create(
            notificationId = NotificationId.newId(),
            topic = DISPATCH_TOPIC,
            partitionKey = RECIPIENT_ID,
            eventPayload = "{}",
            now = NOW.minusSeconds(60),
        ).markPublishing(NOW.minusSeconds(30), "publisher-1")
            .recordFailure(
                reason = "broker-down",
                retryPolicy = RetryPolicy(
                    maxAttempts = 1,
                    baseDelay = Duration.ofSeconds(1),
                    maxDelay = Duration.ofSeconds(1),
                ),
                now = NOW.minusSeconds(30),
            )
    }
}
