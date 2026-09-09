package me.rgunny.kachi.ai.adapter.outbound.persistence.quarantine

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineId
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 키워드 격리 기록의 MongoDB Document.
 *
 * targetType + keyword unique index가 "키워드당 기록 한 건" 계약을 보장한다.
 * 도메인이 갱신 시에도 id를 유지하므로 실패 누적은 새 문서가 아니라 같은 문서의 갱신이 된다.
 */
@Document(collection = "ai_keyword_quarantines")
@CompoundIndexes(
    CompoundIndex(
        name = "ux_ai_keyword_quarantines_target_type_keyword",
        def = "{'targetType': 1, 'keyword': 1}",
        unique = true
    )
)
data class KeywordQuarantineMongoDocument(
    @Id
    val id: UUID,
    val targetType: AiRunTargetType,
    val keyword: String,
    val consecutiveFailures: Int,
    val lastFailureReason: AiFailureReason?,
    val status: KeywordQuarantineStatus,
    val quarantinedAt: Instant?,
    val releasedAt: Instant?,
    val updatedAt: Instant
) {

    fun toDomain(): KeywordQuarantine {
        return KeywordQuarantine.restore(
            id = KeywordQuarantineId.of(id),
            targetType = targetType,
            keyword = AiKeyword.of(keyword),
            consecutiveFailures = consecutiveFailures,
            lastFailureReason = lastFailureReason,
            status = status,
            quarantinedAt = quarantinedAt,
            releasedAt = releasedAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(quarantine: KeywordQuarantine): KeywordQuarantineMongoDocument {
            return KeywordQuarantineMongoDocument(
                id = quarantine.id.value,
                targetType = quarantine.targetType,
                keyword = quarantine.keyword.value,
                consecutiveFailures = quarantine.consecutiveFailures,
                lastFailureReason = quarantine.lastFailureReason,
                status = quarantine.status,
                quarantinedAt = quarantine.quarantinedAt,
                releasedAt = quarantine.releasedAt,
                updatedAt = quarantine.updatedAt
            )
        }
    }
}
