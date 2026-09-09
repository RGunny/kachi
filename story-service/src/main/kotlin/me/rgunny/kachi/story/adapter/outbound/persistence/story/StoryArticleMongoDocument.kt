package me.rgunny.kachi.story.adapter.outbound.persistence.story

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.story.domain.ArticleLanguage
import me.rgunny.kachi.story.domain.ArticleSource
import me.rgunny.kachi.story.domain.EmbeddingModel
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryArticle
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryKeyword
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document

/**
 * `story_articles` 컬렉션 문서.
 *
 * `_id`는 기사 id라 같은 기사는 한 번만 들어간다. [embedding]은 float32 little-endian 바이트열이고 모델은 [embeddingModel]에 따로 둔다.
 */
@Document(collection = "story_articles")
@CompoundIndexes(
    CompoundIndex(
        name = "ix_story_articles_story_id_attached_at",
        def = "{'storyId': 1, 'attachedAt': -1}"
    ),
    CompoundIndex(
        name = "ix_story_articles_collected_at",
        def = "{'collectedAt': 1}"
    )
)
data class StoryArticleMongoDocument(
    @Id
    val id: UUID,
    val title: String,
    val excerpt: String,
    val url: String,
    val source: String,
    val language: String,
    val publishedAt: Instant,
    val collectedAt: Instant,
    val matchedKeywords: List<String>,
    val embeddingModel: String,
    val embedding: ByteArray,
    val storyId: UUID,
    val decision: LinkDecisionMongoDocument,
    val attachedAt: Instant
) {

    fun toDomain(): StoryArticle {
        val model = EmbeddingModel.ofCode(embeddingModel)

        return StoryArticle.restore(
            newsId = NewsId.of(id),
            title = title,
            excerpt = excerpt,
            url = url,
            source = ArticleSource.valueOf(source),
            language = ArticleLanguage.of(language),
            publishedAt = publishedAt,
            collectedAt = collectedAt,
            matchedKeywords = matchedKeywords.map { StoryKeyword.of(it) },
            embedding = EmbeddingBinary.decode(model, embedding),
            storyId = StoryId.of(storyId),
            decision = decision.toDomain(),
            attachedAt = attachedAt
        )
    }

    companion object {

        fun fromDomain(article: StoryArticle): StoryArticleMongoDocument {
            return StoryArticleMongoDocument(
                id = article.newsId.value,
                title = article.title,
                excerpt = article.excerpt,
                url = article.url,
                source = article.source.name,
                language = article.language.value,
                publishedAt = article.publishedAt,
                collectedAt = article.collectedAt,
                matchedKeywords = article.matchedKeywords.map { it.value },
                embeddingModel = article.embedding.model.code,
                embedding = EmbeddingBinary.encode(article.embedding),
                storyId = article.storyId.value,
                decision = LinkDecisionMongoDocument.fromDomain(article.decision),
                attachedAt = article.attachedAt
            )
        }
    }
}
