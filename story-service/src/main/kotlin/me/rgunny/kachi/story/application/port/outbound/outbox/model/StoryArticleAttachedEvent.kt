package me.rgunny.kachi.story.application.port.outbound.outbox.model

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.story.domain.ArticleSource
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryArticle
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType

/**
 * 기사 한 건이 story에 붙은 사건.
 */
data class StoryArticleAttachedEvent(
    val storyId: UUID,
    val newsId: UUID,
    val title: String,
    val excerpt: String,
    val url: String,
    val source: ArticleSource,
    val publishedAt: Instant,
    val storyKeywords: List<String>,
    val storyArticleCount: Int,
    val attachedAt: Instant,
    override val schemaVersion: Int = StoryOutboxEvent.CURRENT_SCHEMA_VERSION
) : StoryOutboxEvent {

    override val type: StoryOutboxEventType
        get() = StoryOutboxEventType.ARTICLE_ATTACHED

    override val eventKey: String
        get() = "$storyId:$newsId"

    override val partitionKey: String
        get() = storyId.toString()

    companion object {

        /**
         * [story]는 기사를 붙인 뒤의 상태다.
         */
        fun from(story: Story, article: StoryArticle): StoryArticleAttachedEvent {
            require(story.id == article.storyId) { "다른 story의 기사입니다: article=${article.storyId}, story=${story.id}" }

            return StoryArticleAttachedEvent(
                storyId = story.id.value,
                newsId = article.newsId.value,
                title = article.title,
                excerpt = article.excerpt,
                url = article.url,
                source = article.source,
                publishedAt = article.publishedAt,
                storyKeywords = story.keywords.map { it.value }.sorted(),
                storyArticleCount = story.articleCount,
                attachedAt = article.attachedAt
            )
        }
    }
}
