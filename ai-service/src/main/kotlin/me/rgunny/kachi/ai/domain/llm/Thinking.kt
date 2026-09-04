package me.rgunny.kachi.ai.domain.llm

/**
 * 추론(thinking) 단계를 별도 필드로 켜고 끄는 모델의 옵션.
 *
 * [OMIT]은 요청에 이 필드를 싣지 않는다는 뜻이다.
 */
enum class Thinking {
    OMIT,
    ENABLED,
    DISABLED
}
