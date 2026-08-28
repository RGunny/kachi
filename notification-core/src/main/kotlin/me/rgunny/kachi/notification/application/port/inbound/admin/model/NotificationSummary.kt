package me.rgunny.kachi.notification.application.port.inbound.admin.model

import me.rgunny.kachi.notification.domain.Notification
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import java.time.Instant

/**
 * 운영 API에 노출할 notification 현재 상태 snapshot.
 */
data class NotificationSummary(
    val notificationId: NotificationId,
    val requestId: String,
    val requester: String,
    val channel: NotificationChannel,
    val recipientId: String,
    val status: NotificationStatus,
    val failureReason: String?,
    val dispatchAttempts: Int,
    val requestedAt: Instant,
    val updatedAt: Instant,
    val lastTransitionAt: Instant,
) {
    companion object {
        fun from(notification: Notification): NotificationSummary {
            return NotificationSummary(
                notificationId = notification.id,
                requestId = notification.requestId,
                requester = notification.requester,
                channel = notification.channel,
                recipientId = notification.recipientId,
                status = notification.status,
                failureReason = notification.failureReason,
                dispatchAttempts = notification.dispatchAttempts,
                requestedAt = notification.requestedAt,
                updatedAt = notification.updatedAt,
                lastTransitionAt = notification.lastTransitionAt,
            )
        }
    }
}
