package me.rgunny.kachi.ai.application.port.inbound.outbox.model

import me.rgunny.kachi.ai.domain.outbox.AiOutboxId

data class RecoverAiOutboxCommand(
    val outboxId: AiOutboxId
)
