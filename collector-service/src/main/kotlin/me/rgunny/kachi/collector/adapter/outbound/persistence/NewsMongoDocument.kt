package me.rgunny.kachi.collector.adapter.outbound.persistence

import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.News
import me.rgunny.kachi.collector.domain.NewsExcerpt
import me.rgunny.kachi.collector.domain.NewsId
import me.rgunny.kachi.collector.domain.NewsLanguage
import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.NewsTitle
import me.rgunny.kachi.collector.domain.NewsUrl
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

/**
 * `news` 컬렉션 문서. 도메인 `News`와 1:1이고 `urlHash`는 정규화 URL의 해시다.
 */
@Document(collection = "news")
@CompoundIndex(
    name = "uk_news_source_url_hash",
    def = "{'source': 1, 'urlHash': 1}",
    unique = true
)
data class NewsMongoDocument(
    @Id
    val id: UUID,
    val source: NewsSource,
    val title: String,
    val excerpt: String,
    val url: String,
    val urlHash: String,
    val language: String,
    val publishedAt: Instant,
    @Indexed
    val collectedAt: Instant,
    val matchedKeywords: List<String>
) {
    fun toDomain(): News {
        return News.restore(
            id = NewsId.of(id),
            source = source,
            title = NewsTitle.of(title),
            excerpt = NewsExcerpt.of(excerpt),
            url = NewsUrl.of(url),
            urlHash = urlHash,
            language = NewsLanguage.of(language),
            publishedAt = publishedAt,
            collectedAt = collectedAt,
            matchedKeywords = matchedKeywords.map { CollectedKeyword.of(it) }
        )
    }

    companion object {
        fun fromDomain(news: News): NewsMongoDocument {
            return NewsMongoDocument(
                id = news.id.value,
                source = news.source,
                title = news.title.value,
                excerpt = news.excerpt.value,
                url = news.url.value,
                urlHash = news.urlHash,
                language = news.language.value,
                publishedAt = news.publishedAt,
                collectedAt = news.collectedAt,
                matchedKeywords = news.matchedKeywords.map { it.value }
            )
        }
    }
}
