package me.rgunny.kachi.notification.application.port.dto.outbox

import me.rgunny.kachi.notification.domain.NotificationOutboxId

data class RecoverNotificationOutboxCommand(
    val outboxId: NotificationOutboxId,
)
