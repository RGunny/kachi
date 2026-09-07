package me.rgunny.kachi.ai.domain.llm

/**
 * ai-service가 정의한 표준 LLM 실패 코드.
 *
 * 코드마다 두 축을 갖는다. [attribution]은 책임이 어디 있는가, [transient]는 다음 tick이나 다음 후보에서 저절로 풀리는가다.
 * [LlmFailure]의 모든 판단은 이 두 축에서 파생되므로, 이 enum이 실패 분류의 유일한 기준이다.
 * provider 원문 코드는 우리 분류 체계와 섞지 않는다. 보존이 필요해지면 별도 필드로 추가한다.
 */
enum class LlmFailureCode(
    val code: String,
    val defaultMessage: String,
    val attribution: LlmFailureAttribution,
    val transient: Boolean
) {
    /** 404. 제공이 끝났거나 이름이 틀린 모델. 이 모델만 빼면 된다. */
    LLM_MODEL_NOT_FOUND(
        code = "LLM_MODEL_NOT_FOUND",
        defaultMessage = "llm model not found",
        attribution = LlmFailureAttribution.MODEL,
        transient = false
    ),

    /** 400·413·422. 요청 본문이 거부됐다. */
    LLM_REQUEST_REJECTED(
        code = "LLM_REQUEST_REJECTED",
        defaultMessage = "llm provider rejected the request",
        attribution = LlmFailureAttribution.INPUT,
        transient = false
    ),

    /** 401. */
    LLM_UNAUTHORIZED(
        code = "LLM_UNAUTHORIZED",
        defaultMessage = "llm provider authentication failed",
        attribution = LlmFailureAttribution.PROVIDER,
        transient = false
    ),

    /** 402. */
    LLM_PAYMENT_REQUIRED(
        code = "LLM_PAYMENT_REQUIRED",
        defaultMessage = "llm provider requires payment",
        attribution = LlmFailureAttribution.PROVIDER,
        transient = false
    ),

    /** 403. 권한이나 일일 한도. */
    LLM_FORBIDDEN(
        code = "LLM_FORBIDDEN",
        defaultMessage = "llm provider forbade the request",
        attribution = LlmFailureAttribution.PROVIDER,
        transient = false
    ),

    /** 429. */
    LLM_RATE_LIMITED(
        code = "LLM_RATE_LIMITED",
        defaultMessage = "llm provider rate limited",
        attribution = LlmFailureAttribution.PROVIDER,
        transient = true
    ),

    /** 5xx. */
    LLM_SERVER_ERROR(
        code = "LLM_SERVER_ERROR",
        defaultMessage = "llm provider server error",
        attribution = LlmFailureAttribution.PROVIDER,
        transient = true
    ),
    LLM_TIMEOUT(
        code = "LLM_TIMEOUT",
        defaultMessage = "llm provider timeout",
        attribution = LlmFailureAttribution.PROVIDER,
        transient = true
    ),

    /** 연결 실패와 I/O 오류. */
    LLM_NETWORK_ERROR(
        code = "LLM_NETWORK_ERROR",
        defaultMessage = "llm provider request failed",
        attribution = LlmFailureAttribution.PROVIDER,
        transient = true
    ),

    /** 200이지만 JSON 계약을 어겼거나 content가 비었다. */
    LLM_INVALID_RESPONSE(
        code = "LLM_INVALID_RESPONSE",
        defaultMessage = "llm provider response does not match the expected contract",
        attribution = LlmFailureAttribution.INPUT,
        transient = false
    ),

    /** 서킷·cooldown·hold가 호출 전에 막았다. */
    LLM_NOT_PERMITTED(
        code = "LLM_NOT_PERMITTED",
        defaultMessage = "llm call not permitted",
        attribution = LlmFailureAttribution.NONE,
        transient = true
    ),
    LLM_UNKNOWN_ERROR(
        code = "LLM_UNKNOWN_ERROR",
        defaultMessage = "llm provider unknown error",
        attribution = LlmFailureAttribution.PROVIDER,
        transient = true
    )
}
