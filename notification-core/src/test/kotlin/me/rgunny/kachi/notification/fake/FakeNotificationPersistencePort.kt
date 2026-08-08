package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import java.time.Instant

class FakeNotificationPersistencePort : NotificationPersistencePort {
    val saved = mutableListOf<Notification>()
    val notificationsByRequestId = mutableMapOf<String, Notification>()
    private val notificationsById = mutableMapOf<NotificationId, Notification>()
    val notifications: List<Notification>
        get() = notificationsById.values.toList()
    var saveFailure: RuntimeException? = null
    var claimPublishedEnabled = true
    var retryWaitClaimCount = 0

    fun put(notification: Notification) {
        notificationsById[notification.id] = notification
        notificationsByRequestId[notification.requestId] = notification
    }

    override suspend fun save(notification: Notification): Notification {
        saveFailure?.let { throw it }
        saved += notification
        put(notification)
        return notification
    }

    override suspend fun findById(notificationId: NotificationId): Notification? {
        return notificationsById[notificationId]
    }

    override suspend fun findByRequestId(requestId: String): Notification? {
        return notificationsByRequestId[requestId]
    }

    override suspend fun findStaleProcessing(
        threshold: Instant,
        batchSize: Int,
    ): List<Notification> {
        return notificationsById.values
            .filter { it.status == NotificationStatus.PROCESSING && it.claimedAt != null && it.claimedAt!! < threshold }
            .take(batchSize)
    }

    override suspend fun claimFromPublished(
        notificationId: NotificationId,
        workerId: String,
        now: Instant,
    ): Notification? {
        if (!claimPublishedEnabled) {
            return null
        }
        val notification = notificationsById[notificationId] ?: return null
        if (notification.status != NotificationStatus.PUBLISHED) {
            return null
        }
        notification.markProcessing(now, workerId)
        return notification
    }

    override suspend fun claimFromRetryWait(
        notificationId: NotificationId,
        workerId: String,
        now: Instant,
    ): Notification? {
        retryWaitClaimCount += 1
        val notification = notificationsById[notificationId] ?: return null
        if (notification.status != NotificationStatus.RETRY_WAIT) {
            return null
        }
        notification.markProcessing(now, workerId)
        return notification
    }
}
