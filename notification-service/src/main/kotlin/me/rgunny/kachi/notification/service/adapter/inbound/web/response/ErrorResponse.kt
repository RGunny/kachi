package me.rgunny.kachi.notification.service.adapter.inbound.web.response

import java.time.Instant

data class ErrorResponse(
    val code: String,
    val message: String,
    val errors: List<FieldError>,
    val timestamp: Instant,
) {
    companion object {
        fun of(
            code: ErrorCode,
            message: String? = null,
            errors: List<FieldError> = emptyList(),
        ): ErrorResponse {
            return ErrorResponse(
                code = code.name,
                message = message ?: code.message,
                errors = errors,
                timestamp = Instant.now(),
            )
        }
    }
}
