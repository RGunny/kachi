package me.rgunny.kachi.ai.adapter.inbound.keyword

import me.rgunny.kachi.ai.application.port.inbound.keyword.model.ExpandKeywordsResult

/**
 * 요청이 실행되어 끝난 결과.
 */
data class AiKeywordExpansionStarted(
    val result: ExpandKeywordsResult
) : AiKeywordExpansionExecutionResult
