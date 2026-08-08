package me.rgunny.kachi.collector.application.port.outbound.news

import me.rgunny.kachi.collector.application.port.outbound.news.model.SaveNewsResult
import me.rgunny.kachi.collector.domain.News
import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.CollectedKeyword
import java.time.Instant

/** 수집 뉴스 저장소 접근을 application 계층에 제공하는 출력 포트 */
interface NewsPersistencePort {

    suspend fun findExistingUrlHashes(source: NewsSource, urlHashes: Set<String>): Set<String>

    suspend fun findByKeyword(
        keyword: CollectedKeyword,
        from: Instant?,
        to: Instant?,
        limit: Int
    ): List<News>

    suspend fun save(news: News): SaveNewsResult
}
