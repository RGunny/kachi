package me.rgunny.kachi.collector.adapter.inbound.web

import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RecoverCollectorOutboxResult
import java.time.Instant

data class RecoverCollectorOutboxResponse(
    val outbox: CollectorOutboxResponse,
    val recoveredAt: Instant
) {
    companion object {

        fun from(result: RecoverCollectorOutboxResult): RecoverCollectorOutboxResponse {
            return RecoverCollectorOutboxResponse(
                outbox = CollectorOutboxResponse.from(result.outbox),
                recoveredAt = result.recoveredAt
            )
        }
    }
}
