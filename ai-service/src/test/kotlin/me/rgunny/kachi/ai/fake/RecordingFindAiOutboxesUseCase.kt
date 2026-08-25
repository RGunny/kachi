package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.inbound.outbox.FindAiOutboxesUseCase
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.AiOutboxSummary
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.FindAiOutboxesQuery
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.FindAiOutboxesResult

/**
 * 전달받은 조회 조건을 그대로 보관하는 outbox 조회 유스케이스 fake.
 */
class RecordingFindAiOutboxesUseCase : FindAiOutboxesUseCase {
    var invokeCount = 0
    var lastQuery: FindAiOutboxesQuery? = null
    var outboxes: List<AiOutboxSummary> = emptyList()

    override suspend fun find(query: FindAiOutboxesQuery): FindAiOutboxesResult {
        invokeCount += 1
        lastQuery = query

        return FindAiOutboxesResult(outboxes)
    }
}
