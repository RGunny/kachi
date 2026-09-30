package me.rgunny.kachi.story.domain

import java.util.UUID

/**
 * 기사 식별자.
 */
@JvmInline
value class NewsId private constructor(
    val value: UUID
) {
    companion object {
        fun of(value: UUID): NewsId = NewsId(value)
    }
}
