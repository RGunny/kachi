package me.rgunny.kachi.ai.adapter.inbound.news

import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizeNewsResult

/**
 * 요청이 실행되어 끝났다.
 */
data class AiNewsSummaryStarted(
    val result: SummarizeNewsResult
) : AiNewsSummaryExecutionResult
