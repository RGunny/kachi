package me.rgunny.kachi.collector.application.port.out

import me.rgunny.kachi.collector.domain.NewsSource
import java.time.Instant

data class CollectedArticle(
    val source: NewsSource,
    val title: String,
    val url: String,
    val publishedAt: Instant?
)
