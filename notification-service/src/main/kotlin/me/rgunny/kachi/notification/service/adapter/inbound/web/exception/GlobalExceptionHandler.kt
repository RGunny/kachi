package me.rgunny.kachi.notification.service.adapter.inbound.web.exception

import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ErrorResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val message = exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: ErrorCode.INVALID_REQUEST.message

        return error(ErrorCode.INVALID_REQUEST, message)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.INVALID_REQUEST, exception.message)
    }

    private fun error(
        code: ErrorCode,
        message: String?,
    ): ResponseEntity<ApiResponse<Unit>> {
        return ResponseEntity.status(code.status)
            .body(ApiResponse.failure(ErrorResponse.of(code, message)))
    }
}
