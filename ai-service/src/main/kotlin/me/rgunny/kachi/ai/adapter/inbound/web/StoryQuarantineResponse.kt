package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.StoryQuarantineSnapshot
import java.time.Instant
import java.util.UUID

/**
 * story 격리 기록 조회 응답.
 */
data class StoryQuarantineResponse(
    val id: UUID,
    val storyId: UUID,
    val consecutiveFailures: Int,
    val lastFailureReason: String?,
    val status: String,
    val quarantinedAt: Instant?,
    val releasedAt: Instant?,
    val updatedAt: Instant
) {
    companion object {

        fun from(snapshot: StoryQuarantineSnapshot): StoryQuarantineResponse {
            return StoryQuarantineResponse(
                id = snapshot.id.value,
                storyId = snapshot.storyId.value,
                consecutiveFailures = snapshot.consecutiveFailures,
                lastFailureReason = snapshot.lastFailureReason?.name,
                status = snapshot.status.name,
                quarantinedAt = snapshot.quarantinedAt,
                releasedAt = snapshot.releasedAt,
                updatedAt = snapshot.updatedAt
            )
        }
    }
}
