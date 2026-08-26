package me.rgunny.kachi.ai.application.port.outbound.outbox

import me.rgunny.kachi.ai.application.port.outbound.outbox.model.AiOutboxEvent

/**
 * 도메인 이벤트를 outbox payload 문자열로 바꾼다.
 */
interface AiOutboxEventSerializer {

    fun serialize(event: AiOutboxEvent): String
}
