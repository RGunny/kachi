package me.rgunny.kachi.story.adapter.inbound.web.response

/**
 * 내부 API 오류 응답의 본문.
 */
data class ErrorResponse(
    val code: String,
    val message: String
) {
    companion object {

        fun of(code: ErrorCode, message: String? = null): ErrorResponse {
            return ErrorResponse(
                code = code.name,
                message = message ?: code.message
            )
        }
    }
}
