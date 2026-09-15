package me.rgunny.kachi.story.application.port.inbound.outbox.model

import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus

/**
 * outbox 조회 조건.
 */
data class FindStoryOutboxesQuery(
    val status: StoryOutboxStatus = DEFAULT_STATUS,
    val limit: Int = DEFAULT_LIMIT
) {
    init {
        require(limit in 1..MAX_LIMIT) { "outbox 조회 limit은 1 이상 $MAX_LIMIT 이하여야 합니다: $limit" }
    }

    companion object {
        val DEFAULT_STATUS: StoryOutboxStatus = StoryOutboxStatus.DEAD
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 500
    }
}
