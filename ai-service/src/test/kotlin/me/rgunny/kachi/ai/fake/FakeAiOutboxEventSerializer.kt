package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.outbox.AiOutboxEventSerializer
import me.rgunny.kachi.ai.application.port.outbound.outbox.model.AiOutboxEvent

/**
 * 직렬화 형식을 흉내 내지 않고 호출만 기록하는 serializer.
 *
 * payload 형식은 어댑터 테스트가 검증한다. 여기서는 어떤 이벤트가 몇 번 직렬화됐는지만 본다.
 */
class FakeAiOutboxEventSerializer : AiOutboxEventSerializer {
    val serialized: MutableList<AiOutboxEvent> = mutableListOf()
    var failure: RuntimeException? = null

    override fun serialize(event: AiOutboxEvent): String {
        failure?.let { throw it }
        serialized.add(event)

        return payloadOf(event)
    }

    companion object {
        fun payloadOf(event: AiOutboxEvent): String {
            return "payload:${event.type}:${event.eventKey}"
        }
    }
}
