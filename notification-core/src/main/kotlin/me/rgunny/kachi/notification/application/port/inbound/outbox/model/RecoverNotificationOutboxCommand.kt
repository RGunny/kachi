package me.rgunny.kachi.notification.application.port.inbound.outbox.model

import me.rgunny.kachi.notification.domain.NotificationOutboxId

data class RecoverNotificationOutboxCommand(
    val outboxId: NotificationOutboxId,
)
