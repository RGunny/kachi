package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword

/**
 * LLM 키워드 확장 응답
 */
data class LlmKeywordExpansionResult(
    val expandedKeywords: List<ExpandedKeyword>,
    val metadata: LlmGenerationMetadata
)
