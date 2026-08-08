package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationAdminResult

/**
 * Notification 운영 목록 응답.
 */
data class NotificationAdminListResponse(
    val notifications: List<NotificationAdminResponse>,
) {
    companion object {
        fun from(result: NotificationAdminResult): NotificationAdminListResponse {
            return NotificationAdminListResponse(
                notifications = result.notifications.map(NotificationAdminResponse::from)
            )
        }
    }
}
