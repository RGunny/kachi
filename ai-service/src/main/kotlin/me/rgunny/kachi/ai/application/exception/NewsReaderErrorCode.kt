package me.rgunny.kachi.ai.application.exception

enum class NewsReaderErrorCode(
    val code: String,
    val message: String
) {
    COLLECTOR_SERVICE_REQUEST_FAILED(
        code = "NEWS_READER_001",
        message = "collector-service news request failed"
    ),
    COLLECTOR_SERVICE_RESPONSE_FAILED(
        code = "NEWS_READER_002",
        message = "collector-service news response was not successful"
    ),
    COLLECTOR_SERVICE_RESPONSE_MISSING_DATA(
        code = "NEWS_READER_003",
        message = "collector-service news response data is missing"
    )
}
