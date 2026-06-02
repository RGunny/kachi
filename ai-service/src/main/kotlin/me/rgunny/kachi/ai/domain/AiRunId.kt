package me.rgunny.kachi.ai.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class AiRunId private constructor(
    val value: UUID
) {
    companion object {

        fun newId(): AiRunId {
            return AiRunId(UuidCreator.getTimeOrderedEpoch())
        }

        fun of(value: UUID): AiRunId {
            return AiRunId(value)
        }
    }
}
