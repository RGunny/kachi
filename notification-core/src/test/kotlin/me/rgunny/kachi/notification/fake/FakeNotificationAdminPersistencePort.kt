package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationAdminPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationHistory
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationOutbox

class FakeNotificationAdminPersistencePort(
    private val notificationPersistencePort: FakeNotificationPersistencePort,
    private val outboxPersistencePort: FakeOutboxPersistencePort,
) : NotificationAdminPersistencePort {
    var forceMismatch: Boolean = false
    val histories: MutableMap<NotificationId, MutableList<NotificationHistory>> = mutableMapOf()

    override suspend fun findDead(batchSize: Int): List<Notification> {
        return notificationPersistencePort.notifications
            .filter { it.status == me.rgunny.kachi.notification.domain.NotificationStatus.DEAD }
            .sortedByDescending { it.updatedAt }
            .take(batchSize)
    }

    override suspend fun findHistories(
        notificationId: NotificationId,
        batchSize: Int,
    ): List<NotificationHistory> {
        return histories[notificationId].orEmpty()
            .sortedBy { it.createdAt }
            .take(batchSize)
    }

    override suspend fun recoverDeadToRequested(
        notification: Notification,
        outbox: NotificationOutbox,
    ): Notification? {
        if (forceMismatch) {
            return null
        }

        val savedNotification = notificationPersistencePort.save(notification)
        outboxPersistencePort.save(outbox)
        return savedNotification
    }
}
