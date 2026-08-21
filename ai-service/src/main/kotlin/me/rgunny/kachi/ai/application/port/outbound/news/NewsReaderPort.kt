package me.rgunny.kachi.ai.application.port.outbound.news

import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import java.time.Instant

/**
 * 외부 뉴스 수집 서비스에서 요약 대상 뉴스를 읽는 출력 포트
 */
interface NewsReaderPort {

    suspend fun findNews(
        keyword: AiKeyword,
        from: Instant?,
        to: Instant?,
        limit: Int
    ): List<NewsArticle>
}
