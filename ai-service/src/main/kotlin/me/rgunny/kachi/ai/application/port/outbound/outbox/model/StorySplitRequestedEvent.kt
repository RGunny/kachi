package me.rgunny.kachi.ai.application.port.outbound.outbox.model

import me.rgunny.kachi.ai.domain.outbox.AiOutboxEventType
import me.rgunny.kachi.ai.domain.summary.StoryDevelopmentKind
import me.rgunny.kachi.ai.domain.summary.StorySummary
import java.time.Instant
import java.util.UUID

/**
 * 요약 판정이 다른 사건으로 본 기사들의 분리를 요청하는 사건.
 */
data class StorySplitRequestedEvent(
    val storyId: UUID,
    val summaryId: UUID,
    val version: Long,
    val newsIds: List<UUID>,
    val requestedAt: Instant,
    override val schemaVersion: Int = AiOutboxEvent.CURRENT_SCHEMA_VERSION
) : AiOutboxEvent {

    override val type: AiOutboxEventType
        get() = AiOutboxEventType.STORY_SPLIT_REQUESTED

    /**
     * split 접두를 붙인 storyId와 version의 조합(요약 이벤트와 outbox unique index 공유).
     */
    override val eventKey: String
        get() = "split:$storyId:$version"

    override val partitionKey: String
        get() = storyId.toString()

    companion object {

        fun from(summary: StorySummary): StorySplitRequestedEvent {
            require(summary.developmentKind == StoryDevelopmentKind.NEW_STORY) {
                "분리 요청은 NEW_STORY 판정에서만 만들 수 있습니다: ${summary.developmentKind}"
            }

            return StorySplitRequestedEvent(
                storyId = summary.storyId.value,
                summaryId = summary.id.value,
                version = summary.version,
                newsIds = summary.newNewsIds,
                requestedAt = summary.createdAt
            )
        }
    }
}
