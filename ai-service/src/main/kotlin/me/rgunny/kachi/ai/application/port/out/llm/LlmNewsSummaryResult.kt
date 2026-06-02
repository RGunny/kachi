package me.rgunny.kachi.ai.application.port.out.llm

import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment

/**
 * LLM 뉴스 요약 응답
 */
data class LlmNewsSummaryResult(
    val title: String,
    val content: String,
    val sentiment: NewsSummarySentiment,
    val tokenUsage: TokenUsage
)
