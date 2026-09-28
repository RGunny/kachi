package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.domain.summary.StoryDevelopmentKind

/**
 * LLM story 요약 응답.
 */
data class LlmStorySummaryResult(
    val title: String,
    val content: String,
    val sentiment: NewsSummarySentiment,
    val developmentKind: StoryDevelopmentKind,
    val metadata: LlmGenerationMetadata
)
