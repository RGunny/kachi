package me.rgunny.kachi.ai.adapter.outbound.persistence.quarantine

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantine
import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantineId
import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.story.StoryId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * story 격리 기록의 MongoDB Document.
 *
 * storyId unique index가 story 하나당 기록 하나 계약을 보장한다.
 */
@Document(collection = "ai_story_quarantines")
data class StoryQuarantineMongoDocument(
    @Id
    val id: UUID,
    @Indexed(unique = true)
    val storyId: UUID,
    val consecutiveFailures: Int,
    val lastFailureReason: AiFailureReason?,
    val status: StoryQuarantineStatus,
    val quarantinedAt: Instant?,
    val releasedAt: Instant?,
    val updatedAt: Instant
) {

    fun toDomain(): StoryQuarantine {
        return StoryQuarantine.restore(
            id = StoryQuarantineId.of(id),
            storyId = StoryId.of(storyId),
            consecutiveFailures = consecutiveFailures,
            lastFailureReason = lastFailureReason,
            status = status,
            quarantinedAt = quarantinedAt,
            releasedAt = releasedAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(quarantine: StoryQuarantine): StoryQuarantineMongoDocument {
            return StoryQuarantineMongoDocument(
                id = quarantine.id.value,
                storyId = quarantine.storyId.value,
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
