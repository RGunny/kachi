package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.inbound.outbox.model.NotificationOutboxSummary
import java.time.Instant
import java.util.UUID

data class NotificationOutboxAdminResponse(
    val outboxId: UUID,
    val notificationId: UUID,
    val topic: String,
    val partitionKey: String,
    val status: String,
    val retryCount: Int,
    val nextRetryAt: Instant,
    val lastError: String?,
    val createdAt: Instant,
    val publishedAt: Instant?,
) {
    companion object {
        fun from(summary: NotificationOutboxSummary): NotificationOutboxAdminResponse {
            return NotificationOutboxAdminResponse(
                outboxId = summary.outboxId.id,
                notificationId = summary.notificationId.id,
                topic = summary.topic,
                partitionKey = summary.partitionKey,
                status = summary.status.name,
                retryCount = summary.retryCount,
                nextRetryAt = summary.nextRetryAt,
                lastError = summary.lastError,
                createdAt = summary.createdAt,
                publishedAt = summary.publishedAt,
            )
        }
    }
}
