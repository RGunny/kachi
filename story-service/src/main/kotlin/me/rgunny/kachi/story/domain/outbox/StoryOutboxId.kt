package me.rgunny.kachi.story.domain.outbox

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class StoryOutboxId private constructor(
    val value: UUID
) {
    companion object {

        fun newId(): StoryOutboxId {
            return StoryOutboxId(UuidCreator.getTimeOrderedEpoch())
        }

        fun of(value: UUID): StoryOutboxId {
            return StoryOutboxId(value)
        }
    }
}
