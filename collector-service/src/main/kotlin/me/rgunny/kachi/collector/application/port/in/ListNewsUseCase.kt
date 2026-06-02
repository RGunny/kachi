package me.rgunny.kachi.collector.application.port.`in`

/**
 * 저장된 뉴스를 키워드와 수집 시각 기준으로 조회하는 입력 포트
 */
interface ListNewsUseCase {

    suspend fun listNews(query: ListNewsQuery): List<ListNewsResult>
}
