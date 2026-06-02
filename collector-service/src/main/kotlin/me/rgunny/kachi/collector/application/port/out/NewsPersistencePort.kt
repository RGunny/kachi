package me.rgunny.kachi.collector.application.port.out

import me.rgunny.kachi.collector.domain.News
import me.rgunny.kachi.collector.domain.NewsSource

/** 수집 뉴스 저장소 접근을 application 계층에 제공하는 출력 포트 */
interface NewsPersistencePort {

    suspend fun findExistingUrlHashes(source: NewsSource, urlHashes: Set<String>): Set<String>

    suspend fun save(news: News): SaveNewsResult
}
