package me.rgunny.kachi.user.adapter.inbound.web.exception

import me.rgunny.kachi.user.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.user.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.user.adapter.inbound.web.response.ErrorResponse
import me.rgunny.kachi.user.application.exception.ChannelBindingNotActiveException
import me.rgunny.kachi.user.application.exception.ChannelBindingNotFoundException
import me.rgunny.kachi.user.application.exception.ChannelBindingRefNotFoundException
import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.exception.DuplicateSubscriptionException
import me.rgunny.kachi.user.application.exception.InvalidChannelAddressException
import me.rgunny.kachi.user.application.exception.InvalidTokenException
import me.rgunny.kachi.user.application.exception.LinkTokenInvalidException
import me.rgunny.kachi.user.application.exception.SubscriptionAccessDeniedException
import me.rgunny.kachi.user.application.exception.SubscriptionNotFoundException
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

    @ExceptionHandler(DuplicateSubscriptionException::class)
    fun handleDuplicateSubscription(exception: DuplicateSubscriptionException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.DUPLICATE_SUBSCRIPTION, exception.message)
    }

    @ExceptionHandler(SubscriptionNotFoundException::class)
    fun handleSubscriptionNotFound(exception: SubscriptionNotFoundException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.SUBSCRIPTION_NOT_FOUND, exception.message)
    }

    @ExceptionHandler(SubscriptionAccessDeniedException::class)
    fun handleSubscriptionAccessDenied(exception: SubscriptionAccessDeniedException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.SUBSCRIPTION_ACCESS_DENIED, exception.message)
    }

    @ExceptionHandler(ChannelBindingNotFoundException::class)
    fun handleChannelBindingNotFound(exception: ChannelBindingNotFoundException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.CHANNEL_BINDING_NOT_FOUND, exception.message)
    }

    @ExceptionHandler(ChannelBindingRefNotFoundException::class)
    fun handleChannelBindingRefNotFound(exception: ChannelBindingRefNotFoundException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.CHANNEL_BINDING_NOT_FOUND, exception.message)
    }

    @ExceptionHandler(ChannelBindingNotActiveException::class)
    fun handleChannelBindingNotActive(exception: ChannelBindingNotActiveException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.CHANNEL_BINDING_NOT_ACTIVE, exception.message)
    }

    @ExceptionHandler(InvalidChannelAddressException::class)
    fun handleInvalidChannelAddress(exception: InvalidChannelAddressException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.INVALID_CHANNEL_ADDRESS, exception.message)
    }

    @ExceptionHandler(LinkTokenInvalidException::class)
    fun handleLinkTokenInvalid(exception: LinkTokenInvalidException): ResponseEntity<ApiResponse<Unit>> {
        return error(ErrorCode.LINK_TOKEN_INVALID, exception.message)
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
