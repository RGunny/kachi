package me.rgunny.kachi.ai.application.port.out.llm

import me.rgunny.kachi.ai.domain.NewsSummarySentiment
import me.rgunny.kachi.ai.domain.TokenUsage

/**
 * LLM 뉴스 요약 응답
 */
data class LlmNewsSummaryResult(
    val title: String,
    val content: String,
    val sentiment: NewsSummarySentiment,
    val tokenUsage: TokenUsage
)
