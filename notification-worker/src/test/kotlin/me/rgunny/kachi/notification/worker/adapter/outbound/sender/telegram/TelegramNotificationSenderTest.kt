package me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationResult
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.retry.FailureCategory
import me.rgunny.kachi.notification.retry.RetryFailureCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@DisplayName("TelegramNotificationSender")
class TelegramNotificationSenderTest {

    @Test
    @DisplayName("TELEGRAM 채널만 지원한다")
    fun supportsTelegramOnly() {
        val sender = senderOf(HttpStatus.OK, OK_BODY)

        assertTrue(sender.supports(NotificationChannel.TELEGRAM))
        assertFalse(sender.supports(NotificationChannel.EMAIL))
    }

    @Test
    @DisplayName("2xx 응답이고 body ok=true이면 성공으로 분류한다")
    fun success() = runBlocking {
        val exchange = CapturingExchangeFunction(HttpStatus.OK, OK_BODY)
        val sender = senderOf(exchange)

        val result = sender.send(command())

        assertIs<SendNotificationResult.Success>(result)
        assertEquals("https://api.telegram.test/bottelegram-bot-token/sendMessage", exchange.request.url().toString())
    }

    @Test
    @DisplayName("429 응답은 body parameters.retry_after를 millis로 변환해 rate limit 실패로 분류한다")
    fun rateLimited() = runBlocking {
        val sender = senderOf(
            CapturingExchangeFunction(
                status = HttpStatus.TOO_MANY_REQUESTS,
                body = """{"ok":false,"error_code":429,"description":"Too Many Requests","parameters":{"retry_after":7}}""",
            )
        )

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.RateLimited>(result).failure
        assertEquals(RetryFailureCode.VENDOR_RATE_LIMITED.code, failure.code)
        assertEquals(429, failure.statusCode)
        assertEquals(7_000, failure.retryAfterMillis)
    }

    @Test
    @DisplayName("5xx 응답은 transient failure로 분류한다")
    fun transientFailure() = runBlocking {
        val sender = senderOf(
            HttpStatus.INTERNAL_SERVER_ERROR,
            """{"ok":false,"error_code":500,"description":"Internal Server Error"}""",
        )

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.TransientFailure>(result).failure
        assertEquals(RetryFailureCode.VENDOR_TRANSIENT_ERROR.code, failure.code)
        assertEquals(500, failure.statusCode)
    }

    @Test
    @DisplayName("일반 4xx 응답은 permanent validation failure로 분류한다")
    fun permanentFailure() = runBlocking {
        val sender = senderOf(
            HttpStatus.BAD_REQUEST,
            """{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}""",
        )

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.PermanentFailure>(result).failure
        assertEquals("TELEGRAM_SEND_MESSAGE_PERMANENT_ERROR", failure.code)
        assertEquals(400, failure.statusCode)
        assertEquals(FailureCategory.VALIDATION_ERROR, failure.category)
    }

    @Test
    @DisplayName("401/403 응답은 permanent authorization failure로 분류한다")
    fun authorizationFailure() = runBlocking {
        val sender = senderOf(
            HttpStatus.UNAUTHORIZED,
            """{"ok":false,"error_code":401,"description":"Unauthorized"}""",
        )

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.PermanentFailure>(result).failure
        assertEquals("TELEGRAM_SEND_MESSAGE_PERMANENT_ERROR", failure.code)
        assertEquals(401, failure.statusCode)
        assertEquals(FailureCategory.AUTHORIZATION_ERROR, failure.category)
    }

    @Test
    @DisplayName("수신 주소가 비어 있으면 vendor 호출 없이 permanent validation failure로 분류한다")
    fun blankRecipient() = runBlocking {
        val sender = senderOf(HttpStatus.OK, OK_BODY)

        val result = sender.send(command(address = ""))

        val failure = assertIs<SendNotificationResult.PermanentFailure>(result).failure
        assertEquals("TELEGRAM_INVALID_RECIPIENT", failure.code)
        assertEquals(FailureCategory.VALIDATION_ERROR, failure.category)
    }

    @Test
    @DisplayName("message가 Telegram text 길이 제한을 넘으면 permanent validation failure로 분류한다")
    fun messageTooLong() = runBlocking {
        val sender = senderOf(HttpStatus.OK, OK_BODY)

        val result = sender.send(command(message = "a".repeat(4097)))

        val failure = assertIs<SendNotificationResult.PermanentFailure>(result).failure
        assertEquals("TELEGRAM_MESSAGE_TOO_LONG", failure.code)
        assertEquals(FailureCategory.VALIDATION_ERROR, failure.category)
    }

    private fun senderOf(status: HttpStatus, body: String): TelegramNotificationSender {
        return senderOf(CapturingExchangeFunction(status, body))
    }

    private fun senderOf(exchangeFunction: ExchangeFunction): TelegramNotificationSender {
        return TelegramNotificationSender(
            client = TelegramSendMessageClient(
                webClient = WebClient.builder()
                    .baseUrl("https://api.telegram.test")
                    .exchangeFunction(exchangeFunction)
                    .build(),
                botToken = "telegram-bot-token",
                sendMessagePath = "/sendMessage",
            ),
        )
    }

    private fun command(
        address: String = "123456789",
        message: String = "hello",
    ): SendNotificationCommand {
        return SendNotificationCommand(
            notificationId = NotificationId.newId(),
            channel = NotificationChannel.TELEGRAM,
            address = address,
            message = message,
            idempotencyKey = "idempotency-key",
        )
    }

    private class CapturingExchangeFunction(
        private val status: HttpStatus,
        private val body: String,
    ) : ExchangeFunction {

        lateinit var request: ClientRequest

        override fun exchange(request: ClientRequest): Mono<ClientResponse> {
            this.request = request

            return Mono.just(
                ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build()
            )
        }
    }

    private companion object {
        const val OK_BODY = """{"ok":true,"result":{"message_id":1}}"""
    }
}
