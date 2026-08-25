package me.rgunny.kachi.notification.worker.fake

import me.rgunny.kachi.notification.application.port.outbound.persistence.NotificationPersistencePort
import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * DispatchNotificationService의 상태 전이와 claim 흐름을 검증하기 위한 in-memory persistence port fake.
 */
class FakeNotificationPersistencePort : NotificationPersistencePort {
    private val notifications = ConcurrentHashMap<NotificationId, Notification>()

    fun put(notification: Notification) {
        notifications[notification.id] = notification
    }

    fun require(notificationId: NotificationId): Notification {
        return notifications.getValue(notificationId)
    }

    override suspend fun save(notification: Notification): Notification {
        notifications[notification.id] = notification
        return notification
    }

    override suspend fun findById(notificationId: NotificationId): Notification? {
        return notifications[notificationId]
    }

    override suspend fun findByRequestId(requestId: String): Notification? {
        return notifications.values.firstOrNull { it.requestId == requestId }
    }

    override suspend fun findStaleProcessing(
        threshold: Instant,
        batchSize: Int,
    ): List<Notification> {
        return notifications.values
            .filter { it.status == NotificationStatus.PROCESSING && it.claimedAt != null && it.claimedAt!! < threshold }
            .take(batchSize)
    }

    override suspend fun claimFromPublished(
        notificationId: NotificationId,
        workerId: String,
        now: Instant,
    ): Notification? {
        return claim(notificationId, NotificationStatus.PUBLISHED, workerId, now)
    }

    override suspend fun claimFromRetryWait(
        notificationId: NotificationId,
        workerId: String,
        now: Instant,
    ): Notification? {
        return claim(notificationId, NotificationStatus.RETRY_WAIT, workerId, now)
    }

    private fun claim(
        notificationId: NotificationId,
        expectedStatus: NotificationStatus,
        workerId: String,
        now: Instant,
    ): Notification? {
        val notification = notifications[notificationId] ?: return null
        if (notification.status != expectedStatus) {
            return null
        }
        return notification.markProcessing(now, workerId).also { notifications[it.id] = it }
    }
}
