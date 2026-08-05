package me.rgunny.kachi.notification.exception

import me.rgunny.kachi.notification.domain.NotificationId

class NotificationNotFoundException(
    val notificationId: NotificationId,
) : NotificationException(
    errorCode = NotificationErrorCode.NOTIFICATION_NOT_FOUND,
    message = "notification not found. notificationId=${notificationId.id}",
)
