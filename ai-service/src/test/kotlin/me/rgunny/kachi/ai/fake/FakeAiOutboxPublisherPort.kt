package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.outbox.AiOutboxPublisherPort
import me.rgunny.kachi.ai.domain.outbox.AiOutbox

/**
 * 발행 시도를 기록하고 지정한 예외를 던지는 fake.
 *
 * [published]에는 실패한 시도도 남는다. 발행 호출 자체가 있었는지가 관찰 대상이기 때문이다.
 */
class FakeAiOutboxPublisherPort : AiOutboxPublisherPort {
    val published: MutableList<AiOutbox> = mutableListOf()
    var failure: Throwable? = null

    override suspend fun publish(outbox: AiOutbox) {
        published.add(outbox)

        failure?.let { throw it }
    }
}
