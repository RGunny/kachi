package me.rgunny.kachi.collector.application.port.outbound.outbox

import me.rgunny.kachi.collector.application.port.outbound.outbox.model.CollectorOutboxEvent

/**
 * 도메인 이벤트를 outbox payload 문자열로 바꾸는 출력 포트.
 */
interface CollectorOutboxEventSerializer {

    fun serialize(event: CollectorOutboxEvent): String
}
