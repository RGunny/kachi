package me.rgunny.kachi.ai.adapter.inbound.news

/**
 * 이미 실행 중인 작업이 있어 요청이 막혔다.
 */
data class AiNewsSummaryAlreadyRunning(
    val runningSummary: RunningAiNewsSummary
) : AiNewsSummaryExecutionResult
