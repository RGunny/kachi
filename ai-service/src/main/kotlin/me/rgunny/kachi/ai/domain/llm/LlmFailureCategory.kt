package me.rgunny.kachi.ai.domain.llm

/**
 * LLM 실패의 성격.
 *
 * LLM은 200 OK를 주면서 JSON 계약을 어기는 실패가 흔하므로 INVALID_RESPONSE를 별도로 둔다.
 */
enum class LlmFailureCategory {
    TIMEOUT,
    RATE_LIMITED,
    TRANSIENT_ERROR,

    /** 우리 쪽에서 호출을 차단해 provider에 요청이 나가지 않았다. */
    UNAVAILABLE,

    VALIDATION_ERROR,
    AUTHORIZATION_ERROR,
    INVALID_RESPONSE,
    UNKNOWN
}
