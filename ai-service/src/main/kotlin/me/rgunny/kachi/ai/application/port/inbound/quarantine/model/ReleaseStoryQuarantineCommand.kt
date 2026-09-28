package me.rgunny.kachi.ai.application.port.inbound.quarantine.model

import me.rgunny.kachi.ai.domain.story.StoryId

/**
 * story 격리를 해제하라는 명령.
 */
data class ReleaseStoryQuarantineCommand(
    val storyId: StoryId
)
