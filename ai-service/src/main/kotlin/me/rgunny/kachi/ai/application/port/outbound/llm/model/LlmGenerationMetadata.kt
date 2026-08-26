package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage

/**
 * LLM 생성 결과의 출처와 사용량 메타데이터.
 *
 * provider routing이나 fallback이 생길 수 있으므로 provider port 속성이 아니라 응답에 포함해 전달한다.
 */
data class LlmGenerationMetadata(
    val provider: LlmProviderName,
    val model: LlmModelName,
    val promptVersion: PromptVersion,
    val tokenUsage: TokenUsage
)
