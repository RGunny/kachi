package me.rgunny.kachi.collector.application.port.inbound.news

import me.rgunny.kachi.collector.application.port.inbound.news.model.ListNewsQuery
import me.rgunny.kachi.collector.application.port.inbound.news.model.ListNewsResult

/**
 * 저장된 뉴스를 키워드와 수집 시각 기준으로 조회하는 입력 포트
 */
interface ListNewsUseCase {

    suspend fun listNews(query: ListNewsQuery): List<ListNewsResult>
}
