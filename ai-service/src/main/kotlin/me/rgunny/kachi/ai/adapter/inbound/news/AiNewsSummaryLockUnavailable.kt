package me.rgunny.kachi.ai.adapter.inbound.news

/**
 * lock을 확인할 수 없어 요약을 시작하지 않은 결과.
 *
 * 이미 실행 중이라 막힌 것과 달리 장애이며, 다음 차례에 저절로 풀린다고 볼 수 없다.
 */
data class AiNewsSummaryLockUnavailable(
    val cause: Throwable
) : AiNewsSummaryExecutionResult
