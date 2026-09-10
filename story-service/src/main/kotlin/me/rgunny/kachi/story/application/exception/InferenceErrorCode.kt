package me.rgunny.kachi.story.application.exception

/**
 * 추론 서버 호출 층의 오류 코드.
 */
enum class InferenceErrorCode(
    override val code: String,
    override val message: String
) : StoryErrorCode {
    INFERENCE_CALL_FAILED(
        code = "INFERENCE_CALL_FAILED",
        message = "추론 서버 호출에 실패했습니다"
    )
}
