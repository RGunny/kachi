package me.rgunny.kachi.ai.domain.summary

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * story 요약 버전 하나의 식별자.
 */
@JvmInline
value class StorySummaryId private constructor(
    val value: UUID
) {
    companion object {

        fun newId(): StorySummaryId {
            return StorySummaryId(UuidCreator.getTimeOrderedEpoch())
        }

        fun of(value: UUID): StorySummaryId {
            return StorySummaryId(value)
        }
    }
}
