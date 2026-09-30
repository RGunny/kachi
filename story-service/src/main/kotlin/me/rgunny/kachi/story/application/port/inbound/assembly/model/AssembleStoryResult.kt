package me.rgunny.kachi.story.application.port.inbound.assembly.model

import me.rgunny.kachi.story.domain.LinkDecision
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryId

/**
 * 기사가 어느 story에 어떤 판정으로 붙었는지.
 */
data class AssembleStoryResult(
    val newsId: NewsId,
    val storyId: StoryId,
    val decision: LinkDecision,
    val replayed: Boolean
)
