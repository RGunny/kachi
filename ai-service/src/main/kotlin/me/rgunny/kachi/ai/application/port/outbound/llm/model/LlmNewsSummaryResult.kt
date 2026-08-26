package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment

/**
 * LLM 뉴스 요약 응답
 */
data class LlmNewsSummaryResult(
    val title: String,
    val content: String,
    val sentiment: NewsSummarySentiment,
    val metadata: LlmGenerationMetadata
)
