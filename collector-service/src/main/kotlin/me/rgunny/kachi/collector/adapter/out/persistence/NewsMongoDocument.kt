package me.rgunny.kachi.collector.adapter.out.persistence

import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.News
import me.rgunny.kachi.collector.domain.NewsId
import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.NewsTitle
import me.rgunny.kachi.collector.domain.NewsUrl
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

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
    val url: String,
    val urlHash: String,
    @Indexed
    val titleFingerprint: String,
    val publishedAt: Instant?,
    @Indexed
    val collectedAt: Instant,
    val matchedKeywords: List<String>
) {
    fun toDomain(): News {
        return News.restore(
            id = NewsId.of(id),
            source = source,
            title = NewsTitle.of(title),
            url = NewsUrl.of(url),
            urlHash = urlHash,
            titleFingerprint = titleFingerprint,
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
                url = news.url.value,
                urlHash = news.urlHash,
                titleFingerprint = news.titleFingerprint,
                publishedAt = news.publishedAt,
                collectedAt = news.collectedAt,
                matchedKeywords = news.matchedKeywords.map { it.value }
            )
        }
    }
}
