package me.rgunny.kachi.notification.routing.exception

abstract class BaseException(
    val errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
