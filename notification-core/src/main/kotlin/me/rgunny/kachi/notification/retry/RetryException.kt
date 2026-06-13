package me.rgunny.kachi.notification.retry

import me.rgunny.kachi.notification.exception.BaseException
import me.rgunny.kachi.notification.exception.ErrorCode

abstract class RetryException(
    errorCode: ErrorCode,
    val failure: RetryFailure,
    cause: Throwable? = null,
) : BaseException(
    errorCode = errorCode,
    message = failure.message,
    cause = cause,
) {
    override val context: Map<String, String> = failure.context()
}
