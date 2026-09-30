package me.rgunny.kachi.story.fake

import me.rgunny.kachi.story.application.port.inbound.story.FindStoriesUseCase
import me.rgunny.kachi.story.application.port.inbound.story.model.FindStoriesQuery
import me.rgunny.kachi.story.application.port.inbound.story.model.FindStoriesResult

/**
 * 조회 조건을 기록하고 정해 둔 목록을 돌려주는 story 목록 유스케이스.
 */
class RecordingFindStoriesUseCase : FindStoriesUseCase {
    var result: FindStoriesResult = FindStoriesResult(emptyList())
    var lastQuery: FindStoriesQuery? = null
    var invokeCount: Int = 0

    override suspend fun find(query: FindStoriesQuery): FindStoriesResult {
        invokeCount += 1
        lastQuery = query

        return result
    }
}
