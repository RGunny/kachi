package me.rgunny.kachi.ai.adapter.outbound.news

import java.time.Instant
import java.util.UUID

data class CollectorServiceApiResponse<T>(
    val success: Boolean,
    val data: T?
)

data class CollectorServiceNewsResponse(
    val id: UUID,
    val source: String,
    val title: String,
    val url: String,
    val publishedAt: Instant?,
    val collectedAt: Instant,
    val matchedKeywords: List<String>
)
