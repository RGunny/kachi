package me.rgunny.kachi.notification.application.port.inbound.admin.model

/**
 * Notification 운영 목록 조회 결과.
 */
data class NotificationAdminResult(
    val notifications: List<NotificationSummary>,
)
