package me.rgunny.kachi.notification.service.adapter.inbound.messaging

import me.rgunny.kachi.notification.application.port.inbound.request.model.RequestNotificationCommand
import me.rgunny.kachi.notification.contract.NotificationRequestedEvent
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationOrigin

object NotificationRequestedEventMapper {

    fun toCommand(event: NotificationRequestedEvent): RequestNotificationCommand {
        require(event.schemaVersion == NotificationRequestedEvent.CURRENT_SCHEMA_VERSION) {
            "unsupported notification requested schemaVersion=${event.schemaVersion}"
        }

        return RequestNotificationCommand(
            requestId = event.requestId,
            requester = event.requester,
            channel = NotificationChannel.valueOf(event.channel.name),
            recipient = event.recipient,
            message = event.message,
            origin = NotificationOrigin.NONE,
        )
    }
}
