package me.rgunny.kachi.story.contract

import java.time.Instant

/**
 * story-service가 story 하나를 다른 story에 흡수했음을 알릴 때 사용하는 이벤트 계약.
 *
 * [storyId]가 흡수한 쪽, [mergedStoryId]가 흡수되어 닫힌 쪽이다. 레코드 키는 흡수한 storyId다.
 */
data class StoryMergedEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val storyId: String,
    val mergedStoryId: String,
    val mergedAt: Instant
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
