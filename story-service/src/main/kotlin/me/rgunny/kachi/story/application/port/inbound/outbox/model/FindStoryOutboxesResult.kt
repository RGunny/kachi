package me.rgunny.kachi.story.application.port.inbound.outbox.model

/**
 * outbox 조회 결과 목록.
 */
data class FindStoryOutboxesResult(
    val outboxes: List<StoryOutboxSummary>
)
