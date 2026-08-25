package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationPublishPersistencePort
import me.rgunny.kachi.notification.domain.NotificationOutbox
import me.rgunny.kachi.notification.domain.NotificationOutboxStatus
import java.time.Instant

class FakeNotificationPublishPersistencePort(
    private val notificationPersistencePort: FakeNotificationPersistencePort,
    private val outboxPersistencePort: FakeOutboxPersistencePort,
) : NotificationPublishPersistencePort {

    override suspend fun savePublished(
        outbox: NotificationOutbox,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
        now: Instant,
    ): Boolean {
        if (!claimMatches(outbox, expectedClaimedAt, expectedClaimedBy)) {
            return false
        }
        outboxPersistencePort.save(outbox)

        val notification = notificationPersistencePort.findById(outbox.notificationId)
            ?: throw IllegalStateException("notification not found. notificationId=${outbox.notificationId}")
        notification.markPublished(now)
        notificationPersistencePort.save(notification)
        return true
    }

    override suspend fun savePublishFailed(
        outbox: NotificationOutbox,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
        now: Instant,
        reason: String,
    ): Boolean {
        if (!claimMatches(outbox, expectedClaimedAt, expectedClaimedBy)) {
            return false
        }
        outboxPersistencePort.save(outbox)

        val notification = notificationPersistencePort.findById(outbox.notificationId)
            ?: throw IllegalStateException("notification not found. notificationId=${outbox.notificationId}")
        notification.markPublishFailed(now, reason)
        notificationPersistencePort.save(notification)
        return true
    }

    /**
     * 저장된 행이 아직 같은 claim을 들고 있는 PUBLISHING일 때만 결과를 반영한다.
     * MongoDB 어댑터의 `_id + PUBLISHING + claim` findAndModify 조건과 같은 규칙이다.
     */
    private suspend fun claimMatches(
        outbox: NotificationOutbox,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
    ): Boolean {
        val stored = outboxPersistencePort.findById(outbox.id) ?: return false

        return stored.outboxStatus == NotificationOutboxStatus.PUBLISHING &&
            stored.claimedAt == expectedClaimedAt &&
            stored.claimedBy == expectedClaimedBy
    }
}
