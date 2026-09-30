package me.rgunny.kachi.ai.application.port.inbound.story.model

import me.rgunny.kachi.ai.domain.story.StoryId

/**
 * story 요약 버전 조회 조건.
 */
data class FindStorySummariesQuery(
    val storyId: StoryId,
    val limit: Int
) {
    init {
        require(limit in 1..MAX_LIMIT) { "조회 개수는 1 이상 $MAX_LIMIT 이하여야 합니다: $limit" }
    }

    companion object {
        const val MAX_LIMIT = 100
        const val DEFAULT_LIMIT = 20
    }
}
