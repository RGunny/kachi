package me.rgunny.kachi.ai.application.service.news

import java.time.Instant

/**
 * 이번 실행이 실제로 조회할 구간.
 *
 * 도메인의 `SummaryWindow`와 달리 null을 허용한다.
 * 수동 실행은 구간을 지정하지 않을 수 있고, 그때는 collector가 전체 기간을 조회한다.
 */
data class ResolvedSummaryWindow(
    val from: Instant?,
    val to: Instant?
)
