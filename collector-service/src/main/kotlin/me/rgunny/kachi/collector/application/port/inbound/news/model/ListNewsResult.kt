package me.rgunny.kachi.collector.application.port.inbound.news.model

import me.rgunny.kachi.collector.domain.News
import java.time.Instant
import java.util.UUID

/**
 * 저장 뉴스 조회 결과
 */
data class ListNewsResult(
    val id: UUID,
    val source: String,
    val title: String,
    val url: String,
    val publishedAt: Instant?,
    val collectedAt: Instant,
    val matchedKeywords: List<String>
) {
    companion object {
        fun from(news: News): ListNewsResult {
            return ListNewsResult(
                id = news.id.value,
                source = news.source.name,
                title = news.title.value,
                url = news.url.value,
                publishedAt = news.publishedAt,
                collectedAt = news.collectedAt,
                matchedKeywords = news.matchedKeywords.map { it.value }
            )
        }
    }
}
