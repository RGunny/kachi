package me.rgunny.kachi.notification.exception

import me.rgunny.kachi.notification.exception.BaseException

abstract class NotificationException(
    errorCode: NotificationErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : BaseException(
    errorCode = errorCode,
    message = message,
    cause = cause,
)
