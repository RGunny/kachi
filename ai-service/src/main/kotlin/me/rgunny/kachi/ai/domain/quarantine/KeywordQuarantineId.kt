package me.rgunny.kachi.ai.domain.quarantine

import com.github.f4b6a3.uuid.UuidCreator
import java.util.UUID

/**
 * 키워드 격리 기록 식별자.
 *
 * 시간 정렬 UUID(v7)라 id 순서가 곧 기록 생성 순서다(ADR 003).
 */
@JvmInline
value class KeywordQuarantineId private constructor(
    val value: UUID
) {
    companion object {

        fun newId(): KeywordQuarantineId {
            return KeywordQuarantineId(UuidCreator.getTimeOrderedEpoch())
        }

        fun of(value: UUID): KeywordQuarantineId {
            return KeywordQuarantineId(value)
        }
    }
}
