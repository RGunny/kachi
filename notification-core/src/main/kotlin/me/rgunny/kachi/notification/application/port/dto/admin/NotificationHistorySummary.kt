package me.rgunny.kachi.notification.application.port.dto.admin

import me.rgunny.kachi.notification.domain.NotificationHistory
import me.rgunny.kachi.notification.domain.NotificationHistoryId
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import java.time.Instant

/**
 * 운영 API에 노출할 notification 상태 전이 이력 snapshot.
 */
data class NotificationHistorySummary(
    val historyId: NotificationHistoryId,
    val notificationId: NotificationId,
    val fromStatus: NotificationStatus,
    val toStatus: NotificationStatus,
    val reason: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(history: NotificationHistory): NotificationHistorySummary {
            return NotificationHistorySummary(
                historyId = history.id,
                notificationId = history.notificationId,
                fromStatus = history.fromStatus,
                toStatus = history.toStatus,
                reason = history.reason,
                createdAt = history.createdAt,
            )
        }
    }
}
