package me.rgunny.kachi.ai.application.exception

/**
 * 활성 키워드를 확정하지 못한 실패.
 *
 * 요약 대상 자체를 정할 수 없는 상태이므로 실행을 진행시키지 않는다.
 */
class KeywordReaderException(
    errorCode: KeywordReaderErrorCode,
    detail: String? = null
) : AiException(
    errorCode = errorCode,
    message = messageOf(errorCode, detail)
)
