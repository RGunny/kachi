package me.rgunny.kachi.collector.application.port.outbound.outbox.model

import me.rgunny.kachi.collector.domain.News
import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxEventType
import java.time.Instant
import java.util.UUID

/**
 * 기사 한 건이 저장된 사건.
 *
 * 기사는 저장 후 바뀌지 않으므로 이벤트 키와 파티션 키가 모두 기사 id다.
 * topic이 기사 id로 compaction되면 기사당 최신 1건이 남아 전체 재처리가 가능하다.
 */
data class NewsCollectedEvent(
    val newsId: UUID,
    val source: NewsSource,
    val title: String,
    val excerpt: String,
    val url: String,
    val language: String,
    val publishedAt: Instant,
    val collectedAt: Instant,
    val matchedKeywords: List<String>,
    override val schemaVersion: Int = CollectorOutboxEvent.CURRENT_SCHEMA_VERSION
) : CollectorOutboxEvent {

    override val type: CollectorOutboxEventType
        get() = CollectorOutboxEventType.NEWS_COLLECTED

    override val eventKey: String
        get() = newsId.toString()

    override val partitionKey: String
        get() = newsId.toString()

    companion object {

        fun from(news: News): NewsCollectedEvent {
            return NewsCollectedEvent(
                newsId = news.id.value,
                source = news.source,
                title = news.title.value,
                excerpt = news.excerpt.value,
                url = news.url.value,
                language = news.language.value,
                publishedAt = news.publishedAt,
                collectedAt = news.collectedAt,
                matchedKeywords = news.matchedKeywords.map { it.value }
            )
        }
    }
}
