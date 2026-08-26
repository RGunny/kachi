package me.rgunny.kachi.ai.adapter.inbound.keyword

/**
 * 이미 실행 중인 작업이 있어 요청이 막혔다.
 */
data class AiKeywordExpansionAlreadyRunning(
    val runningExpansion: RunningAiKeywordExpansion
) : AiKeywordExpansionExecutionResult
