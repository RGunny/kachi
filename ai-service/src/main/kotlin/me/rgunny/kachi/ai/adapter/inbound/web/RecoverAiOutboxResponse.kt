package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RecoverAiOutboxResult
import java.time.Instant

data class RecoverAiOutboxResponse(
    val outbox: AiOutboxResponse,
    val recoveredAt: Instant
) {
    companion object {

        fun from(result: RecoverAiOutboxResult): RecoverAiOutboxResponse {
            return RecoverAiOutboxResponse(
                outbox = AiOutboxResponse.from(result.outbox),
                recoveredAt = result.recoveredAt
            )
        }
    }
}
