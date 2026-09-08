package me.rgunny.kachi.collector.fake

import me.rgunny.kachi.collector.application.port.inbound.outbox.FindCollectorOutboxesUseCase
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.CollectorOutboxSummary
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.FindCollectorOutboxesQuery
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.FindCollectorOutboxesResult

/**
 * 전달받은 조회 조건을 그대로 보관하는 outbox 조회 유스케이스 fake.
 */
class RecordingFindCollectorOutboxesUseCase : FindCollectorOutboxesUseCase {
    var invokeCount = 0
    var lastQuery: FindCollectorOutboxesQuery? = null
    var outboxes: List<CollectorOutboxSummary> = emptyList()

    override suspend fun find(query: FindCollectorOutboxesQuery): FindCollectorOutboxesResult {
        invokeCount += 1
        lastQuery = query

        return FindCollectorOutboxesResult(outboxes)
    }
}
