package me.rgunny.kachi.notification.retry

import me.rgunny.kachi.notification.exception.ErrorCode

open class NonRetryableException(
    errorCode: ErrorCode,
    failure: RetryFailure,
    cause: Throwable? = null,
) : RetryException(
    errorCode = errorCode,
    failure = failure,
    cause = cause,
)
