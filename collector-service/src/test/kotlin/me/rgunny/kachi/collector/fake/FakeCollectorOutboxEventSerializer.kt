package me.rgunny.kachi.collector.fake

import me.rgunny.kachi.collector.application.port.outbound.outbox.CollectorOutboxEventSerializer
import me.rgunny.kachi.collector.application.port.outbound.outbox.model.CollectorOutboxEvent

/**
 * 직렬화 형식을 흉내 내지 않고 호출만 기록하는 serializer.
 *
 * payload 형식은 어댑터 테스트가 검증한다. 여기서는 어떤 이벤트가 몇 번 직렬화됐는지만 본다.
 */
class FakeCollectorOutboxEventSerializer : CollectorOutboxEventSerializer {
    val serialized: MutableList<CollectorOutboxEvent> = mutableListOf()
    var failure: RuntimeException? = null

    override fun serialize(event: CollectorOutboxEvent): String {
        failure?.let { throw it }
        serialized.add(event)

        return payloadOf(event)
    }

    companion object {
        fun payloadOf(event: CollectorOutboxEvent): String {
            return "payload:${event.type}:${event.eventKey}"
        }
    }
}
