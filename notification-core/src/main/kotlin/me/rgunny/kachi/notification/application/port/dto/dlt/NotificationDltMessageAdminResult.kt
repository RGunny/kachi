package me.rgunny.kachi.notification.application.port.dto.dlt

/**
 * DLT 메시지 운영 목록 조회 결과.
 */
data class NotificationDltMessageAdminResult(
    val messages: List<NotificationDltMessageSummary>,
)
