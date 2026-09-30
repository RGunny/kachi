package me.rgunny.kachi.ai.application.port.inbound.story.model

import me.rgunny.kachi.ai.domain.story.StoryId
import java.time.Instant

/**
 * story 병합 사실을 사본 상태에 반영하라는 명령.
 *
 * [storyId]가 흡수한 쪽, [mergedStoryId]가 흡수되어 닫힌 쪽이다.
 */
data class ApplyStoryMergeCommand(
    val storyId: StoryId,
    val mergedStoryId: StoryId,
    val mergedAt: Instant
) {
    init {
        require(storyId != mergedStoryId) { "story는 자신에게 흡수될 수 없습니다: ${storyId.value}" }
    }
}
