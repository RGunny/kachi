package me.rgunny.kachi.notification.application.port.inbound.dlt.model

/**
 * DLT 메시지 운영 목록 조회 결과.
 */
data class NotificationDltMessageAdminResult(
    val messages: List<NotificationDltMessageSummary>,
)
