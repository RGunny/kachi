package me.rgunny.kachi.ai.domain.outbox

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class AiOutboxId private constructor(
    val value: UUID
) {
    companion object {

        fun newId(): AiOutboxId {
            return AiOutboxId(UuidCreator.getTimeOrderedEpoch())
        }

        fun of(value: UUID): AiOutboxId {
            return AiOutboxId(value)
        }
    }
}
