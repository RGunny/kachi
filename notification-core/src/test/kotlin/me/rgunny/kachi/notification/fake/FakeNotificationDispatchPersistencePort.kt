package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationDispatchPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import java.time.Instant

class FakeNotificationDispatchPersistencePort(
    private val notificationPersistencePort: FakeNotificationPersistencePort,
) : NotificationDispatchPersistencePort {

    var forceClaimMismatch: Boolean = false

    override suspend fun saveFinalizedIfProcessingClaimMatches(
        notification: Notification,
        expectedClaimedAt: Instant,
        expectedClaimedBy: String,
    ): Notification? {
        if (forceClaimMismatch) {
            return null
        }
        return notificationPersistencePort.save(notification)
    }
}
