package me.rgunny.kachi.ai.application.port.outbound.outbox.model

import me.rgunny.kachi.ai.domain.outbox.AiOutboxEventType
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import java.time.Instant
import java.util.UUID

/**
 * 키워드가 연속 실패로 격리된 사건.
 */
data class KeywordQuarantinedEvent(
    val quarantineId: UUID,
    val targetType: AiRunTargetType,
    val keyword: String,
    val consecutiveFailures: Int,
    val lastFailureReason: AiFailureReason,
    val quarantinedAt: Instant,
    override val schemaVersion: Int = AiOutboxEvent.CURRENT_SCHEMA_VERSION
) : AiOutboxEvent {

    override val type: AiOutboxEventType
        get() = AiOutboxEventType.KEYWORD_QUARANTINED

    /**
     * 같은 키워드가 해제 뒤 다시 격리되면 기록은 같은 id를 유지하므로 격리 시각까지 넣어야 새 이벤트가 된다.
     */
    override val eventKey: String
        get() = "$quarantineId:${quarantinedAt.toEpochMilli()}"

    override val partitionKey: String
        get() = keyword

    companion object {

        fun from(quarantine: KeywordQuarantine): KeywordQuarantinedEvent {
            require(quarantine.isQuarantined) {
                "격리 상태가 아닌 기록으로는 격리 이벤트를 만들 수 없습니다: ${quarantine.status}"
            }
            val quarantinedAt = requireNotNull(quarantine.quarantinedAt) {
                "격리 기록에 격리 시각이 없습니다"
            }
            val lastFailureReason = requireNotNull(quarantine.lastFailureReason) {
                "격리 기록에 마지막 실패 원인이 없습니다"
            }

            return KeywordQuarantinedEvent(
                quarantineId = quarantine.id.value,
                targetType = quarantine.targetType,
                keyword = quarantine.keyword.value,
                consecutiveFailures = quarantine.consecutiveFailures,
                lastFailureReason = lastFailureReason,
                quarantinedAt = quarantinedAt
            )
        }
    }
}
