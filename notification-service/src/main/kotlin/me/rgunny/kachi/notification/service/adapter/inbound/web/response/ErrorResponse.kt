package me.rgunny.kachi.notification.service.adapter.inbound.web.response

data class ErrorResponse(
    val code: String,
    val message: String,
) {
    companion object {
        fun of(code: ErrorCode, message: String? = null): ErrorResponse {
            return ErrorResponse(
                code = code.name,
                message = message ?: code.message,
            )
        }
    }
}
