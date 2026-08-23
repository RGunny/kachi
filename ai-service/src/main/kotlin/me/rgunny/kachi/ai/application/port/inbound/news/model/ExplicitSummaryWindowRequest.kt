package me.rgunny.kachi.ai.application.port.inbound.news.model

import java.time.Instant

/**
 * 구간을 직접 지정한다.
 * watermark를 읽지도 전진시키지도 않는다.
 */
data class ExplicitSummaryWindowRequest(
    val from: Instant?,
    val to: Instant?
) : SummaryWindowRequest {
    init {
        if (from != null && to != null) {
            require(!from.isAfter(to)) { "뉴스 요약 시작 시각은 종료 시각보다 이후일 수 없습니다" }
        }
    }
}
