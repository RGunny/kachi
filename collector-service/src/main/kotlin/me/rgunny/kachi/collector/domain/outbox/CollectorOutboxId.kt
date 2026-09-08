package me.rgunny.kachi.collector.domain.outbox

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class CollectorOutboxId private constructor(
    val value: UUID
) {
    companion object {

        fun newId(): CollectorOutboxId {
            return CollectorOutboxId(UuidCreator.getTimeOrderedEpoch())
        }

        fun of(value: UUID): CollectorOutboxId {
            return CollectorOutboxId(value)
        }
    }
}
