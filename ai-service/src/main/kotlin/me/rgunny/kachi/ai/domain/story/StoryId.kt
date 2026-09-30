package me.rgunny.kachi.ai.domain.story

import java.util.UUID

/**
 * story-service가 부여한 story 식별자.
 */
@JvmInline
value class StoryId private constructor(
    val value: UUID
) {
    companion object {

        fun of(value: UUID): StoryId {
            return StoryId(value)
        }
    }
}
