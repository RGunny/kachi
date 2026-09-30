package me.rgunny.kachi.ai.adapter.inbound.story

/**
 * lock을 확인할 수 없어 tick을 시작하지 않은 결과.
 *
 * [cause]는 lock 확인 중 난 예외다.
 */
data class AiStorySummaryLockUnavailable(
    val cause: Throwable
) : AiStorySummaryExecutionResult
