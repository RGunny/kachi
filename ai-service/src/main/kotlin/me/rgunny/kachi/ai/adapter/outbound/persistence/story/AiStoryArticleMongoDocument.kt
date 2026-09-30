package me.rgunny.kachi.ai.adapter.outbound.persistence.story

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.ai.domain.story.AiStoryArticle
import me.rgunny.kachi.ai.domain.story.StoryId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * 기사 사본의 MongoDB Document.
 *
 * _id는 newsId이며 기사 한 건은 story 하나에만 속한다.
 * pending index는 story의 미요약 기사 조회(attachedAt 오름차순)를 받친다.
 */
@Document(collection = "story_articles")
@CompoundIndexes(
    CompoundIndex(
        name = "ix_story_articles_story_pending_attached_at",
        def = "{'storyId': 1, 'summarizedInVersion': 1, 'attachedAt': 1}"
    )
)
data class AiStoryArticleMongoDocument(
    @Id
    val id: UUID,
    val storyId: UUID,
    val source: String,
    val title: String,
    val excerpt: String,
    val url: String,
    val publishedAt: Instant,
    val attachedAt: Instant,
    val summarizedInVersion: Long?
) {

    fun toDomain(): AiStoryArticle {
        return AiStoryArticle.restore(
            newsId = id,
            storyId = StoryId.of(storyId),
            source = source,
            title = title,
            excerpt = excerpt,
            url = url,
            publishedAt = publishedAt,
            attachedAt = attachedAt,
            summarizedInVersion = summarizedInVersion
        )
    }

    companion object {
        fun fromDomain(article: AiStoryArticle): AiStoryArticleMongoDocument {
            return AiStoryArticleMongoDocument(
                id = article.newsId,
                storyId = article.storyId.value,
                source = article.source,
                title = article.title,
                excerpt = article.excerpt,
                url = article.url,
                publishedAt = article.publishedAt,
                attachedAt = article.attachedAt,
                summarizedInVersion = article.summarizedInVersion
            )
        }
    }
}
