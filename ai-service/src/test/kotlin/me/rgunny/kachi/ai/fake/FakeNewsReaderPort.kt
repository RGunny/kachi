package me.rgunny.kachi.ai.fake

import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.application.port.outbound.news.NewsReaderPort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import java.time.Instant

class FakeNewsReaderPort : NewsReaderPort {
    var articlesByKeyword: Map<AiKeyword, List<NewsArticle>> = emptyMap()

    /** 뉴스 조회 실패가 요약 실패로 어떻게 분류되는지 확인하기 위한 주입 지점이다. */
    var failureByKeyword: Map<AiKeyword, Throwable> = emptyMap()

    /** 조기 중단으로 건너뛴 키워드가 조회조차 되지 않았는지 확인하기 위해 조회 대상을 남긴다. */
    val readKeywords: MutableList<AiKeyword> = mutableListOf()

    override suspend fun findNews(
        keyword: AiKeyword,
        from: Instant?,
        to: Instant?,
        limit: Int
    ): List<NewsArticle> {
        readKeywords += keyword
        failureByKeyword[keyword]?.let { throw it }

        return articlesByKeyword[keyword].orEmpty().take(limit)
    }
}
