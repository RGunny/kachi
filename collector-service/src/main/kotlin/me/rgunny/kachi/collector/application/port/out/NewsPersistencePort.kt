package me.rgunny.kachi.collector.application.port.out

import me.rgunny.kachi.collector.domain.News

/** 수집 뉴스 저장소 접근을 application 계층에 제공하는 출력 포트 */
interface NewsPersistencePort {

    suspend fun findExistingUrlHashes(urlHashes: Set<String>): Set<String>

    suspend fun save(news: News): SaveNewsResult
}
