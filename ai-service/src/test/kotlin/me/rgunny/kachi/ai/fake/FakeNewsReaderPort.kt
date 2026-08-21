package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.application.port.outbound.news.NewsReaderPort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import java.time.Instant

class FakeNewsReaderPort : NewsReaderPort {
    var articlesByKeyword: Map<AiKeyword, List<NewsArticle>> = emptyMap()

    /** 조기 중단으로 건너뛴 키워드가 조회조차 되지 않았는지 확인하기 위해 조회 대상을 남긴다. */
    val readKeywords: MutableList<AiKeyword> = mutableListOf()

    override suspend fun findNews(
        keyword: AiKeyword,
        from: Instant?,
        to: Instant?,
        limit: Int
    ): List<NewsArticle> {
        readKeywords += keyword

        return articlesByKeyword[keyword].orEmpty().take(limit)
    }
}
