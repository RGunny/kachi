package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationSummary
import java.time.Instant
import java.util.UUID

/**
 * 운영자가 실패 상태와 복구 대상을 판단할 notification 현재 상태 응답.
 */
data class NotificationAdminResponse(
    val notificationId: UUID,
    val requestId: String,
    val requester: String,
    val channel: String,
    val recipientId: String,
    val status: String,
    val failureReason: String?,
    val dispatchAttempts: Int,
    val requestedAt: Instant,
    val updatedAt: Instant,
    val lastTransitionAt: Instant,
) {
    companion object {
        fun from(summary: NotificationSummary): NotificationAdminResponse {
            return NotificationAdminResponse(
                notificationId = summary.notificationId.id,
                requestId = summary.requestId,
                requester = summary.requester,
                channel = summary.channel.name,
                recipientId = summary.recipientId,
                status = summary.status.name,
                failureReason = summary.failureReason,
                dispatchAttempts = summary.dispatchAttempts,
                requestedAt = summary.requestedAt,
                updatedAt = summary.updatedAt,
                lastTransitionAt = summary.lastTransitionAt,
            )
        }
    }
}
