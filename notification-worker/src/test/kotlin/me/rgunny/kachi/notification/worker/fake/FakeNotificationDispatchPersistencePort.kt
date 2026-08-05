package me.rgunny.kachi.notification.worker.fake

import me.rgunny.kachi.notification.application.port.outbound.NotificationDispatchPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import java.time.Instant

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
