package me.rgunny.kachi.notification.exception

import me.rgunny.kachi.notification.domain.retry.RetryFailure

abstract class RetryException(
    errorCode: ErrorCode,
    val failure: RetryFailure,
    cause: Throwable? = null,
) : BaseException(
    errorCode = errorCode,
    message = failure.message,
    cause = cause,
)
