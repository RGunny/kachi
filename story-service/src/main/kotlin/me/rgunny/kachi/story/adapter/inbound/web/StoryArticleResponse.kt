package me.rgunny.kachi.story.adapter.inbound.web

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.story.domain.StoryArticle

/**
 * story 구성 기사 한 건의 조회 응답.
 *
 * 발췌문과 임베딩은 싣지 않는다.
 */
data class StoryArticleResponse(
    val newsId: UUID,
    val title: String,
    val url: String,
    val source: String,
    val language: String,
    val publishedAt: Instant,
    val collectedAt: Instant,
    val matchedKeywords: List<String>,
    val attachedAt: Instant,
    val decision: LinkDecisionResponse
) {
    companion object {

        fun from(article: StoryArticle): StoryArticleResponse {
            return StoryArticleResponse(
                newsId = article.newsId.value,
                title = article.title,
                url = article.url,
                source = article.source.name,
                language = article.language.value,
                publishedAt = article.publishedAt,
                collectedAt = article.collectedAt,
                matchedKeywords = article.matchedKeywords.map { it.value },
                attachedAt = article.attachedAt,
                decision = LinkDecisionResponse.from(article.decision)
            )
        }
    }
}
