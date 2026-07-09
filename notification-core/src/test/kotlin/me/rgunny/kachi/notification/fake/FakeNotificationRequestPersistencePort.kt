package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.NotificationRequestPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationOutbox

class FakeNotificationRequestPersistencePort(
    private val notificationPersistencePort: FakeNotificationPersistencePort,
    private val outboxPersistencePort: FakeOutboxPersistencePort,
) : NotificationRequestPersistencePort {
    var saveFailure: RuntimeException? = null

    override suspend fun saveRequested(
        notification: Notification,
        outbox: NotificationOutbox,
    ): Notification {
        saveFailure?.let { throw it }

        val savedNotification = notificationPersistencePort.save(notification)
        outboxPersistencePort.save(outbox)
        return savedNotification
    }
}
