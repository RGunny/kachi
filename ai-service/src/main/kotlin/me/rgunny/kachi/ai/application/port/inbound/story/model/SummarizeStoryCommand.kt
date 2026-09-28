package me.rgunny.kachi.ai.application.port.inbound.story.model

import me.rgunny.kachi.ai.domain.story.StoryId

/**
 * story 하나의 미요약 기사를 요약하라는 명령.
 */
data class SummarizeStoryCommand(
    val storyId: StoryId
)
