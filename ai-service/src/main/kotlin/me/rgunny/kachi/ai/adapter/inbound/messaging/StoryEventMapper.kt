package me.rgunny.kachi.ai.adapter.inbound.messaging

import java.util.UUID
import me.rgunny.kachi.ai.application.port.inbound.story.model.ApplyStoryMergeCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.RecordStoryArticleCommand
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.story.StoryId
import me.rgunny.kachi.story.contract.StoryArticleAttachedEvent
import me.rgunny.kachi.story.contract.StoryArticleSource
import me.rgunny.kachi.story.contract.StoryMergedEvent

/**
 * story 이벤트 계약을 application 명령으로 옮기는 Mapper.
 */
object StoryEventMapper {

    fun toRecordCommand(event: StoryArticleAttachedEvent): RecordStoryArticleCommand {
        require(event.schemaVersion == StoryArticleAttachedEvent.CURRENT_SCHEMA_VERSION) {
            "unsupported story article attached schemaVersion=${event.schemaVersion}"
        }

        return RecordStoryArticleCommand(
            storyId = StoryId.of(UUID.fromString(event.storyId)),
            newsId = UUID.fromString(event.newsId),
            source = sourceName(event.source),
            title = event.title,
            excerpt = event.excerpt,
            url = event.url,
            publishedAt = event.publishedAt,
            storyKeywords = event.storyKeywords.map(AiKeyword::of),
            storyArticleCount = event.storyArticleCount,
            attachedAt = event.attachedAt
        )
    }

    fun toMergeCommand(event: StoryMergedEvent): ApplyStoryMergeCommand {
        require(event.schemaVersion == StoryMergedEvent.CURRENT_SCHEMA_VERSION) {
            "unsupported story merged schemaVersion=${event.schemaVersion}"
        }

        return ApplyStoryMergeCommand(
            storyId = StoryId.of(UUID.fromString(event.storyId)),
            mergedStoryId = StoryId.of(UUID.fromString(event.mergedStoryId)),
            mergedAt = event.mergedAt
        )
    }

    private fun sourceName(source: StoryArticleSource): String {
        return when (source) {
            StoryArticleSource.GOOGLE -> "GOOGLE"
            StoryArticleSource.NAVER -> "NAVER"
            StoryArticleSource.FINNHUB -> "FINNHUB"
        }
    }
}
