package me.rgunny.kachi.ai.domain.llm

/**
 * 모델별 요청 옵션.
 *
 * 모든 모델이 모든 값을 명시한다. 한 제공자만 값을 갖고 나머지는 null인 공용 필드를 두지 않는다.
 * 새 옵션이 생기면 필드가 하나 늘고, 모든 모델 상수가 그 값을 적어야 컴파일된다.
 */
data class LlmRequestOptions(
    val reasoningEffort: ReasoningEffort,
    val thinking: Thinking
)
