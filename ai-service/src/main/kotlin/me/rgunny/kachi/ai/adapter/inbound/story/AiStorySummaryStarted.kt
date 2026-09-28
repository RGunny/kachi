package me.rgunny.kachi.ai.adapter.inbound.story

import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesResult

/**
 * 요청이 실행되어 끝난 결과.
 */
data class AiStorySummaryStarted(
    val result: SummarizeDueStoriesResult
) : AiStorySummaryExecutionResult
