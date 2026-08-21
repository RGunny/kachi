package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion

/**
 * LLM 호출 전에 확정되어야 하는 뉴스 요약 중복 판별 metadata.
 */
data class LlmNewsSummaryPlan(
    val provider: LlmProviderName,
    val model: LlmModelName,
    val promptVersion: PromptVersion
)
