package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import me.rgunny.kachi.notification.application.port.dto.DispatchNotificationCommand
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import java.util.UUID

object NotificationDispatchMessageMapper {

    fun toCommand(payload: NotificationDispatchPayload): DispatchNotificationCommand {
        return DispatchNotificationCommand(
            notificationId = NotificationId.of(UUID.fromString(payload.notificationId)),
            requestId = payload.requestId,
            channel = NotificationChannel.valueOf(payload.channel),
            recipient = payload.recipient,
            message = payload.message,
        )
    }
}
