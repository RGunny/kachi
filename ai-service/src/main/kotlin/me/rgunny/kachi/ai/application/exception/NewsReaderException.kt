package me.rgunny.kachi.ai.application.exception

/**
 * 요약 대상 뉴스를 확정하지 못한 실패.
 *
 * 뉴스 0건은 이 실패가 아니라 skip으로 다룬다(ADR 021).
 */
class NewsReaderException(
    errorCode: NewsReaderErrorCode,
    detail: String? = null
) : AiException(
    errorCode = errorCode,
    message = messageOf(errorCode, detail)
)
