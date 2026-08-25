package me.rgunny.kachi.ai.application.port.inbound.outbox.model

data class FindAiOutboxesResult(
    val outboxes: List<AiOutboxSummary>
)
