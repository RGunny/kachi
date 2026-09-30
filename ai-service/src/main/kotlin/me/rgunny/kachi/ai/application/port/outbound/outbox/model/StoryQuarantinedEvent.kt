package me.rgunny.kachi.ai.application.port.outbound.outbox.model

import me.rgunny.kachi.ai.domain.outbox.AiOutboxEventType
import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantine
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import java.time.Instant
import java.util.UUID

/**
 * story가 연속 실패로 격리된 사건.
 */
data class StoryQuarantinedEvent(
    val quarantineId: UUID,
    val storyId: UUID,
    val consecutiveFailures: Int,
    val lastFailureReason: AiFailureReason,
    val quarantinedAt: Instant,
    override val schemaVersion: Int = AiOutboxEvent.CURRENT_SCHEMA_VERSION
) : AiOutboxEvent {

    override val type: AiOutboxEventType
        get() = AiOutboxEventType.STORY_QUARANTINED

    /**
     * 격리 기록 id와 격리 시각의 조합(해제 뒤 재격리해도 기록 id는 동일).
     */
    override val eventKey: String
        get() = "$quarantineId:${quarantinedAt.toEpochMilli()}"

    override val partitionKey: String
        get() = storyId.toString()

    companion object {

        fun from(quarantine: StoryQuarantine): StoryQuarantinedEvent {
            require(quarantine.isQuarantined) {
                "격리 상태가 아닌 기록으로는 격리 이벤트를 만들 수 없습니다: ${quarantine.status}"
            }
            val quarantinedAt = requireNotNull(quarantine.quarantinedAt) {
                "격리 기록에 격리 시각이 없습니다"
            }
            val lastFailureReason = requireNotNull(quarantine.lastFailureReason) {
                "격리 기록에 마지막 실패 원인이 없습니다"
            }

            return StoryQuarantinedEvent(
                quarantineId = quarantine.id.value,
                storyId = quarantine.storyId.value,
                consecutiveFailures = quarantine.consecutiveFailures,
                lastFailureReason = lastFailureReason,
                quarantinedAt = quarantinedAt
            )
        }
    }
}
