package me.rgunny.kachi.collector.application.port.inbound.news.model

import me.rgunny.kachi.collector.domain.CollectedKeyword
import java.time.Instant

/** 저장 뉴스 조회 조건 */
data class ListNewsQuery(
    val keyword: CollectedKeyword,
    val from: Instant?,
    val to: Instant?,
    val limit: Int
) {
    init {
        require(limit in 1..MAX_LIMIT) { "뉴스 조회 limit은 1 이상 ${MAX_LIMIT} 이하여야 합니다" }
        if (from != null && to != null) {
            require(!from.isAfter(to)) { "뉴스 조회 시작 시각은 종료 시각보다 이후일 수 없습니다" }
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 100
    }
}
