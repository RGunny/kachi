package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.NotificationAdminPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationOutbox

class FakeNotificationAdminPersistencePort(
    private val notificationPersistencePort: FakeNotificationPersistencePort,
    private val outboxPersistencePort: FakeOutboxPersistencePort,
) : NotificationAdminPersistencePort {
    var forceMismatch: Boolean = false

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
