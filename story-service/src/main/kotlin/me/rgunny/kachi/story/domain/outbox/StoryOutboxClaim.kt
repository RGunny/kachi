package me.rgunny.kachi.story.domain.outbox

import java.time.Instant

/**
 * PUBLISHING 상태의 소유권.
 */
data class StoryOutboxClaim(
    val claimedBy: String,
    val claimedAt: Instant
) {
    init {
        require(claimedBy.isNotBlank()) { "outbox claimedBy는 비어 있을 수 없습니다" }
    }
}
