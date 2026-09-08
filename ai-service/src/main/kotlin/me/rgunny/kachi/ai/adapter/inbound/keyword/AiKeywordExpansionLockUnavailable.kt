package me.rgunny.kachi.ai.adapter.inbound.keyword

/**
 * lock을 확인할 수 없어 키워드 확장을 시작하지 않은 결과.
 *
 * 이미 실행 중이라 막힌 것과 달리 장애이며, 다음 차례에 저절로 풀린다고 볼 수 없다.
 */
data class AiKeywordExpansionLockUnavailable(
    val cause: Throwable
) : AiKeywordExpansionExecutionResult
