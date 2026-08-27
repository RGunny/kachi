package me.rgunny.kachi.notification.exception.routing

import me.rgunny.kachi.notification.exception.BaseException

abstract class RoutingException(
    errorCode: RoutingErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : BaseException(
    errorCode = errorCode,
    message = message,
    cause = cause,
)
