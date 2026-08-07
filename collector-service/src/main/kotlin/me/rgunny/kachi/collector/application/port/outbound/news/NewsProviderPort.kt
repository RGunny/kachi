package me.rgunny.kachi.collector.application.port.outbound.news

import me.rgunny.kachi.collector.application.port.outbound.news.model.CollectedArticle
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.NewsSource

/** 외부 뉴스 provider 수집을 application 계층에 제공하는 출력 포트 */
interface NewsProviderPort {

    val source: NewsSource

    suspend fun collect(keyword: CollectedKeyword): List<CollectedArticle>
}
