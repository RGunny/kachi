package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.dto.NotificationDispatchMessage
import me.rgunny.kachi.notification.application.port.outbound.NotificationEventSerializer

class FakeEventSerializer : NotificationEventSerializer {
    val messages = mutableListOf<NotificationDispatchMessage>()

    override fun serializeDispatch(message: NotificationDispatchMessage): String {
        messages += message
        return "payload-${message.requestId}"
    }
}
