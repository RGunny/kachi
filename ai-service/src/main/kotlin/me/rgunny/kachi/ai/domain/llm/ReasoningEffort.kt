package me.rgunny.kachi.ai.domain.llm

/**
 * 모델이 답하기 전에 쓰는 추론(thinking) 토큰의 양.
 *
 * [OMIT]은 요청에 이 필드를 싣지 않는다는 뜻이다. 어떤 철자로 실을지는 API 규격 adapter가 정한다.
 */
enum class ReasoningEffort {
    OMIT,
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    MAX
}
