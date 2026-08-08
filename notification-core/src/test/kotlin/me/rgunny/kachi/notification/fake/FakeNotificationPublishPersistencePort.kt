package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationPublishPersistencePort
import me.rgunny.kachi.notification.domain.NotificationOutbox
import java.time.Instant

class FakeNotificationPublishPersistencePort(
    private val notificationPersistencePort: FakeNotificationPersistencePort,
    private val outboxPersistencePort: FakeOutboxPersistencePort,
) : NotificationPublishPersistencePort {

    override suspend fun savePublished(
        outbox: NotificationOutbox,
        now: Instant,
    ) {
        outboxPersistencePort.save(outbox)

        val notification = notificationPersistencePort.findById(outbox.notificationId)
            ?: throw IllegalStateException("notification not found. notificationId=${outbox.notificationId}")
        notification.markPublished(now)
        notificationPersistencePort.save(notification)
    }

    override suspend fun savePublishFailed(
        outbox: NotificationOutbox,
        now: Instant,
        reason: String,
    ) {
        outboxPersistencePort.save(outbox)

        val notification = notificationPersistencePort.findById(outbox.notificationId)
            ?: throw IllegalStateException("notification not found. notificationId=${outbox.notificationId}")
        notification.markPublishFailed(now, reason)
        notificationPersistencePort.save(notification)
    }
}
