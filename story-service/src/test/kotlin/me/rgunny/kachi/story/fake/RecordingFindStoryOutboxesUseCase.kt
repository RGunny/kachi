package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.inbound.outbox.FindStoryOutboxesUseCase
import me.rgunny.kachi.story.application.port.inbound.outbox.model.FindStoryOutboxesQuery
import me.rgunny.kachi.story.application.port.inbound.outbox.model.FindStoryOutboxesResult

/**
 * 조회 조건을 기록하고 정해 둔 목록을 돌려주는 outbox 조회 유스케이스.
 */
class RecordingFindStoryOutboxesUseCase : FindStoryOutboxesUseCase {
    var result: FindStoryOutboxesResult = FindStoryOutboxesResult(emptyList())
    var lastQuery: FindStoryOutboxesQuery? = null
    var invokeCount: Int = 0

    override suspend fun find(query: FindStoryOutboxesQuery): FindStoryOutboxesResult {
        invokeCount += 1
        lastQuery = query

        return result
    }
}
