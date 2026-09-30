package me.rgunny.kachi.story.application.port.inbound.outbox.model

import me.rgunny.kachi.story.domain.outbox.StoryOutboxId

/**
 * outbox 복구 명령.
 */
data class RecoverStoryOutboxCommand(
    val outboxId: StoryOutboxId
)
