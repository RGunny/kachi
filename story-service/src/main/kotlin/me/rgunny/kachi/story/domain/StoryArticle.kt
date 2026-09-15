package me.rgunny.kachi.story.domain

import java.time.Instant

/**
 * 기사 한 건의 사본과 그 기사의 소속·판정.
 */
class StoryArticle private constructor(
    val newsId: NewsId,
    val title: String,
    val excerpt: String,
    val url: String,
    val source: ArticleSource,
    val language: ArticleLanguage,
    val publishedAt: Instant,
    val collectedAt: Instant,
    val matchedKeywords: List<StoryKeyword>,
    val embedding: Embedding,
    val storyId: StoryId,
    val decision: LinkDecision,
    val attachedAt: Instant
) {
    companion object {

        fun create(
            newsId: NewsId,
            title: String,
            excerpt: String,
            url: String,
            source: ArticleSource,
            language: ArticleLanguage,
            publishedAt: Instant,
            collectedAt: Instant,
            matchedKeywords: List<StoryKeyword>,
            embedding: Embedding,
            storyId: StoryId,
            decision: LinkDecision,
            attachedAt: Instant
        ): StoryArticle {
            require(!decision.merged || decision.candidateStoryId == storyId) {
                "붙였다고 판정한 story와 소속 story가 다릅니다: decision=${decision.candidateStoryId}, storyId=$storyId"
            }

            return validated(
                newsId = newsId,
                title = title,
                excerpt = excerpt,
                url = url,
                source = source,
                language = language,
                publishedAt = publishedAt,
                collectedAt = collectedAt,
                matchedKeywords = matchedKeywords.distinct(),
                embedding = embedding,
                storyId = storyId,
                decision = decision,
                attachedAt = attachedAt
            )
        }

        fun restore(
            newsId: NewsId,
            title: String,
            excerpt: String,
            url: String,
            source: ArticleSource,
            language: ArticleLanguage,
            publishedAt: Instant,
            collectedAt: Instant,
            matchedKeywords: List<StoryKeyword>,
            embedding: Embedding,
            storyId: StoryId,
            decision: LinkDecision,
            attachedAt: Instant
        ): StoryArticle {
            return validated(
                newsId = newsId,
                title = title,
                excerpt = excerpt,
                url = url,
                source = source,
                language = language,
                publishedAt = publishedAt,
                collectedAt = collectedAt,
                matchedKeywords = matchedKeywords,
                embedding = embedding,
                storyId = storyId,
                decision = decision,
                attachedAt = attachedAt
            )
        }

        private fun validated(
            newsId: NewsId,
            title: String,
            excerpt: String,
            url: String,
            source: ArticleSource,
            language: ArticleLanguage,
            publishedAt: Instant,
            collectedAt: Instant,
            matchedKeywords: List<StoryKeyword>,
            embedding: Embedding,
            storyId: StoryId,
            decision: LinkDecision,
            attachedAt: Instant
        ): StoryArticle {
            require(title.isNotBlank()) { "제목은 빈 값일 수 없습니다" }
            require(excerpt.isNotBlank()) { "발췌문은 빈 값일 수 없습니다" }
            require(url.isNotBlank()) { "URL은 빈 값일 수 없습니다" }
            require(matchedKeywords.isNotEmpty()) { "매칭 키워드는 하나 이상이어야 합니다" }

            return StoryArticle(
                newsId = newsId,
                title = title,
                excerpt = excerpt,
                url = url,
                source = source,
                language = language,
                publishedAt = publishedAt,
                collectedAt = collectedAt,
                matchedKeywords = matchedKeywords,
                embedding = embedding,
                storyId = storyId,
                decision = decision,
                attachedAt = attachedAt
            )
        }
    }

    /** 임베딩과 판정기에 넣는 텍스트. */
    val embeddingText: EmbeddingText
        get() = EmbeddingText.of(title, excerpt)

    /** 병합이나 분리로 소속을 옮긴다. */
    fun reassign(storyId: StoryId): StoryArticle {
        require(storyId != this.storyId) { "이미 그 story에 속해 있습니다: $storyId" }

        return StoryArticle(
            newsId = newsId,
            title = title,
            excerpt = excerpt,
            url = url,
            source = source,
            language = language,
            publishedAt = publishedAt,
            collectedAt = collectedAt,
            matchedKeywords = matchedKeywords,
            embedding = embedding,
            storyId = storyId,
            decision = decision,
            attachedAt = attachedAt
        )
    }
}
