package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.dto.admin.NotificationHistorySummary
import java.time.Instant
import java.util.UUID

/**
 * Notification 상태 전이 history 응답.
 */
data class NotificationHistoryResponse(
    val historyId: UUID,
    val notificationId: UUID,
    val fromStatus: String,
    val toStatus: String,
    val reason: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(summary: NotificationHistorySummary): NotificationHistoryResponse {
            return NotificationHistoryResponse(
                historyId = summary.historyId.id,
                notificationId = summary.notificationId.id,
                fromStatus = summary.fromStatus.name,
                toStatus = summary.toStatus.name,
                reason = summary.reason,
                createdAt = summary.createdAt,
            )
        }
    }
}
