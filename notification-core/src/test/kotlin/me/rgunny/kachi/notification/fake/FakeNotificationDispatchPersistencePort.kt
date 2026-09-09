package me.rgunny.kachi.notification.fake

import java.time.Instant
import me.rgunny.kachi.notification.application.port.outbound.notification.NotificationDispatchPersistencePort
import me.rgunny.kachi.notification.domain.Notification

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
