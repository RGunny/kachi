package me.rgunny.kachi.collector.application.port.`in`

import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.NewsSource

data class CollectNewsCommand(
    val keywords: List<CollectedKeyword>,
    val sources: Set<NewsSource> = emptySet()
)
