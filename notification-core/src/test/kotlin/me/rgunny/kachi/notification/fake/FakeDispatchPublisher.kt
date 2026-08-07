package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.messaging.NotificationDispatchPublisher
import me.rgunny.kachi.notification.domain.NotificationOutbox

class FakeDispatchPublisher(
    private val failure: RuntimeException? = null,
) : NotificationDispatchPublisher {
    var publishCount = 0

    override suspend fun publish(outbox: NotificationOutbox) {
        publishCount += 1
        failure?.let { throw it }
    }
}
