package me.rgunny.kachi.story.domain.inference

/**
 * story-service가 정의한 표준 추론 실패 코드.
 *
 * 코드마다 두 축을 갖는다.
 * [attribution]은 책임이 어디 있는가, [transient]는 다음 호출에서 저절로 풀리는가다.
 */
enum class InferenceFailureCode(
    val code: String,
    val defaultMessage: String,
    val attribution: InferenceFailureAttribution,
    val transient: Boolean
) {
    /** 400. 빈 입력. */
    INFERENCE_INPUT_EMPTY(
        code = "INFERENCE_INPUT_EMPTY",
        defaultMessage = "inference input is empty",
        attribution = InferenceFailureAttribution.INPUT,
        transient = false
    ),

    /** 413. 요청 본문이 서버의 payload 상한을 넘었다. */
    INFERENCE_PAYLOAD_TOO_LARGE(
        code = "INFERENCE_PAYLOAD_TOO_LARGE",
        defaultMessage = "inference request payload is too large",
        attribution = InferenceFailureAttribution.INPUT,
        transient = false
    ),

    /** 422. 배치 크기 초과, 토큰 한도 초과, 토크나이저 오류. */
    INFERENCE_INPUT_INVALID(
        code = "INFERENCE_INPUT_INVALID",
        defaultMessage = "inference server rejected the input",
        attribution = InferenceFailureAttribution.INPUT,
        transient = false
    ),

    /** 424. 추론 자체가 실패했다. */
    INFERENCE_FAILED(
        code = "INFERENCE_FAILED",
        defaultMessage = "inference backend failed",
        attribution = InferenceFailureAttribution.SERVER,
        transient = true
    ),

    /** 429. 대기열이 찼다. */
    INFERENCE_OVERLOADED(
        code = "INFERENCE_OVERLOADED",
        defaultMessage = "inference server is overloaded",
        attribution = InferenceFailureAttribution.SERVER,
        transient = true
    ),

    /** 503. 서버가 스스로 unhealthy라고 답했다. */
    INFERENCE_UNHEALTHY(
        code = "INFERENCE_UNHEALTHY",
        defaultMessage = "inference server is unhealthy",
        attribution = InferenceFailureAttribution.SERVER,
        transient = true
    ),

    /** 그 밖의 5xx. */
    INFERENCE_SERVER_ERROR(
        code = "INFERENCE_SERVER_ERROR",
        defaultMessage = "inference server error",
        attribution = InferenceFailureAttribution.SERVER,
        transient = true
    ),
    INFERENCE_TIMEOUT(
        code = "INFERENCE_TIMEOUT",
        defaultMessage = "inference server timeout",
        attribution = InferenceFailureAttribution.SERVER,
        transient = true
    ),

    /** 연결 실패와 I/O 오류. */
    INFERENCE_NETWORK_ERROR(
        code = "INFERENCE_NETWORK_ERROR",
        defaultMessage = "inference server request failed",
        attribution = InferenceFailureAttribution.SERVER,
        transient = true
    ),

    /** 200이지만 차원·개수·값이 계약과 다르다. */
    INFERENCE_INVALID_RESPONSE(
        code = "INFERENCE_INVALID_RESPONSE",
        defaultMessage = "inference response does not match the expected contract",
        attribution = InferenceFailureAttribution.MODEL,
        transient = false
    ),

    /** 서킷이 호출 전에 막았다. */
    INFERENCE_NOT_PERMITTED(
        code = "INFERENCE_NOT_PERMITTED",
        defaultMessage = "inference call not permitted",
        attribution = InferenceFailureAttribution.NONE,
        transient = true
    ),

    /** 그 밖의 4xx. */
    INFERENCE_UNKNOWN_ERROR(
        code = "INFERENCE_UNKNOWN_ERROR",
        defaultMessage = "inference server unknown error",
        attribution = InferenceFailureAttribution.SERVER,
        transient = true
    )
}
