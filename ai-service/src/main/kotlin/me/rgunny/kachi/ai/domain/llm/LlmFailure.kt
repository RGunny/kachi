package me.rgunny.kachi.ai.domain.llm

/**
 * LLM provider 호출 실패를 원천/성격과 함께 보존하는 값.
 *
 * 이 값 하나로 세 가지 판단이 갈린다.
 * - 실행 기록에 남길 실패 원인
 * - 키워드 격리 카운트를 올릴 것인가([keywordBound])
 * - provider 서킷 브레이커에 실패로 기록할 것인가([fromActualCall]이면서 [retryable])
 *
 * 자세한 결정 배경은 docs/decisions/021-ai-service-llm-실패-분류와-provider-circuit-breaker.md 를 참고한다.
 */
data class LlmFailure(
    val code: LlmFailureCode,
    /** 실패를 낸 제공자. 호출이 나가기 전에 차단된 실패는 어느 제공자의 것도 아니므로 null이다. */
    val provider: LlmProvider?,
    val message: String = code.defaultMessage,
    val statusCode: Int? = null,
    val retryAfterMillis: Long? = null
) {
    init {
        require(message.isNotBlank()) { "LLM 실패 메시지는 빈 값일 수 없습니다" }
        require(retryAfterMillis == null || retryAfterMillis >= 0) { "Retry-After는 음수일 수 없습니다" }
    }

    /** 로그와 메시지에 쓰는 제공자 code. 호출이 나가지 않은 실패는 [NO_PROVIDER]다. */
    val providerCode: String
        get() = provider?.code ?: NO_PROVIDER

    /**
     * 원천과 분류는 [code]에서 파생한다. 분류 축이 코드와 어긋난 실패를 만들 수 없게 하려는 것이다.
     */
    val source: LlmFailureSource
        get() = code.source

    val category: LlmFailureCategory
        get() = code.category

    /**
     * 다음 tick이 같은 구간을 다시 처리하면 해소될 수 있는 실패인가.
     *
     * INVALID_RESPONSE와 VALIDATION_ERROR는 provider가 살아 있다는 증거이므로 여기 포함하지 않는다.
     */
    val retryable: Boolean
        get() = category in RETRYABLE_CATEGORIES

    /**
     * 실제로 provider를 호출해서 얻은 실패인가.
     *
     * 호출 전에 차단해서 만든 실패는 provider의 상태에 대해 아무것도 말해주지 않는다.
     * 차단은 다음 tick에 풀릴 수 있어 [retryable]에는 해당하지만, 재시도 가능 여부만으로 회로를 열면
     * 차단해서 만든 실패가 다시 회로를 여는 근거가 된다.
     */
    val fromActualCall: Boolean
        get() = source != LlmFailureSource.APPLICATION

    /**
     * 키워드에 책임을 물을 수 있는 실패인가.
     *
     * 격리는 재시도로 해결되지 않는 실패만 걷어내는 장치이므로, 재시도로 풀릴 실패는 카운트하지 않는다.
     */
    val keywordBound: Boolean
        get() = category in KEYWORD_BOUND_CATEGORIES

    companion object {
        const val NO_PROVIDER = "none"

        private val RETRYABLE_CATEGORIES = setOf(
            LlmFailureCategory.TIMEOUT,
            LlmFailureCategory.RATE_LIMITED,
            LlmFailureCategory.TRANSIENT_ERROR,
            LlmFailureCategory.UNAVAILABLE
        )
        private val KEYWORD_BOUND_CATEGORIES = setOf(
            LlmFailureCategory.INVALID_RESPONSE,
            LlmFailureCategory.VALIDATION_ERROR
        )
    }
}
