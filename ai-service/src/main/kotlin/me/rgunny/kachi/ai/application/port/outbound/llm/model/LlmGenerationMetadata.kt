package me.rgunny.kachi.ai.application.port.outbound.llm.model

import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage

/**
 * LLM 생성 결과의 출처와 사용량 메타데이터.
 *
 * 후보 순회로 어느 모델이 답할지 호출 전에는 모르므로 포트 속성이 아니라 응답에 포함해 전달한다.
 * [model]은 응답이 보고한 모델 이름이다. 요청한 code와 같지 않을 수 있다.
 */
data class LlmGenerationMetadata(
    val provider: LlmProvider,
    val model: String,
    val promptVersion: PromptVersion,
    val tokenUsage: TokenUsage
)
