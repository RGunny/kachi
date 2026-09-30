package me.rgunny.kachi.story.application.port.inbound.assembly.model

import java.time.Instant
import me.rgunny.kachi.story.domain.ArticleLanguage
import me.rgunny.kachi.story.domain.ArticleSource
import me.rgunny.kachi.story.domain.EmbeddingText
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryKeyword

/**
 * 붙일 기사 한 건.
 */
data class AttachArticleCommand(
    val newsId: NewsId,
    val source: ArticleSource,
    val title: String,
    val excerpt: String,
    val url: String,
    val language: ArticleLanguage,
    val publishedAt: Instant,
    val collectedAt: Instant,
    val matchedKeywords: List<StoryKeyword>
) {
    init {
        require(title.isNotBlank()) { "제목은 빈 값일 수 없습니다" }
        require(excerpt.isNotBlank()) { "발췌문은 빈 값일 수 없습니다" }
        require(url.isNotBlank()) { "URL은 빈 값일 수 없습니다" }
        require(matchedKeywords.isNotEmpty()) { "매칭 키워드는 하나 이상이어야 합니다" }
    }

    val embeddingText: EmbeddingText
        get() = EmbeddingText.of(title, excerpt)
}
