package me.rgunny.kachi.collector.application.port.inbound.outbox.model

import java.time.Instant

data class RecoverCollectorOutboxResult(
    val outbox: CollectorOutboxSummary,
    val recoveredAt: Instant
)
