package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.dto.outbox.NotificationOutboxAdminResult

/**
 * Outbox 운영 목록 응답.
 *
 * 운영자가 실패 원인과 재시도 상태를 판단하는 데 필요한 메타데이터만 노출한다.
 */
data class NotificationOutboxAdminListResponse(
    val outboxes: List<NotificationOutboxAdminResponse>,
) {
    companion object {
        fun from(result: NotificationOutboxAdminResult): NotificationOutboxAdminListResponse {
            return NotificationOutboxAdminListResponse(
                outboxes = result.outboxes.map(NotificationOutboxAdminResponse::from)
            )
        }
    }
}
