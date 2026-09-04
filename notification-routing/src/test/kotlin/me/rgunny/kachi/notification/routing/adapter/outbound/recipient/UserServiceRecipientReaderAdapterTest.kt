package me.rgunny.kachi.notification.routing.adapter.outbound.recipient

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.contract.NotificationChannel
import me.rgunny.kachi.notification.routing.application.port.outbound.recipient.model.Recipient
import me.rgunny.kachi.notification.routing.exception.routing.RecipientReaderException
import me.rgunny.kachi.notification.routing.exception.routing.RoutingErrorCode
import me.rgunny.kachi.notification.routing.support.CapturingExchangeFunction
import me.rgunny.kachi.notification.routing.support.RoutingTestFixture
import me.rgunny.kachi.notification.routing.support.jsonExchangeFunction
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@DisplayName("UserServiceRecipientReaderAdapter")
class UserServiceRecipientReaderAdapterTest {

    private val subscribersJson = """
        {
          "success": true,
          "data": [
            { "userId": "user-1", "channel": "SLACK" },
            { "userId": "user-1", "channel": "TELEGRAM" },
            { "userId": "user-2", "channel": "DISCORD" }
          ]
        }
    """.trimIndent()

    private val adminsJson = """
        {
          "success": true,
          "data": [
            { "userId": "admin-1", "channels": ["SLACK", "TELEGRAM"] },
            { "userId": "admin-2", "channels": ["DISCORD"] }
          ]
        }
    """.trimIndent()

    @Nested
    @DisplayName("구독자 조회")
    inner class Subscribers {

        @Test
        @DisplayName("구독 조회 경로에 keyword query로 호출한다")
        fun requestPath() = runBlocking {
            val exchange = CapturingExchangeFunction(subscribersJson)

            adapterOf(exchange).findSubscribers("space x")

            assertEquals(RoutingTestFixture.SUBSCRIPTIONS_PATH, exchange.request.url().path)
            assertEquals("keyword=space%20x", exchange.request.url().rawQuery)
        }

        @Test
        @DisplayName("응답을 Recipient로 옮기고 채널을 계약 채널로 짝짓는다")
        fun mapSubscribers() = runBlocking {
            val recipients = adapterOf(jsonExchangeFunction(subscribersJson)).findSubscribers("tesla")

            assertEquals(
                listOf(
                    Recipient("user-1", NotificationChannel.SLACK),
                    Recipient("user-1", NotificationChannel.TELEGRAM),
                    Recipient("user-2", NotificationChannel.DISCORD),
                ),
                recipients,
            )
        }

        @Test
        @DisplayName("빈 목록은 빈 리스트다")
        fun emptyList() = runBlocking {
            val recipients = adapterOf(jsonExchangeFunction("""{ "success": true, "data": [] }""")).findSubscribers("nobody")

            assertTrue(recipients.isEmpty())
        }

        @Test
        @DisplayName("모르는 채널은 USER_SERVICE_RESPONSE_INVALID다")
        fun rejectUnknownChannel() = runBlocking {
            val body = """{ "success": true, "data": [ { "userId": "user-1", "channel": "PIGEON" } ] }"""

            val exception = assertFailsWith<RecipientReaderException> {
                adapterOf(jsonExchangeFunction(body)).findSubscribers("tesla")
            }

            assertEquals(RoutingErrorCode.USER_SERVICE_RESPONSE_INVALID, exception.errorCode)
        }
    }

    @Nested
    @DisplayName("관리자 조회")
    inner class Admins {

        @Test
        @DisplayName("사용자 조회 경로에 role=ADMIN query로 호출한다")
        fun requestPath() = runBlocking {
            val exchange = CapturingExchangeFunction(adminsJson)

            adapterOf(exchange).findAdmins()

            assertEquals(RoutingTestFixture.USERS_PATH, exchange.request.url().path)
            assertEquals("role=ADMIN", exchange.request.url().rawQuery)
        }

        @Test
        @DisplayName("사용자마다 채널을 fan-out해 Recipient로 옮긴다")
        fun flattenChannels() = runBlocking {
            val recipients = adapterOf(jsonExchangeFunction(adminsJson)).findAdmins()

            assertEquals(
                listOf(
                    Recipient("admin-1", NotificationChannel.SLACK),
                    Recipient("admin-1", NotificationChannel.TELEGRAM),
                    Recipient("admin-2", NotificationChannel.DISCORD),
                ),
                recipients,
            )
        }

        @Test
        @DisplayName("관리자가 없으면 빈 리스트다")
        fun emptyList() = runBlocking {
            val recipients = adapterOf(jsonExchangeFunction("""{ "success": true, "data": [] }""")).findAdmins()

            assertTrue(recipients.isEmpty())
        }

        @Test
        @DisplayName("모르는 채널은 USER_SERVICE_RESPONSE_INVALID다")
        fun rejectUnknownChannel() = runBlocking {
            val body = """{ "success": true, "data": [ { "userId": "admin-1", "channels": ["PIGEON"] } ] }"""

            val exception = assertFailsWith<RecipientReaderException> {
                adapterOf(jsonExchangeFunction(body)).findAdmins()
            }

            assertEquals(RoutingErrorCode.USER_SERVICE_RESPONSE_INVALID, exception.errorCode)
        }
    }

    @Nested
    @DisplayName("실패 분류")
    inner class Failures {

        @Test
        @DisplayName("비-2xx 응답은 USER_SERVICE_REQUEST_FAILED다")
        fun rejectErrorStatus() = runBlocking {
            val exception = assertFailsWith<RecipientReaderException> {
                adapterOf(jsonExchangeFunction("""{ "success": false }""", HttpStatus.SERVICE_UNAVAILABLE)).findSubscribers("tesla")
            }

            assertEquals(RoutingErrorCode.USER_SERVICE_REQUEST_FAILED, exception.errorCode)
            assertTrue(exception.message!!.contains("status=503"))
        }

        @Test
        @DisplayName("success=false는 USER_SERVICE_RESPONSE_FAILED다")
        fun rejectFailureEnvelope() = runBlocking {
            val exception = assertFailsWith<RecipientReaderException> {
                adapterOf(jsonExchangeFunction("""{ "success": false, "data": null }""")).findAdmins()
            }

            assertEquals(RoutingErrorCode.USER_SERVICE_RESPONSE_FAILED, exception.errorCode)
        }

        @Test
        @DisplayName("data가 없으면 USER_SERVICE_RESPONSE_MISSING_DATA다")
        fun rejectMissingData() = runBlocking {
            val exception = assertFailsWith<RecipientReaderException> {
                adapterOf(jsonExchangeFunction("""{ "success": true }""")).findSubscribers("tesla")
            }

            assertEquals(RoutingErrorCode.USER_SERVICE_RESPONSE_MISSING_DATA, exception.errorCode)
        }

        @Test
        @DisplayName("timeout은 USER_SERVICE_REQUEST_FAILED다")
        fun rejectTimeout() = runBlocking {
            val adapter = adapterOf(
                jsonExchangeFunction(subscribersJson, delay = Duration.ofMillis(300)),
                timeout = Duration.ofMillis(50),
            )

            val exception = assertFailsWith<RecipientReaderException> { adapter.findSubscribers("tesla") }

            assertEquals(RoutingErrorCode.USER_SERVICE_REQUEST_FAILED, exception.errorCode)
        }
    }

    private fun adapterOf(
        exchange: ExchangeFunction,
        timeout: Duration = Duration.ofSeconds(3),
    ): UserServiceRecipientReaderAdapter {
        val properties = RoutingTestFixture.properties(timeout = timeout).userService
        return UserServiceRecipientReaderAdapter(
            webClient = WebClient.builder().baseUrl(properties.baseUrl).exchangeFunction(exchange).build(),
            properties = properties,
        )
    }
}
