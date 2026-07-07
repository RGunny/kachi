package me.rgunny.kachi.notification.application.port.dto.outbox

import java.time.Instant

data class RecoverNotificationOutboxResult(
    val outbox: NotificationOutboxSummary,
    val recoveredAt: Instant,
)
