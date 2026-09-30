package me.rgunny.kachi.ai.application.port.outbound.llm.model

import java.time.Instant

/**
 * story 요약 입력으로 사용할 기사 한 건.
 */
data class StorySummaryArticle(
    val source: String,
    val title: String,
    val excerpt: String,
    val publishedAt: Instant
)
