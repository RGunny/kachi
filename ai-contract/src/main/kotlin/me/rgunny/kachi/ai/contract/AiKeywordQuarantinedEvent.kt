package me.rgunny.kachi.ai.contract

import java.time.Instant

/**
 * ai-service가 키워드 격리를 알릴 때 사용하는 이벤트 계약.
 */
data class AiKeywordQuarantinedEvent(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val quarantineId: String,
    val targetType: AiTargetType,
    val keyword: String,
    val consecutiveFailures: Int,
    val lastFailureReason: AiFailureReason,
    val quarantinedAt: Instant
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
