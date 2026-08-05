package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.out.news.NewsArticle
import me.rgunny.kachi.ai.application.port.out.news.NewsReaderPort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import java.time.Instant

class FakeNewsReaderPort : NewsReaderPort {
    var articlesByKeyword: Map<AiKeyword, List<NewsArticle>> = emptyMap()

    override suspend fun findNews(
        keyword: AiKeyword,
        from: Instant?,
        to: Instant?,
        limit: Int
    ): List<NewsArticle> {
        return articlesByKeyword[keyword].orEmpty().take(limit)
    }
}
