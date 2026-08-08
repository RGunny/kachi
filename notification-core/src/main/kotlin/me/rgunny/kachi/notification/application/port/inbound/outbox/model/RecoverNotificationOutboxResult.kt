package me.rgunny.kachi.notification.application.port.inbound.outbox.model

import java.time.Instant

data class RecoverNotificationOutboxResult(
    val outbox: NotificationOutboxSummary,
    val recoveredAt: Instant,
)
