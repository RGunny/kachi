package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.outbound.routing.SubscriberReaderPort
import me.rgunny.kachi.notification.application.port.outbound.routing.model.Subscriber

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
