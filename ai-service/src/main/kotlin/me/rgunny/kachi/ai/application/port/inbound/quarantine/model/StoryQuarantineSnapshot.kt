package me.rgunny.kachi.ai.application.port.inbound.quarantine.model

import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantine
import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantineId
import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.story.StoryId
import java.time.Instant

/**
 * 조회 응답이 쓰는 story 격리 기록 스냅샷.
 */
data class StoryQuarantineSnapshot(
    val id: StoryQuarantineId,
    val storyId: StoryId,
    val consecutiveFailures: Int,
    val lastFailureReason: AiFailureReason?,
    val status: StoryQuarantineStatus,
    val quarantinedAt: Instant?,
    val releasedAt: Instant?,
    val updatedAt: Instant
) {
    companion object {

        fun from(quarantine: StoryQuarantine): StoryQuarantineSnapshot {
            return StoryQuarantineSnapshot(
                id = quarantine.id,
                storyId = quarantine.storyId,
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
