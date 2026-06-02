package me.rgunny.kachi.ai.domain

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

@JvmInline
value class NewsSummaryId private constructor(
    val value: UUID
) {
    companion object {

        fun newId(): NewsSummaryId {
            return NewsSummaryId(UuidCreator.getTimeOrderedEpoch())
        }

        fun of(value: UUID): NewsSummaryId {
            return NewsSummaryId(value)
        }
    }
}
