package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.AvailableRecipient
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.RecipientUnavailableReason
import me.rgunny.kachi.notification.application.port.outbound.recipient.model.UnavailableRecipient
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.exception.recipient.RecipientResolveException
import me.rgunny.kachi.notification.domain.retry.RetryFailureCode
import me.rgunny.kachi.notification.worker.support.CapturingJsonExchangeFunction
import me.rgunny.kachi.notification.worker.support.jsonExchangeFunction
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

@DisplayName("UserServiceRecipientResolver")
class UserServiceRecipientResolverTest {

    @Test
    @DisplayName("user-service 채널 바인딩 조회 경로로 GET 요청을 보낸다")
    fun requestPath() = runBlocking {
        val exchange = CapturingJsonExchangeFunction(activeBody(ADDRESS))
        val resolver = resolverOf(exchange)

        resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK)

        assertEquals(HttpMethod.GET, exchange.request.method())
        assertEquals(
            "http://user-service.test/api/v1/internal/users/$RECIPIENT_ID/channel-bindings/SLACK",
            exchange.request.url().toString(),
        )
    }

    @Test
    @DisplayName("ACTIVE 바인딩에 주소가 있고 채널이 일치하면 Available이다")
    fun active() = runBlocking {
        val resolver = resolverOf(jsonExchangeFunction(activeBody(ADDRESS)))

        val resolved = resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK)

        assertEquals(AvailableRecipient(ADDRESS), resolved)
    }

    @Test
    @DisplayName("PENDING 바인딩은 Unavailable(PENDING)이다")
    fun pending() = runBlocking {
        val resolver = resolverOf(jsonExchangeFunction(body("SLACK", "PENDING", null)))

        assertEquals(UnavailableRecipient(RecipientUnavailableReason.PENDING), resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK))
    }

    @Test
    @DisplayName("REVOKED 바인딩은 Unavailable(REVOKED)이다")
    fun revoked() = runBlocking {
        val resolver = resolverOf(jsonExchangeFunction(body("SLACK", "REVOKED", null)))

        assertEquals(UnavailableRecipient(RecipientUnavailableReason.REVOKED), resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK))
    }

    @Test
    @DisplayName("ACTIVE인데 주소가 없으면 Unavailable(ADDRESS_MISSING)이다")
    fun addressMissing() = runBlocking {
        val resolver = resolverOf(jsonExchangeFunction(body("SLACK", "ACTIVE", null)))

        assertEquals(UnavailableRecipient(RecipientUnavailableReason.ADDRESS_MISSING), resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK))
    }

    @Test
    @DisplayName("응답 채널이 알림 채널과 다르면 Unavailable(CHANNEL_MISMATCH)이다")
    fun channelMismatch() = runBlocking {
        val resolver = resolverOf(jsonExchangeFunction(body("DISCORD", "ACTIVE", ADDRESS)))

        assertEquals(UnavailableRecipient(RecipientUnavailableReason.CHANNEL_MISMATCH), resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK))
    }

    @Test
    @DisplayName("404는 Unavailable(NOT_FOUND)이다")
    fun notFound() = runBlocking {
        val resolver = resolverOf(jsonExchangeFunction(errorBody("CHANNEL_BINDING_NOT_FOUND"), HttpStatus.NOT_FOUND))

        assertEquals(UnavailableRecipient(RecipientUnavailableReason.NOT_FOUND), resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK))
    }

    @Test
    @DisplayName("recipientId가 UUID 형식이 아니면 요청 없이 Unavailable(NOT_FOUND)이다")
    fun nonUuidRecipientId() = runBlocking {
        val exchange = CapturingJsonExchangeFunction(activeBody(ADDRESS))
        val resolver = resolverOf(exchange)

        val resolved = resolver.resolve("user-1", NotificationChannel.SLACK)

        assertEquals(UnavailableRecipient(RecipientUnavailableReason.NOT_FOUND), resolved)
        assertEquals(0, exchange.exchangeCount)
    }

    @Test
    @DisplayName("500은 RECIPIENT_RESOLVE_FAILED 예외다")
    fun serverError() {
        val resolver = resolverOf(jsonExchangeFunction(errorBody("INTERNAL"), HttpStatus.INTERNAL_SERVER_ERROR))

        val exception = assertFailsWith<RecipientResolveException> {
            runBlocking { resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK) }
        }

        assertEquals(RetryFailureCode.RECIPIENT_RESOLVE_FAILED.code, exception.failure.code)
        assertEquals(500, exception.failure.statusCode)
    }

    @Test
    @DisplayName("success=false 응답은 RECIPIENT_RESOLVE_FAILED 예외다")
    fun successFalse() {
        val resolver = resolverOf(jsonExchangeFunction("""{"success":false,"data":null}"""))

        val exception = assertFailsWith<RecipientResolveException> {
            runBlocking { resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK) }
        }

        assertEquals(RetryFailureCode.RECIPIENT_RESOLVE_FAILED.code, exception.failure.code)
    }

    @Test
    @DisplayName("data가 없는 응답은 RECIPIENT_RESOLVE_FAILED 예외다")
    fun dataMissing() {
        val resolver = resolverOf(jsonExchangeFunction("""{"success":true,"data":null}"""))

        val exception = assertFailsWith<RecipientResolveException> {
            runBlocking { resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK) }
        }

        assertEquals(RetryFailureCode.RECIPIENT_RESOLVE_FAILED.code, exception.failure.code)
    }

    @Test
    @DisplayName("본문을 파싱할 수 없으면 RECIPIENT_RESOLVE_FAILED 예외다")
    fun malformedBody() {
        val resolver = resolverOf(jsonExchangeFunction("not-json"))

        val exception = assertFailsWith<RecipientResolveException> {
            runBlocking { resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK) }
        }

        assertEquals(RetryFailureCode.RECIPIENT_RESOLVE_FAILED.code, exception.failure.code)
    }

    @Test
    @DisplayName("모르는 status는 RECIPIENT_RESOLVE_FAILED 예외다")
    fun unknownStatus() {
        val resolver = resolverOf(jsonExchangeFunction(body("SLACK", "BOUNCED", null)))

        val exception = assertFailsWith<RecipientResolveException> {
            runBlocking { resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK) }
        }

        assertEquals(RetryFailureCode.RECIPIENT_RESOLVE_FAILED.code, exception.failure.code)
    }

    @Test
    @DisplayName("응답이 timeout보다 늦으면 RECIPIENT_RESOLVE_TIMEOUT 예외다")
    fun timeout() {
        val resolver = resolverOf(
            jsonExchangeFunction(activeBody(ADDRESS), delay = Duration.ofMillis(300)),
            timeout = Duration.ofMillis(50),
        )

        val exception = assertFailsWith<RecipientResolveException> {
            runBlocking { resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK) }
        }

        assertEquals(RetryFailureCode.RECIPIENT_RESOLVE_TIMEOUT.code, exception.failure.code)
    }

    @Test
    @DisplayName("예외 메시지에 응답 본문의 주소가 들어가지 않는다")
    fun exceptionMessageDoesNotContainAddress() {
        val resolver = resolverOf(
            jsonExchangeFunction("""{"success":false,"data":{"channel":"SLACK","status":"ACTIVE","address":"$ADDRESS"}}"""),
        )

        val exception = assertFailsWith<RecipientResolveException> {
            runBlocking { resolver.resolve(RECIPIENT_ID, NotificationChannel.SLACK) }
        }

        assertFalse(exception.message!!.contains(ADDRESS))
        assertIs<RecipientResolveException>(exception)
    }

    private fun resolverOf(
        exchangeFunction: ExchangeFunction,
        timeout: Duration = Duration.ofSeconds(3),
    ): UserServiceRecipientResolver {
        return UserServiceRecipientResolver(
            webClient = WebClient.builder()
                .baseUrl("http://user-service.test")
                .exchangeFunction(exchangeFunction)
                .build(),
            channelBindingPath = "/api/v1/internal/users/{userId}/channel-bindings/{channel}",
            timeout = timeout,
        )
    }

    private fun activeBody(address: String): String = body("SLACK", "ACTIVE", address)

    private fun body(channel: String, status: String, address: String?): String {
        val addressJson = address?.let { "\"$it\"" } ?: "null"
        return """{"success":true,"data":{"channel":"$channel","status":"$status","address":$addressJson},"error":null}"""
    }

    private fun errorBody(code: String): String {
        return """{"success":false,"data":null,"error":{"code":"$code","message":"error"}}"""
    }

    private companion object {
        const val RECIPIENT_ID = "0d4a8b0e-4f0a-4a3e-9a5f-1c2b3d4e5f60"
        const val ADDRESS = "https://hooks.slack.test/services/T000/B000/XXXX"
    }
}
