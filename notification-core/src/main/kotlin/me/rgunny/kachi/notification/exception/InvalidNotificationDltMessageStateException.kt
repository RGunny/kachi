package me.rgunny.kachi.notification.exception

import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus

class InvalidNotificationDltMessageStateException(
    val messageId: NotificationDltMessageId,
    val currentStatus: NotificationDltMessageStatus?,
    message: String,
    cause: Throwable? = null,
) : NotificationException(
    errorCode = NotificationErrorCode.INVALID_DLT_MESSAGE_STATE,
    message = message,
    cause = cause,
)
