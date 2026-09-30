package me.rgunny.kachi.story.application.exception

/**
 * 벡터 색인 호출 층의 에러 코드.
 */
enum class CandidateIndexErrorCode(
    override val code: String,
    override val message: String
) : StoryErrorCode {
    CANDIDATE_INDEX_CALL_FAILED(
        code = "CANDIDATE_INDEX_CALL_FAILED",
        message = "벡터 색인 호출에 실패했습니다"
    )
}
