package me.rgunny.kachi.ai.application.port.inbound.outbox.model

import java.time.Instant

data class RecoverAiOutboxResult(
    val outbox: AiOutboxSummary,
    val recoveredAt: Instant
)
