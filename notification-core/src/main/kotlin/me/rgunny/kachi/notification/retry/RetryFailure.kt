package me.rgunny.kachi.notification.retry

data class RetryFailure(
    val code: String,
    val message: String,
    val source: FailureSource,
    val category: FailureCategory,
    val statusCode: Int? = null,
    val retryAfterMillis: Long? = null,
) {
    init {
        require(code.isNotBlank()) { "code must not be blank" }
        require(message.isNotBlank()) { "message must not be blank" }
        require(retryAfterMillis == null || retryAfterMillis >= 0) { "retryAfterMillis must not be negative" }
    }

    companion object {

        /**
         * notification-core에서 정의한 표준 실패 코드로 생성한다.
         */
        fun of(
            code: RetryFailureCode,
            message: String = code.defaultMessage,
            statusCode: Int? = null,
            retryAfterMillis: Long? = null,
        ): RetryFailure {
            return RetryFailure(
                code = code.code,
                message = message,
                source = code.source,
                category = code.category,
                statusCode = statusCode,
                retryAfterMillis = retryAfterMillis,
            )
        }

        /**
         * vendor나 broker가 내려준 동적 실패 코드를 보존해 생성한다.
         */
        fun external(
            code: String,
            message: String,
            source: FailureSource,
            category: FailureCategory,
            statusCode: Int? = null,
            retryAfterMillis: Long? = null,
        ): RetryFailure {
            return RetryFailure(
                code = code,
                message = message,
                source = source,
                category = category,
                statusCode = statusCode,
                retryAfterMillis = retryAfterMillis,
            )
        }
    }
}
