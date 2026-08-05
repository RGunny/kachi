package me.rgunny.kachi.notification.exception.sender

import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.NonRetryableException
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.exception.NotificationErrorCode

class NonRetryableSendException(
    val notificationId: NotificationId,
    val channel: NotificationChannel,
    failure: RetryFailure,
    cause: Throwable? = null,
) : NonRetryableException(
    errorCode = NotificationErrorCode.SEND_PERMANENT_FAILURE,
    failure = failure,
    cause = cause,
)
