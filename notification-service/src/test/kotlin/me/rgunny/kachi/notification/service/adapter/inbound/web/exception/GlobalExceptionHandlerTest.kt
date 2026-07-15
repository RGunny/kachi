package me.rgunny.kachi.notification.service.adapter.inbound.web.exception

import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationOutboxId
import me.rgunny.kachi.notification.exception.NotificationDltMessageNotFoundException
import me.rgunny.kachi.notification.exception.NotificationOutboxNotFoundException
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ErrorCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.core.MethodParameter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    @DisplayName("validation 예외를 field errors와 timestamp를 포함한 ApiResponse로 변환한다")
    fun handleValidation() {
        val bindingResult = BeanPropertyBindingResult(Any(), "notificationRequest")
        bindingResult.addError(FieldError("notificationRequest", "requestId", "requestId는 필수입니다"))
        bindingResult.addError(FieldError("notificationRequest", "recipient", "recipient는 필수입니다"))
        val exception = MethodArgumentNotValidException(methodParameter(), bindingResult)

        val response = handler.handleValidation(exception)

        assertEquals(ErrorCode.INVALID_REQUEST.status, response.statusCode)
        val body = assertNotNull(response.body)
        val error = assertNotNull(body.error)
        assertEquals(false, body.success)
        assertEquals(ErrorCode.INVALID_REQUEST.name, error.code)
        assertEquals(ErrorCode.INVALID_REQUEST.message, error.message)
        assertEquals(2, error.errors.size)
        assertEquals("requestId", error.errors[0].field)
        assertEquals("requestId는 필수입니다", error.errors[0].message)
        assertNotNull(error.timestamp)
    }

    @Test
    @DisplayName("outbox not found 예외를 404로 변환한다")
    fun handleNotificationOutboxNotFound() {
        val response = handler.handleNotificationOutboxNotFound(
            NotificationOutboxNotFoundException(NotificationOutboxId.newId())
        )

        assertEquals(ErrorCode.NOT_FOUND.status, response.statusCode)
        val body = assertNotNull(response.body)
        val error = assertNotNull(body.error)
        assertEquals(false, body.success)
        assertEquals(ErrorCode.NOT_FOUND.name, error.code)
    }

    @Test
    @DisplayName("DLT message not found 예외를 404로 변환한다")
    fun handleNotificationDltMessageNotFound() {
        val response = handler.handleNotificationDltMessageNotFound(
            NotificationDltMessageNotFoundException(
                NotificationDltMessageId.fromOriginalRecord("notification.dispatch", 0, 100)
            )
        )

        assertEquals(ErrorCode.NOT_FOUND.status, response.statusCode)
        val body = assertNotNull(response.body)
        val error = assertNotNull(body.error)
        assertEquals(false, body.success)
        assertEquals(ErrorCode.NOT_FOUND.name, error.code)
    }

    private fun methodParameter(): MethodParameter {
        val method = this::class.java.getDeclaredMethod("dummy", String::class.java)
        return MethodParameter(method, 0)
    }

    @Suppress("unused")
    private fun dummy(value: String) {
    }
}
