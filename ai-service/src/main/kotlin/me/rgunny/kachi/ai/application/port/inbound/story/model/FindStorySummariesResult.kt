package me.rgunny.kachi.ai.application.port.inbound.story.model

/**
 * story 요약 버전 조회 결과.
 *
 * 최신 버전이 앞이다.
 */
data class FindStorySummariesResult(
    val summaries: List<StorySummarySnapshot>
)
