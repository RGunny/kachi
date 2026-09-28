package me.rgunny.kachi.ai.application.exception

/**
 * story 사본 처리 에러 코드.
 */
enum class AiStoryErrorCode(
    override val code: String,
    override val message: String
) : AiErrorCode {
    STORY_RECORD_CONFLICT_EXHAUSTED(
        code = "STORY_RECORD_CONFLICT_EXHAUSTED",
        message = "story 상태 경합이 재시도 상한을 넘었습니다"
    )
}
