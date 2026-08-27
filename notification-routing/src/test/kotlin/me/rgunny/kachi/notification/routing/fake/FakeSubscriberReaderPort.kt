package me.rgunny.kachi.notification.routing.fake

import me.rgunny.kachi.notification.routing.application.port.outbound.subscriber.SubscriberReaderPort
import me.rgunny.kachi.notification.routing.application.port.outbound.subscriber.model.Subscriber

class FakeSubscriberReaderPort(
    private val subscribers: List<Subscriber> = emptyList(),
) : SubscriberReaderPort {
    val requestedKeywords = mutableListOf<String>()
    var failure: RuntimeException? = null

    override suspend fun findSubscribers(keyword: String): List<Subscriber> {
        requestedKeywords += keyword
        failure?.let { throw it }
        return subscribers
    }
}
