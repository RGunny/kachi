package me.rgunny.kachi.story.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * story 식별자.
 */
@JvmInline
value class StoryId private constructor(
    val value: UUID
) {
    companion object {
        fun newId(): StoryId = StoryId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): StoryId = StoryId(value)
    }
}
