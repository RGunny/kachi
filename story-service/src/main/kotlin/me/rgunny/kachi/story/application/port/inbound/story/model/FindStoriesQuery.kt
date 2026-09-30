package me.rgunny.kachi.story.application.port.inbound.story.model

import java.time.Instant
import me.rgunny.kachi.story.domain.StoryStatus

/**
 * story 목록 조회 조건.
 *
 * null인 조건은 거르지 않는다.
 */
data class FindStoriesQuery(
    val status: StoryStatus?,
    val openedAfter: Instant?,
    val limit: Int
) {
    init {
        require(limit >= 1) { "limit은 1 이상이어야 합니다: $limit" }
    }

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}
