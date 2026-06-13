package me.rgunny.kachi.notification.exception.dispatch

import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.exception.NotificationErrorCode
import me.rgunny.kachi.notification.exception.NotificationException

class DispatchNotReadyException(
    val notificationId: NotificationId,
    val status: NotificationStatus,
) : NotificationException(
    errorCode = NotificationErrorCode.DISPATCH_NOT_READY,
    message = "notification is not ready to dispatch. notificationId=${notificationId.id}, status=$status",
) {
    override val context: Map<String, String> = mapOf(
        "notificationId" to notificationId.id.toString(),
        "status" to status.name,
    )
}