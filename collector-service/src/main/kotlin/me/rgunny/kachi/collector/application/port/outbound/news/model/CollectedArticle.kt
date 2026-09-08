package me.rgunny.kachi.collector.application.port.outbound.news.model

import me.rgunny.kachi.collector.domain.NewsSource
import java.time.Instant

/**
 * provider adapter가 application 계층에 넘기는 수집 기사.
 *
 * HTML 제거 같은 형식 정리는 adapter가 끝낸 상태다. 값이 하나라도 없는 item은 adapter가 여기까지 넘기지 않는다.
 * 값 검증과 정규화는 도메인으로 옮길 때 한다.
 */
data class CollectedArticle(
    val source: NewsSource,
    val title: String,
    val excerpt: String,
    val url: String,
    val language: String,
    val publishedAt: Instant
)
