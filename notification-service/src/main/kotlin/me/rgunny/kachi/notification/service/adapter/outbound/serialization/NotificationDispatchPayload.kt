package me.rgunny.kachi.notification.service.adapter.outbound.serialization

import me.rgunny.kachi.notification.application.port.dto.NotificationDispatchMessage

data class NotificationDispatchPayload(
    val notificationId: String,
    val requestId: String,
    val channel: String,
    val recipient: String,
    val message: String
) {

    companion object {

        fun from(message: NotificationDispatchMessage): NotificationDispatchPayload {
            return NotificationDispatchPayload(
                notificationId = message.notificationId.id.toString(),
                requestId = message.requestId,
                channel = message.channel.name,
                recipient = message.recipient,
                message = message.message
            )
        }
    }
}
