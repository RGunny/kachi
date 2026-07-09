package me.rgunny.kachi.notification.worker.fake

import me.rgunny.kachi.notification.application.port.outbound.NotificationDispatchPersistencePort
import me.rgunny.kachi.notification.domain.Notification

class FakeNotificationDispatchPersistencePort(
    private val notificationPersistencePort: FakeNotificationPersistencePort,
) : NotificationDispatchPersistencePort {

    override suspend fun saveFinalized(notification: Notification): Notification {
        return notificationPersistencePort.save(notification)
    }
}
