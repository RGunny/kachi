package me.rgunny.kachi.ai.domain.llm

/**
 * ai-service가 정의한 표준 LLM 실패 코드.
 *
 * [LlmFailure]의 원천/성격이 여기서 파생되므로, 이 enum이 실패 분류의 유일한 기준이다.
 * provider 원문 코드는 우리 분류 체계와 섞지 않는다. 보존이 필요해지면 별도 필드로 추가한다.
 */
enum class LlmFailureCode(
    val code: String,
    val defaultMessage: String,
    val source: LlmFailureSource,
    val category: LlmFailureCategory
) {
    LLM_TIMEOUT(
        code = "LLM_TIMEOUT",
        defaultMessage = "llm provider timeout",
        source = LlmFailureSource.NETWORK,
        category = LlmFailureCategory.TIMEOUT
    ),
    LLM_RATE_LIMITED(
        code = "LLM_RATE_LIMITED",
        defaultMessage = "llm provider rate limited",
        source = LlmFailureSource.PROVIDER,
        category = LlmFailureCategory.RATE_LIMITED
    ),
    LLM_TRANSIENT_ERROR(
        code = "LLM_TRANSIENT_ERROR",
        defaultMessage = "llm provider transient error",
        source = LlmFailureSource.PROVIDER,
        category = LlmFailureCategory.TRANSIENT_ERROR
    ),
    LLM_NETWORK_ERROR(
        code = "LLM_NETWORK_ERROR",
        defaultMessage = "llm provider request failed",
        source = LlmFailureSource.NETWORK,
        category = LlmFailureCategory.TRANSIENT_ERROR
    ),
    LLM_CLIENT_ERROR(
        code = "LLM_CLIENT_ERROR",
        defaultMessage = "llm provider rejected the request",
        source = LlmFailureSource.PROVIDER,
        category = LlmFailureCategory.VALIDATION_ERROR
    ),
    LLM_AUTHORIZATION_ERROR(
        code = "LLM_AUTHORIZATION_ERROR",
        defaultMessage = "llm provider authorization failed",
        source = LlmFailureSource.PROVIDER,
        category = LlmFailureCategory.AUTHORIZATION_ERROR
    ),
    LLM_INVALID_RESPONSE(
        code = "LLM_INVALID_RESPONSE",
        defaultMessage = "llm provider response does not match the expected contract",
        source = LlmFailureSource.PROVIDER,
        category = LlmFailureCategory.INVALID_RESPONSE
    ),
    LLM_UNKNOWN_ERROR(
        code = "LLM_UNKNOWN_ERROR",
        defaultMessage = "llm provider unknown error",
        source = LlmFailureSource.PROVIDER,
        category = LlmFailureCategory.UNKNOWN
    )
}
