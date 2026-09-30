package me.rgunny.kachi.ai.adapter.inbound.story

/**
 * story 요약 tick 요청의 결과.
 *
 * 실행된 요청은 [AiStorySummaryStarted], 이미 실행 중이라 막힌 요청은 [AiStorySummaryAlreadyRunning], lock을 확인하지 못한 요청은 [AiStorySummaryLockUnavailable]이다.
 */
sealed interface AiStorySummaryExecutionResult
