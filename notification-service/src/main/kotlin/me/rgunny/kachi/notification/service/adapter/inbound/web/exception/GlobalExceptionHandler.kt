package me.rgunny.kachi.notification.service.adapter.inbound.web.exception

import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ErrorResponse
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.FieldError
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindingResult
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebInputException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val fieldErrors = exception.bindingResult.toFieldErrors()

        log.debug("notification request validation failed: {}", fieldErrors)
        return error(
            code = ErrorCode.INVALID_REQUEST,
            message = ErrorCode.INVALID_REQUEST.message,
            errors = fieldErrors,
        )
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleWebExchangeBind(exception: WebExchangeBindException): ResponseEntity<ApiResponse<Unit>> {
        val fieldErrors = exception.bindingResult.toFieldErrors()

        log.debug("notification request binding failed: {}", fieldErrors)
        return error(
            code = ErrorCode.INVALID_REQUEST,
            message = ErrorCode.INVALID_REQUEST.message,
            errors = fieldErrors,
        )
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun handleServerWebInput(exception: ServerWebInputException): ResponseEntity<ApiResponse<Unit>> {
        log.debug("notification request body format failed: {}", exception.message)
        return error(ErrorCode.INVALID_FORMAT, ErrorCode.INVALID_FORMAT.message)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<ApiResponse<Unit>> {
        log.debug("notification domain input failed: {}", exception.message)
        return error(ErrorCode.INVALID_DOMAIN_INPUT, exception.message)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(exception: IllegalStateException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("notification domain invariant violation: {}", exception.message)
        return error(ErrorCode.DOMAIN_INVARIANT, exception.message)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnknown(exception: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.error("notification request failed by unhandled exception", exception)
        return error(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message)
    }

    private fun error(
        code: ErrorCode,
        message: String?,
        errors: List<FieldError> = emptyList(),
    ): ResponseEntity<ApiResponse<Unit>> {
        return ResponseEntity.status(code.status)
            .body(ApiResponse.failure(ErrorResponse.of(code, message, errors)))
    }

    private fun BindingResult.toFieldErrors(): List<FieldError> {
        return fieldErrors.map { fieldError ->
            FieldError(
                field = fieldError.field,
                message = fieldError.defaultMessage ?: ErrorCode.INVALID_REQUEST.message,
            )
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
