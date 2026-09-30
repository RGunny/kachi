package me.rgunny.kachi.collector.application.port.inbound.outbox

import me.rgunny.kachi.collector.application.port.inbound.outbox.model.FindCollectorOutboxesQuery
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.FindCollectorOutboxesResult

/**
 * 발행 상태별 outbox 행을 조회하는 유스케이스.
 */
interface FindCollectorOutboxesUseCase {

    suspend fun find(query: FindCollectorOutboxesQuery): FindCollectorOutboxesResult
}
