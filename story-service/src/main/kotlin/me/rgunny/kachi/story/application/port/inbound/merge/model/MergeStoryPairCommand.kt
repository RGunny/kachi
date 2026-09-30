package me.rgunny.kachi.story.application.port.inbound.merge.model

import me.rgunny.kachi.story.domain.StoryId

/**
 * 운영자 병합 명령.
 *
 * [targetStoryId]가 [sourceStoryId]를 흡수한다.
 */
data class MergeStoryPairCommand(
    val targetStoryId: StoryId,
    val sourceStoryId: StoryId
)
