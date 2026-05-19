package me.rgunny.kachi.user.adapter.`in`.web.exception

import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.exception.DuplicateKeywordException
import me.rgunny.kachi.user.application.exception.KeywordNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateEmailException::class)
    fun handleDuplicateEmail(exception: DuplicateEmailException): ResponseEntity<ErrorResponse> {
        return error(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", exception.message)
    }

    @ExceptionHandler(DuplicateKeywordException::class)
    fun handleDuplicateKeyword(exception: DuplicateKeywordException): ResponseEntity<ErrorResponse> {
        return error(HttpStatus.CONFLICT, "DUPLICATE_KEYWORD", exception.message)
    }

    @ExceptionHandler(KeywordNotFoundException::class)
    fun handleKeywordNotFound(exception: KeywordNotFoundException): ResponseEntity<ErrorResponse> {
        return error(HttpStatus.NOT_FOUND, "KEYWORD_NOT_FOUND", exception.message)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: "요청 값이 올바르지 않습니다"

        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.message)
    }

    private fun error(
        status: HttpStatus,
        code: String,
        message: String?
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(status).body(
            ErrorResponse(
                code = code,
                message = message ?: status.reasonPhrase
            )
        )
    }
}
