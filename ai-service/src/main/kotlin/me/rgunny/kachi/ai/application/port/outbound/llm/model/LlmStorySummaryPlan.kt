package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.PromptVersion

/**
 * LLM 호출 전에 확정되어야 하는 story 요약 선조회 키([promptVersion])와 첫 호출 후보([provider])의 묶음(실제 model은 호출 결과 metadata에 기록).
 */
data class LlmStorySummaryPlan(
    val provider: LlmProvider,
    val promptVersion: PromptVersion
)
