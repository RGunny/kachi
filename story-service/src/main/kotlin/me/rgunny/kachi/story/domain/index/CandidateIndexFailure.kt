package me.rgunny.kachi.story.domain.index

/**
 * 벡터 색인 호출 실패를 책임·지속 두 축과 함께 보존하는 값.
 */
data class CandidateIndexFailure(
    val code: CandidateIndexFailureCode,
    val message: String = code.defaultMessage,
    val grpcStatus: String? = null
) {
    init {
        require(message.isNotBlank()) { "색인 실패 메시지는 빈 값일 수 없습니다" }
    }

    val attribution: CandidateIndexFailureAttribution
        get() = code.attribution

    /** 다음 호출에서 저절로 풀리는가. */
    val transient: Boolean
        get() = code.transient
}
