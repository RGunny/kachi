package me.rgunny.kachi.notification.exception.sender

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.exception.NotificationErrorCode
import me.rgunny.kachi.notification.exception.NotificationException

class NotificationSenderNotFoundException(
    val channel: NotificationChannel,
) : NotificationException(
    errorCode = NotificationErrorCode.SENDER_NOT_FOUND,
    message = "notification sender not found. channel=$channel",
) {
    override val context: Map<String, String> = mapOf(
        "channel" to channel.name,
    )
}
