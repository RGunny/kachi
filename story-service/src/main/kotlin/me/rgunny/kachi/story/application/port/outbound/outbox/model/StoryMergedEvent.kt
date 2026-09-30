package me.rgunny.kachi.story.application.port.outbound.outbox.model

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType

/**
 * story 하나가 다른 story에 흡수된 사건.
 */
data class StoryMergedEvent(
    val storyId: UUID,
    val mergedStoryId: UUID,
    val mergedAt: Instant,
    override val schemaVersion: Int = StoryOutboxEvent.CURRENT_SCHEMA_VERSION
) : StoryOutboxEvent {

    override val type: StoryOutboxEventType
        get() = StoryOutboxEventType.MERGED

    override val eventKey: String
        get() = "$mergedStoryId>$storyId"

    override val partitionKey: String
        get() = storyId.toString()

    companion object {

        /**
         * [merged]는 흡수된 뒤의 상태다.
         */
        fun from(merged: Story): StoryMergedEvent {
            val target = requireNotNull(merged.mergedInto) { "흡수되지 않은 story입니다: ${merged.id}" }
            val mergedAt = requireNotNull(merged.closedAt) { "닫히지 않은 story입니다: ${merged.id}" }

            return StoryMergedEvent(
                storyId = target.value,
                mergedStoryId = merged.id.value,
                mergedAt = mergedAt
            )
        }
    }
}
