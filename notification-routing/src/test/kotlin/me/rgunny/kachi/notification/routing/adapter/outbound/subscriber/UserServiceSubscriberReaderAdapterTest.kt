package me.rgunny.kachi.notification.routing.adapter.outbound.subscriber

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.routing.application.port.outbound.subscriber.model.Subscriber
import me.rgunny.kachi.notification.contract.NotificationChannel
import me.rgunny.kachi.notification.routing.exception.routing.RoutingErrorCode
import me.rgunny.kachi.notification.routing.exception.routing.SubscriberReaderException
import me.rgunny.kachi.notification.routing.support.CapturingExchangeFunction
import me.rgunny.kachi.notification.routing.support.RoutingTestFixture
import me.rgunny.kachi.notification.routing.support.jsonExchangeFunction
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("UserServiceSubscriberReaderAdapter")
class UserServiceSubscriberReaderAdapterTest {

    private val subscribersJson = """
        {
          "success": true,
          "data": [
            { "userId": "user-1", "channel": "SLACK", "recipientRef": "ref-1" },
            { "userId": "user-1", "channel": "TELEGRAM", "recipientRef": "ref-2" },
            { "userId": "user-2", "channel": "DISCORD", "recipientRef": "ref-3" }
          ]
        }
    """.trimIndent()

    @Test
    @DisplayName("구독 조회 경로에 keyword query로 호출한다")
    fun requestPath() = runBlocking {
        val exchange = CapturingExchangeFunction(subscribersJson)

        adapterOf(exchange).findSubscribers("space x")

        assertEquals(HttpStatus.OK.value(), 200)
        assertEquals(RoutingTestFixture.SUBSCRIPTIONS_PATH, exchange.request.url().path)
        assertEquals("keyword=space%20x", exchange.request.url().rawQuery)
    }

    @Test
    @DisplayName("응답을 Subscriber로 옮기고 채널을 core 채널로 짝짓는다")
    fun mapSubscribers() = runBlocking {
        val subscribers = adapterOf(jsonExchangeFunction(subscribersJson)).findSubscribers("tesla")

        assertEquals(
            listOf(
                Subscriber("user-1", NotificationChannel.SLACK, "ref-1"),
                Subscriber("user-1", NotificationChannel.TELEGRAM, "ref-2"),
                Subscriber("user-2", NotificationChannel.DISCORD, "ref-3"),
            ),
            subscribers,
        )
    }

    @Test
    @DisplayName("빈 목록은 빈 리스트다")
    fun emptyList() = runBlocking {
        val subscribers = adapterOf(jsonExchangeFunction("""{ "success": true, "data": [] }""")).findSubscribers("nobody")

        assertTrue(subscribers.isEmpty())
    }

    @Test
    @DisplayName("비-2xx 응답은 USER_SERVICE_REQUEST_FAILED다")
    fun rejectErrorStatus() = runBlocking {
        val exception = assertFailsWith<SubscriberReaderException> {
            adapterOf(jsonExchangeFunction("""{ "success": false }""", HttpStatus.SERVICE_UNAVAILABLE)).findSubscribers("tesla")
        }

        assertEquals(RoutingErrorCode.USER_SERVICE_REQUEST_FAILED, exception.errorCode)
        assertTrue(exception.message!!.contains("status=503"))
    }

    @Test
    @DisplayName("success=false는 USER_SERVICE_RESPONSE_FAILED다")
    fun rejectFailureEnvelope() = runBlocking {
        val exception = assertFailsWith<SubscriberReaderException> {
            adapterOf(jsonExchangeFunction("""{ "success": false, "data": null }""")).findSubscribers("tesla")
        }

        assertEquals(RoutingErrorCode.USER_SERVICE_RESPONSE_FAILED, exception.errorCode)
    }

    @Test
    @DisplayName("data가 없으면 USER_SERVICE_RESPONSE_MISSING_DATA다")
    fun rejectMissingData() = runBlocking {
        val exception = assertFailsWith<SubscriberReaderException> {
            adapterOf(jsonExchangeFunction("""{ "success": true }""")).findSubscribers("tesla")
        }

        assertEquals(RoutingErrorCode.USER_SERVICE_RESPONSE_MISSING_DATA, exception.errorCode)
    }

    @Test
    @DisplayName("모르는 채널은 USER_SERVICE_RESPONSE_INVALID다")
    fun rejectUnknownChannel() = runBlocking {
        val body = """{ "success": true, "data": [ { "userId": "user-1", "channel": "PIGEON", "recipientRef": "ref-1" } ] }"""

        val exception = assertFailsWith<SubscriberReaderException> {
            adapterOf(jsonExchangeFunction(body)).findSubscribers("tesla")
        }

        assertEquals(RoutingErrorCode.USER_SERVICE_RESPONSE_INVALID, exception.errorCode)
    }

    @Test
    @DisplayName("timeout은 USER_SERVICE_REQUEST_FAILED다")
    fun rejectTimeout() = runBlocking {
        val adapter = adapterOf(
            jsonExchangeFunction(subscribersJson, delay = Duration.ofMillis(300)),
            timeout = Duration.ofMillis(50),
        )

        val exception = assertFailsWith<SubscriberReaderException> { adapter.findSubscribers("tesla") }

        assertEquals(RoutingErrorCode.USER_SERVICE_REQUEST_FAILED, exception.errorCode)
    }

    private fun adapterOf(
        exchange: ExchangeFunction,
        timeout: Duration = Duration.ofSeconds(3),
    ): UserServiceSubscriberReaderAdapter {
        val properties = RoutingTestFixture.properties(timeout = timeout).userService
        return UserServiceSubscriberReaderAdapter(
            webClient = WebClient.builder().baseUrl(properties.baseUrl).exchangeFunction(exchange).build(),
            properties = properties,
        )
    }
}
