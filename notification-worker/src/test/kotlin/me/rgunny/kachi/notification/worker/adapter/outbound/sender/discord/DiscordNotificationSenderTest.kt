package me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.dto.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.SendNotificationResult
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.retry.FailureCategory
import me.rgunny.kachi.notification.retry.RetryFailureCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@DisplayName("DiscordNotificationSender")
class DiscordNotificationSenderTest {

    @Test
    @DisplayName("DISCORD 채널만 지원한다")
    fun supportsDiscordOnly() {
        val sender = senderOf(HttpStatus.NO_CONTENT)

        assertTrue(sender.supports(NotificationChannel.DISCORD))
        assertFalse(sender.supports(NotificationChannel.EMAIL))
    }

    @Test
    @DisplayName("2xx 응답은 성공으로 분류한다")
    fun success() = runBlocking {
        val exchange = CapturingExchangeFunction(HttpStatus.NO_CONTENT)
        val sender = senderOf(exchange)

        val result = sender.send(command())

        assertIs<SendNotificationResult.Success>(result)
        assertEquals("https://discord.test/api/webhooks/test", exchange.request.url().toString())
    }

    @Test
    @DisplayName("429 응답은 rate limit 실패로 분류하고 Retry-After를 millis로 변환한다")
    fun rateLimitedByRetryAfter() = runBlocking {
        val sender = senderOf(
            CapturingExchangeFunction(
                status = HttpStatus.TOO_MANY_REQUESTS,
                headers = mapOf(HttpHeaders.RETRY_AFTER to "7"),
            )
        )

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.RateLimited>(result).failure
        assertEquals(RetryFailureCode.VENDOR_RATE_LIMITED.code, failure.code)
        assertEquals(429, failure.statusCode)
        assertEquals(7_000, failure.retryAfterMillis)
    }

    @Test
    @DisplayName("Retry-After가 없으면 X-RateLimit-Reset-After 소수 초를 millis로 올림 변환한다")
    fun rateLimitedByResetAfter() = runBlocking {
        val sender = senderOf(
            CapturingExchangeFunction(
                status = HttpStatus.TOO_MANY_REQUESTS,
                headers = mapOf("X-RateLimit-Reset-After" to "1.234"),
            )
        )

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.RateLimited>(result).failure
        assertEquals(RetryFailureCode.VENDOR_RATE_LIMITED.code, failure.code)
        assertEquals(429, failure.statusCode)
        assertEquals(1_234, failure.retryAfterMillis)
    }

    @Test
    @DisplayName("5xx 응답은 transient failure로 분류한다")
    fun transientFailure() = runBlocking {
        val sender = senderOf(HttpStatus.INTERNAL_SERVER_ERROR)

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.TransientFailure>(result).failure
        assertEquals(RetryFailureCode.VENDOR_TRANSIENT_ERROR.code, failure.code)
        assertEquals(500, failure.statusCode)
    }

    @Test
    @DisplayName("일반 4xx 응답은 permanent validation failure로 분류한다")
    fun permanentFailure() = runBlocking {
        val sender = senderOf(HttpStatus.NOT_FOUND)

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.PermanentFailure>(result).failure
        assertEquals("DISCORD_WEBHOOK_PERMANENT_ERROR", failure.code)
        assertEquals(404, failure.statusCode)
        assertEquals(FailureCategory.VALIDATION_ERROR, failure.category)
    }

    @Test
    @DisplayName("401/403 응답은 permanent authorization failure로 분류한다")
    fun authorizationFailure() = runBlocking {
        val sender = senderOf(HttpStatus.FORBIDDEN)

        val result = sender.send(command())

        val failure = assertIs<SendNotificationResult.PermanentFailure>(result).failure
        assertEquals("DISCORD_WEBHOOK_PERMANENT_ERROR", failure.code)
        assertEquals(403, failure.statusCode)
        assertEquals(FailureCategory.AUTHORIZATION_ERROR, failure.category)
    }

    private fun senderOf(status: HttpStatus): DiscordNotificationSender {
        return senderOf(CapturingExchangeFunction(status))
    }

    private fun senderOf(exchangeFunction: ExchangeFunction): DiscordNotificationSender {
        return DiscordNotificationSender(
            webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build(),
            webhookUrl = "https://discord.test/api/webhooks/test",
        )
    }

    private fun command(): SendNotificationCommand {
        return SendNotificationCommand(
            notificationId = NotificationId.newId(),
            channel = NotificationChannel.DISCORD,
            recipient = "discord-webhook",
            message = "hello",
            idempotencyKey = "idempotency-key",
        )
    }

    private class CapturingExchangeFunction(
        private val status: HttpStatus,
        private val headers: Map<String, String> = emptyMap(),
    ) : ExchangeFunction {

        lateinit var request: ClientRequest

        override fun exchange(request: ClientRequest): Mono<ClientResponse> {
            this.request = request

            val responseBuilder = ClientResponse.create(status)
            headers.forEach { (name, value) -> responseBuilder.header(name, value) }
            return Mono.just(responseBuilder.build())
        }
    }
}
