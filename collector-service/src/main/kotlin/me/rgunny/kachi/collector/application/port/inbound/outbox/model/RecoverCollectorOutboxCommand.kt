package me.rgunny.kachi.collector.application.port.inbound.outbox.model

import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxId

data class RecoverCollectorOutboxCommand(
    val outboxId: CollectorOutboxId
)
