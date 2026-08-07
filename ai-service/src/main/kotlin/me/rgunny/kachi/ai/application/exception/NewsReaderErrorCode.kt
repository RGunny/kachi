package me.rgunny.kachi.ai.application.exception

enum class NewsReaderErrorCode(
    override val code: String,
    override val message: String
) : AiErrorCode {
    COLLECTOR_SERVICE_REQUEST_FAILED(
        code = "NEWS_READER_REQUEST_FAILED",
        message = "collector-service news request failed"
    ),
    COLLECTOR_SERVICE_RESPONSE_FAILED(
        code = "NEWS_READER_RESPONSE_FAILED",
        message = "collector-service news response was not successful"
    ),
    COLLECTOR_SERVICE_RESPONSE_MISSING_DATA(
        code = "NEWS_READER_RESPONSE_MISSING_DATA",
        message = "collector-service news response data is missing"
    )
}
