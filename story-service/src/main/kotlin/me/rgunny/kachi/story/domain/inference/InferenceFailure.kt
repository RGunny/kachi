package me.rgunny.kachi.story.domain.inference

/**
 * 추론 서버 호출 실패를 책임·지속 두 축과 함께 보존하는 값.
 *
 * 두 축은 [code]에서 파생한다.
 */
data class InferenceFailure(
    val code: InferenceFailureCode,
    val target: InferenceTarget,
    val message: String = code.defaultMessage,
    val statusCode: Int? = null
) {
    init {
        require(message.isNotBlank()) { "추론 실패 메시지는 빈 값일 수 없습니다" }
    }

    val attribution: InferenceFailureAttribution
        get() = code.attribution

    /** 다음 호출에서 저절로 풀리는가. */
    val transient: Boolean
        get() = code.transient

    /** 실제로 서버를 호출해서 얻은 실패인가. */
    val fromActualCall: Boolean
        get() = attribution != InferenceFailureAttribution.NONE

    /**
     * 서킷 브레이커에 실패로 기록할 것인가.
     *
     * 실제 호출에서 나온 일시 실패만 표본이 된다.
     */
    val recordsInCircuit: Boolean
        get() = fromActualCall && transient
}
