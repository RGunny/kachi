package me.rgunny.kachi.notification.worker.fake

import java.time.Instant
import me.rgunny.kachi.notification.application.port.outbound.notification.NotificationDispatchPersistencePort
import me.rgunny.kachi.notification.domain.Notification

class FakeNotificationDispatchPersistencePort(
    private val notificationPersistencePort: FakeNotificationPersistencePort,
) : NotificationDispatchPersistencePort {

    override suspend fun saveFinalizedIfProcessingClaimMatches(
        notification: Notification,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
    ): Notification? {
        return notificationPersistencePort.save(notification)
    }
}
