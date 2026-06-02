package me.rgunny.kachi.ai.application.port.out.llm

import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage

/**
 * LLM 생성 결과의 출처와 사용량 메타데이터
 */
data class LlmGenerationMetadata(
    val provider: LlmProviderName,
    val model: LlmModelName,
    val promptVersion: PromptVersion,
    val tokenUsage: TokenUsage
)
