package me.rgunny.kachi.collector.application.port.outbound.news.model

import me.rgunny.kachi.collector.domain.NewsSource
import java.time.Instant

data class CollectedArticle(
    val source: NewsSource,
    val title: String,
    val url: String,
    val publishedAt: Instant?
)
