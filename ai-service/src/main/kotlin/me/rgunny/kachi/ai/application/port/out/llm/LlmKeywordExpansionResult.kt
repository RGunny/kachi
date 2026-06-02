package me.rgunny.kachi.ai.application.port.out.llm

import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.llm.TokenUsage

/**
 * LLM 키워드 확장 응답
 */
data class LlmKeywordExpansionResult(
    val expandedKeywords: List<ExpandedKeyword>,
    val tokenUsage: TokenUsage
)
