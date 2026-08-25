package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.KeywordQuarantineSummary
import java.time.Instant
import java.util.UUID

data class KeywordQuarantineResponse(
    val id: UUID,
    val targetType: String,
    val keyword: String,
    val consecutiveFailures: Int,
    val lastFailureReason: String?,
    val status: String,
    val quarantinedAt: Instant?,
    val releasedAt: Instant?,
    val updatedAt: Instant
) {
    companion object {

        fun from(summary: KeywordQuarantineSummary): KeywordQuarantineResponse {
            return KeywordQuarantineResponse(
                id = summary.id.value,
                targetType = summary.targetType.name,
                keyword = summary.keyword,
                consecutiveFailures = summary.consecutiveFailures,
                lastFailureReason = summary.lastFailureReason?.name,
                status = summary.status.name,
                quarantinedAt = summary.quarantinedAt,
                releasedAt = summary.releasedAt,
                updatedAt = summary.updatedAt
            )
        }
    }
}
