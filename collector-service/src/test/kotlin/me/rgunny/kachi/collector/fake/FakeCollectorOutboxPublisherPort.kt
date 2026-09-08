package me.rgunny.kachi.collector.fake

import me.rgunny.kachi.collector.application.port.outbound.outbox.CollectorOutboxPublisherPort
import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox

/**
 * 발행 시도를 기록하고 지정한 예외를 던지는 fake.
 *
 * [published]에는 실패한 시도도 남는다. 발행 호출 자체가 있었는지가 관찰 대상이기 때문이다.
 */
class FakeCollectorOutboxPublisherPort : CollectorOutboxPublisherPort {
    val published: MutableList<CollectorOutbox> = mutableListOf()
    var failure: Throwable? = null

    override suspend fun publish(outbox: CollectorOutbox) {
        published.add(outbox)

        failure?.let { throw it }
    }
}
