package me.rgunny.kachi.story.application.exception

import me.rgunny.kachi.story.domain.index.CandidateIndexFailure

/**
 * 벡터 색인 호출 실패를 분류 결과와 함께 전달하는 예외.
 */
class CandidateIndexException(
    val failure: CandidateIndexFailure,
    cause: Throwable? = null
) : StoryException(
    errorCode = CandidateIndexErrorCode.CANDIDATE_INDEX_CALL_FAILED,
    message = "${failure.code.code} ${failure.message}" + (failure.grpcStatus?.let { " (grpc=$it)" } ?: ""),
    cause = cause
)
