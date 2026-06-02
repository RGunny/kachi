package me.rgunny.kachi.collector.application.exception

enum class KeywordReaderErrorCode(
    val code: String,
    val message: String
) {
    USER_SERVICE_REQUEST_FAILED(
        code = "KEYWORD_READER_001",
        message = "user-service active keyword request failed"
    ),
    USER_SERVICE_RESPONSE_FAILED(
        code = "KEYWORD_READER_002",
        message = "user-service active keyword response was not successful"
    ),
    USER_SERVICE_RESPONSE_MISSING_DATA(
        code = "KEYWORD_READER_003",
        message = "user-service active keyword response data is missing"
    ),
    ACTIVE_KEYWORDS_EMPTY(
        code = "KEYWORD_READER_004",
        message = "active keywords are empty"
    )
}
