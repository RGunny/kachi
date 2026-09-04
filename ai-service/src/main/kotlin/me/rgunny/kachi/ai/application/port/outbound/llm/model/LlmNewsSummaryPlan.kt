package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.PromptVersion

/**
 * LLM 호출 전에 확정되어야 하는 뉴스 요약 선조회 키([promptVersion])와 첫 호출 후보([provider]).
 *
 * 실제 요약에 쓰인 model은 호출 결과 metadata가 갖는다.
 */
data class LlmNewsSummaryPlan(
    val provider: LlmProvider,
    val promptVersion: PromptVersion
)
