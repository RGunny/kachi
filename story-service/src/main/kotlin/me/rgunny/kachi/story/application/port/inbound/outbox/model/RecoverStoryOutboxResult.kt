package me.rgunny.kachi.story.application.port.inbound.outbox.model

import java.time.Instant

/**
 * outbox 복구 결과.
 */
data class RecoverStoryOutboxResult(
    val outbox: StoryOutboxSummary,
    val recoveredAt: Instant
)
