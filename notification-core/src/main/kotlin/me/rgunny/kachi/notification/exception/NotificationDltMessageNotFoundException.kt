package me.rgunny.kachi.notification.exception

import me.rgunny.kachi.notification.domain.NotificationDltMessageId

class NotificationDltMessageNotFoundException(
    val messageId: NotificationDltMessageId,
) : NotificationException(
    errorCode = NotificationErrorCode.DLT_MESSAGE_NOT_FOUND,
    message = "notification dlt message not found. messageId=${messageId.id}",
)
