package me.rgunny.kachi.collector.application.exception

enum class KeywordReaderErrorCode(
    override val code: String,
    override val message: String
) : CollectorErrorCode {
    USER_SERVICE_REQUEST_FAILED(
        code = "KEYWORD_READER_REQUEST_FAILED",
        message = "user-service active keyword request failed"
    ),
    USER_SERVICE_RESPONSE_FAILED(
        code = "KEYWORD_READER_RESPONSE_FAILED",
        message = "user-service active keyword response was not successful"
    ),
    USER_SERVICE_RESPONSE_MISSING_DATA(
        code = "KEYWORD_READER_RESPONSE_MISSING_DATA",
        message = "user-service active keyword response data is missing"
    ),
    ACTIVE_KEYWORDS_EMPTY(
        code = "KEYWORD_READER_ACTIVE_KEYWORDS_EMPTY",
        message = "active keywords are empty"
    )
}
