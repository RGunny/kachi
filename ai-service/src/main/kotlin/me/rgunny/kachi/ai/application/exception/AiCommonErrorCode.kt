package me.rgunny.kachi.ai.application.exception

/**
 * 특정 외부 연동에 속하지 않는 ai-service 공통 에러 코드.
 *
 * 연동 대상이 분명한 실패는 KeywordReaderErrorCode처럼 대상별 enum에 둔다.
 */
enum class AiCommonErrorCode(
    override val code: String,
    override val message: String
) : AiErrorCode {
    LLM_PROVIDER_CALL_FAILED(
        code = "LLM_PROVIDER_CALL_FAILED",
        message = "LLM provider 호출에 실패했습니다"
    )
}
