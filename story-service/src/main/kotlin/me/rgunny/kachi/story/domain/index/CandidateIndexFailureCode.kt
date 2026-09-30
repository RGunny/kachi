package me.rgunny.kachi.story.domain.index

/**
 * story-service가 정의한 표준 벡터 색인 실패 코드.
 *
 * - [attribution] 책임이 어디 있는지
 * - [transient] 다음 호출에서 저절로 풀리는지
 */
enum class CandidateIndexFailureCode(
    val code: String,
    val defaultMessage: String,
    val attribution: CandidateIndexFailureAttribution,
    val transient: Boolean
) {
    /** UNAVAILABLE. 서버에 닿지 않는다. */
    INDEX_UNAVAILABLE(
        code = "INDEX_UNAVAILABLE",
        defaultMessage = "candidate index is unavailable",
        attribution = CandidateIndexFailureAttribution.INDEX,
        transient = true
    ),

    /** DEADLINE_EXCEEDED. */
    INDEX_TIMEOUT(
        code = "INDEX_TIMEOUT",
        defaultMessage = "candidate index timeout",
        attribution = CandidateIndexFailureAttribution.INDEX,
        transient = true
    ),

    /** RESOURCE_EXHAUSTED. */
    INDEX_OVERLOADED(
        code = "INDEX_OVERLOADED",
        defaultMessage = "candidate index is overloaded",
        attribution = CandidateIndexFailureAttribution.INDEX,
        transient = true
    ),

    /** INTERNAL, UNKNOWN, ABORTED. */
    INDEX_SERVER_ERROR(
        code = "INDEX_SERVER_ERROR",
        defaultMessage = "candidate index server error",
        attribution = CandidateIndexFailureAttribution.INDEX,
        transient = true
    ),

    /** NOT_FOUND. 컬렉션이 없다. */
    INDEX_COLLECTION_MISSING(
        code = "INDEX_COLLECTION_MISSING",
        defaultMessage = "candidate index collection is missing",
        attribution = CandidateIndexFailureAttribution.INDEX,
        transient = false
    ),

    /** INVALID_ARGUMENT. 벡터·필터·식별자를 거부했다. */
    INDEX_REQUEST_REJECTED(
        code = "INDEX_REQUEST_REJECTED",
        defaultMessage = "candidate index rejected the request",
        attribution = CandidateIndexFailureAttribution.INPUT,
        transient = false
    ),

    /** FAILED_PRECONDITION. */
    INDEX_PRECONDITION_FAILED(
        code = "INDEX_PRECONDITION_FAILED",
        defaultMessage = "candidate index precondition failed",
        attribution = CandidateIndexFailureAttribution.INDEX,
        transient = false
    ),

    /** UNAUTHENTICATED, PERMISSION_DENIED. */
    INDEX_UNAUTHORIZED(
        code = "INDEX_UNAUTHORIZED",
        defaultMessage = "candidate index rejected the credentials",
        attribution = CandidateIndexFailureAttribution.INDEX,
        transient = false
    ),

    /** 그 밖의 상태와 gRPC 밖의 예외. */
    INDEX_UNKNOWN_ERROR(
        code = "INDEX_UNKNOWN_ERROR",
        defaultMessage = "candidate index unknown error",
        attribution = CandidateIndexFailureAttribution.INDEX,
        transient = true
    )
}
