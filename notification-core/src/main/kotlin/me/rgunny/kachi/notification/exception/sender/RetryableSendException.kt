package me.rgunny.kachi.notification.exception.sender

import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryableException
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.exception.NotificationErrorCode

class RetryableSendException(
    val notificationId: NotificationId,
    val channel: NotificationChannel,
    failure: RetryFailure,
    cause: Throwable? = null,
) : RetryableException(
    errorCode = NotificationErrorCode.SEND_TRANSIENT_FAILURE,
    failure = failure,
    cause = cause,
)
