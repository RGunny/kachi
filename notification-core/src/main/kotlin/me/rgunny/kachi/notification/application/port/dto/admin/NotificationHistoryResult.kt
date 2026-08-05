package me.rgunny.kachi.notification.application.port.dto.admin

/**
 * Notification 상태 전이 history 조회 결과.
 */
data class NotificationHistoryResult(
    val histories: List<NotificationHistorySummary>,
)
