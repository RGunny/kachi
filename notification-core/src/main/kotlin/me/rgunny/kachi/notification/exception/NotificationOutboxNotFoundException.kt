package me.rgunny.kachi.notification.exception

import me.rgunny.kachi.notification.domain.NotificationOutboxId

class NotificationOutboxNotFoundException(
    val outboxId: NotificationOutboxId,
) : NotificationException(
    errorCode = NotificationErrorCode.OUTBOX_NOT_FOUND,
    message = "notification outbox not found. outboxId=${outboxId.id}",
)
