package me.rgunny.kachi.story.application.port.inbound.merge.model

import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryId

/**
 * 운영자 병합의 결과.
 */
data class MergeStoryPairResult(
    val survivor: Story,
    val mergedStoryId: StoryId
)
