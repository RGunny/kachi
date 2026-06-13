package me.rgunny.kachi.notification.exception.sender

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.exception.NotificationErrorCode
import me.rgunny.kachi.notification.exception.NotificationException

class MultipleNotificationSendersFoundException(
    val channel: NotificationChannel,
) : NotificationException(
    errorCode = NotificationErrorCode.MULTIPLE_SENDERS_FOUND,
    message = "multiple notification senders found. channel=$channel",
) {
    override val context: Map<String, String> = mapOf(
        "channel" to channel.name,
    )
}
