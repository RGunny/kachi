package me.rgunny.kachi.notification.routing.exception.routing

import me.rgunny.kachi.notification.routing.exception.BaseException

abstract class RoutingException(
    errorCode: RoutingErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : BaseException(
    errorCode = errorCode,
    message = message,
    cause = cause,
)
