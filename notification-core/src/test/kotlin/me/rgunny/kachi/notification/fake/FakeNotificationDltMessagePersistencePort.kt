package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.dlt.NotificationDltMessagePersistencePort
import me.rgunny.kachi.notification.domain.NotificationDltMessage

class FakeNotificationDltMessagePersistencePort : NotificationDltMessagePersistencePort {
    val saved = mutableListOf<NotificationDltMessage>()

    override suspend fun save(message: NotificationDltMessage): NotificationDltMessage {
        saved += message
        return message
    }
}
