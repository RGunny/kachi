package me.rgunny.kachi.collector.application.exception

/**
 * 수집 대상 키워드를 확정하지 못한 실패.
 *
 * 수집 대상 자체를 정할 수 없는 상태이므로 실행을 진행시키지 않는다.
 */
class KeywordReaderException(
    errorCode: KeywordReaderErrorCode,
    detail: String? = null
) : CollectorException(
    errorCode = errorCode,
    message = messageOf(errorCode, detail)
)
