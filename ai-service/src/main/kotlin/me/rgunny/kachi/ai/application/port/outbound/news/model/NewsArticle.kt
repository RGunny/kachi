package me.rgunny.kachi.ai.application.port.outbound.news.model

import java.time.Instant
import java.util.UUID

/**
 * 뉴스 요약 입력으로 사용할 수집 뉴스
 */
data class NewsArticle(
    val id: UUID,
    val source: String,
    val title: String,
    val url: String,
    val publishedAt: Instant?,
    val collectedAt: Instant,
    val matchedKeywords: List<String>
)
