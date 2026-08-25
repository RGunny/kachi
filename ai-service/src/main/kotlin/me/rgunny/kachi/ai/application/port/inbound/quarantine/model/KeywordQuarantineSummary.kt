package me.rgunny.kachi.ai.application.port.inbound.quarantine.model

import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineId
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import java.time.Instant

/**
 * 조회 응답이 쓰는 격리 기록 스냅샷.
 *
 * 도메인 객체를 어댑터까지 흘리지 않기 위해 값만 옮겨 담는다.
 */
data class KeywordQuarantineSummary(
    val id: KeywordQuarantineId,
    val targetType: AiRunTargetType,
    val keyword: String,
    val consecutiveFailures: Int,
    val lastFailureReason: AiFailureReason?,
    val status: KeywordQuarantineStatus,
    val quarantinedAt: Instant?,
    val releasedAt: Instant?,
    val updatedAt: Instant
) {
    companion object {

        fun from(quarantine: KeywordQuarantine): KeywordQuarantineSummary {
            return KeywordQuarantineSummary(
                id = quarantine.id,
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
