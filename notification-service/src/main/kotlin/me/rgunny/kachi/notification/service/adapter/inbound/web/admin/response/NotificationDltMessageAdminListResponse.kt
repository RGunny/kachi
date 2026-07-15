package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageAdminResult

/**
 * DLT 메시지 운영 목록 응답.
 */
data class NotificationDltMessageAdminListResponse(
    val messages: List<NotificationDltMessageAdminResponse>,
) {
    companion object {
        fun from(result: NotificationDltMessageAdminResult): NotificationDltMessageAdminListResponse {
            return NotificationDltMessageAdminListResponse(
                messages = result.messages.map(NotificationDltMessageAdminResponse::from)
            )
        }
    }
}
