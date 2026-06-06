package me.rgunny.kachi.ai.adapter.`in`.web.response

data class ErrorResponse(
    val code: String,
    val message: String
) {
    companion object {

        fun of(code: ErrorCode, message: String?): ErrorResponse {
            return ErrorResponse(
                code = code.name,
                message = message ?: code.message
            )
        }
    }
}
