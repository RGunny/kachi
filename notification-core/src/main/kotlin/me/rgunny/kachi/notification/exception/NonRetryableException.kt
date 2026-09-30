package me.rgunny.kachi.notification.exception

import me.rgunny.kachi.notification.domain.retry.RetryFailure

open class NonRetryableException(
    errorCode: ErrorCode,
    failure: RetryFailure,
    cause: Throwable? = null,
) : RetryException(
    errorCode = errorCode,
    failure = failure,
    cause = cause,
)
