package me.rgunny.kachi.notification.application.port.inbound.outbox.model

data class NotificationOutboxAdminResult(
    val outboxes: List<NotificationOutboxSummary>,
)
