package me.rgunny.kachi.story.application.exception

/**
 * 조립 유스케이스의 에러 코드.
 */
enum class StoryAssemblyErrorCode(
    override val code: String,
    override val message: String
) : StoryErrorCode {
    ASSEMBLY_CONFLICT_EXHAUSTED(
        code = "ASSEMBLY_CONFLICT_EXHAUSTED",
        message = "story 갱신 경합이 재시도 한도 안에 풀리지 않았습니다"
    )
}
