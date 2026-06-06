package me.rgunny.kachi.ai.domain.keyword

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class KeywordExpansionId private constructor(
    val value: UUID
) {
    companion object {

        fun newId(): KeywordExpansionId {
            return KeywordExpansionId(UuidCreator.getTimeOrderedEpoch())
        }

        fun of(value: UUID): KeywordExpansionId {
            return KeywordExpansionId(value)
        }
    }
}
