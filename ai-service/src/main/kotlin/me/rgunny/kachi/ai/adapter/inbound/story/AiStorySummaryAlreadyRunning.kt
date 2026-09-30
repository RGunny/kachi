package me.rgunny.kachi.ai.adapter.inbound.story

/**
 * 이미 실행 중인 작업이 있어 요청이 막힌 결과.
 */
data class AiStorySummaryAlreadyRunning(
    val runningSummary: RunningAiStorySummary
) : AiStorySummaryExecutionResult
