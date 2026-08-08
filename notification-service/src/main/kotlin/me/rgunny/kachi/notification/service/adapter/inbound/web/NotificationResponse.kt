package me.rgunny.kachi.notification.service.adapter.inbound.web

import me.rgunny.kachi.notification.application.port.inbound.request.model.RequestNotificationResult
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val notificationId: UUID,
    val status: String,
    val duplicated: Boolean,
    val acceptedAt: Instant,
) {
    companion object {
        fun from(result: RequestNotificationResult): NotificationResponse {
            return NotificationResponse(
                notificationId = result.notificationId.id,
                status = result.status.name,
                duplicated = result.duplicated,
                acceptedAt = result.acceptedAt,
            )
        }
    }
}
