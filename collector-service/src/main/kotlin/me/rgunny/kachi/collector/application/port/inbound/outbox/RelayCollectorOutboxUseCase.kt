package me.rgunny.kachi.collector.application.port.inbound.outbox

import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RelayCollectorOutboxResult

/**
 * 기록된 outbox 이벤트를 발행 상태로 진행시키는 입력 포트.
 */
interface RelayCollectorOutboxUseCase {

    suspend fun relay(): RelayCollectorOutboxResult
}
