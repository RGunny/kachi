package me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response

import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationHistoryResult

/**
 * Notification 상태 전이 history 목록 응답.
 */
data class NotificationHistoryListResponse(
    val histories: List<NotificationHistoryResponse>,
) {
    companion object {
        fun from(result: NotificationHistoryResult): NotificationHistoryListResponse {
            return NotificationHistoryListResponse(
                histories = result.histories.map(NotificationHistoryResponse::from)
            )
        }
    }
}
