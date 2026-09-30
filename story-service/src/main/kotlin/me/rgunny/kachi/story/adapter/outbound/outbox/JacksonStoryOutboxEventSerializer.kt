package me.rgunny.kachi.story.adapter.outbound.outbox

import me.rgunny.kachi.story.application.port.outbound.outbox.StoryOutboxEventSerializer
import me.rgunny.kachi.story.application.port.outbound.outbox.model.StoryArticleAttachedEvent
import me.rgunny.kachi.story.application.port.outbound.outbox.model.StoryMergedEvent
import me.rgunny.kachi.story.application.port.outbound.outbox.model.StoryOutboxEvent
import me.rgunny.kachi.story.contract.StoryArticleAttachedEvent as StoryArticleAttachedContract
import me.rgunny.kachi.story.contract.StoryArticleSource
import me.rgunny.kachi.story.contract.StoryMergedEvent as StoryMergedContract
import me.rgunny.kachi.story.domain.ArticleSource
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

/**
 * application 이벤트를 계약 객체로 옮긴 뒤 JSON으로 쓰는 serializer.
 */
@Component
class JacksonStoryOutboxEventSerializer(
    private val jsonMapper: JsonMapper
) : StoryOutboxEventSerializer {

    override fun serialize(event: StoryOutboxEvent): String {
        val contract: Any = when (event) {
            is StoryArticleAttachedEvent -> toContract(event)
            is StoryMergedEvent -> toContract(event)
        }

        return jsonMapper.writeValueAsString(contract)
    }

    private fun toContract(event: StoryArticleAttachedEvent): StoryArticleAttachedContract {
        return StoryArticleAttachedContract(
            schemaVersion = event.schemaVersion,
            storyId = event.storyId.toString(),
            newsId = event.newsId.toString(),
            title = event.title,
            excerpt = event.excerpt,
            url = event.url,
            source = toContract(event.source),
            publishedAt = event.publishedAt,
            storyKeywords = event.storyKeywords,
            storyArticleCount = event.storyArticleCount,
            attachedAt = event.attachedAt
        )
    }

    private fun toContract(event: StoryMergedEvent): StoryMergedContract {
        return StoryMergedContract(
            schemaVersion = event.schemaVersion,
            storyId = event.storyId.toString(),
            mergedStoryId = event.mergedStoryId.toString(),
            mergedAt = event.mergedAt
        )
    }

    private fun toContract(source: ArticleSource): StoryArticleSource {
        return when (source) {
            ArticleSource.GOOGLE -> StoryArticleSource.GOOGLE
            ArticleSource.NAVER -> StoryArticleSource.NAVER
            ArticleSource.FINNHUB -> StoryArticleSource.FINNHUB
        }
    }
}
