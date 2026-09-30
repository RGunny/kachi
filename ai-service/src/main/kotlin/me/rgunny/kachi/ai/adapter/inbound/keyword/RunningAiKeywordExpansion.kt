package me.rgunny.kachi.ai.adapter.inbound.keyword

import java.time.Instant

/**
 * 현재 인스턴스에서 실행 중인 키워드 확장 작업의 최소 메타데이터.
 *
 * executor가 중복 실행을 막으려고 메모리에 들고 있는 상태이며, 막힌 요청에 시작 시각을 알릴 때 함께 나간다.
 */
data class RunningAiKeywordExpansion(
    val startedAt: Instant
)
