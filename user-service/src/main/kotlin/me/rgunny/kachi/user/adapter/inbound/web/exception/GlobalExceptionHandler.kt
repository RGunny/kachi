package me.rgunny.kachi.user.adapter.inbound.web.exception

import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.user.adapter.inbound.web.response.ErrorResponse
import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.exception.DuplicateKeywordException
import me.rgunny.kachi.user.application.exception.InvalidTokenException
import me.rgunny.kachi.user.application.exception.KeywordAccessDeniedException
import me.rgunny.kachi.user.application.exception.KeywordNotFoundException
import me.rgunny.kachi.user.application.exception.UserNotFoundException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException::class)
    fun handleDuplicateEmail(exception: DuplicateEmailException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.DUPLICATE_EMAIL, exception.message)
    }

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(exception: UserNotFoundException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.USER_NOT_FOUND, exception.message)
    }

    @ExceptionHandler(InactiveUserException::class)
    fun handleInactiveUser(exception: InactiveUserException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.INACTIVE_USER, exception.message)
    }

    @ExceptionHandler(InvalidTokenException::class)
    fun handleInvalidToken(exception: InvalidTokenException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.INVALID_TOKEN, exception.message)
    }

    @ExceptionHandler(DuplicateKeywordException::class)
    fun handleDuplicateKeyword(exception: DuplicateKeywordException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.DUPLICATE_KEYWORD, exception.message)
    }

    @ExceptionHandler(KeywordNotFoundException::class)
    fun handleKeywordNotFound(exception: KeywordNotFoundException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.KEYWORD_NOT_FOUND, exception.message)
    }

    @ExceptionHandler(KeywordAccessDeniedException::class)
    fun handleKeywordAccessDenied(exception: KeywordAccessDeniedException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.KEYWORD_ACCESS_DENIED, exception.message)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val message = exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: "요청 값이 올바르지 않습니다"

        return error(ErrorCode.INVALID_REQUEST, message)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.INVALID_REQUEST, exception.message)
    }

    private fun error(
        code: ErrorCode,
        message: String?
    ): ResponseEntity<ApiResponse<Unit>> {
        return ResponseEntity.status(code.status).body(ApiResponse.failure(ErrorResponse.of(code, message)))
    }
}
