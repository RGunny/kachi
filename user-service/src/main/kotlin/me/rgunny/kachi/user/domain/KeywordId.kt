package me.rgunny.kachi.user.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class KeywordId private constructor(
    val value: UUID
) {
    companion object {
        fun newId(): KeywordId = KeywordId(UuidCreator.getTimeOrderedEpoch())

        fun of(value: UUID): KeywordId = KeywordId(value)
    }
}
