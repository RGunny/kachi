package me.rgunny.kachi.notification.exception

import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus

class InvalidNotificationStateException(
    val notificationId: NotificationId?,
    val currentStatus: NotificationStatus?,
    message: String,
    cause: Throwable? = null,
) : NotificationException(
    errorCode = NotificationErrorCode.INVALID_NOTIFICATION_STATE,
    message = message,
    cause = cause,
)
