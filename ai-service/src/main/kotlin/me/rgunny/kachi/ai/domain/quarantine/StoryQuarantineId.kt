package me.rgunny.kachi.ai.domain.quarantine

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * story 격리 기록의 식별자.
 */
@JvmInline
value class StoryQuarantineId private constructor(
    val value: UUID
) {
    companion object {

        fun newId(): StoryQuarantineId {
            return StoryQuarantineId(UuidCreator.getTimeOrderedEpoch())
        }

        fun of(value: UUID): StoryQuarantineId {
            return StoryQuarantineId(value)
        }
    }
}
