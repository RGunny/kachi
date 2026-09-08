package me.rgunny.kachi.collector.application.port.inbound.outbox.model

data class FindCollectorOutboxesResult(
    val outboxes: List<CollectorOutboxSummary>
)
