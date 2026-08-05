package me.rgunny.kachi.notification.application.port.dto.outbox

data class NotificationOutboxAdminResult(
    val outboxes: List<NotificationOutboxSummary>,
)
