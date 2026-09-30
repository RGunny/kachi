package me.rgunny.kachi.notification.exception.dispatch

import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.exception.NotificationErrorCode
import me.rgunny.kachi.notification.domain.retry.RetryFailure
import me.rgunny.kachi.notification.domain.retry.RetryFailureCode
import me.rgunny.kachi.notification.exception.RetryableException

class DispatchNotReadyException(
    val notificationId: NotificationId,
    val status: NotificationStatus,
) : RetryableException(
    errorCode = NotificationErrorCode.DISPATCH_NOT_READY,
    failure = RetryFailure.of(
        code = RetryFailureCode.DISPATCH_NOT_READY,
        message = "notification is not ready to dispatch. notificationId=${notificationId.id}, status=$status",
    ),
)
