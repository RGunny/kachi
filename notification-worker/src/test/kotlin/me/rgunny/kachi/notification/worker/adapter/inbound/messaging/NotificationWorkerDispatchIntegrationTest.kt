package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.worker.adapter.inbound.messaging.exception.RetryableDispatchMessageException
import me.rgunny.kachi.notification.worker.fake.FakeAcknowledgment
import me.rgunny.kachi.notification.worker.fixture.NotificationWorkerDispatchFixture
import me.rgunny.kachi.notification.worker.support.TestVendorResponse
import me.rgunny.kachi.notification.worker.support.TestVendorServer
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("NotificationWorker dispatch integration")
class NotificationWorkerDispatchIntegrationTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-17T00:00:00Z"), ZoneOffset.UTC)

    @Test
    @DisplayName("mock sender가 켜져 있어도 Slack/Discord/Telegram은 real sender로 라우팅하고 나머지 채널은 mock sender로 처리한다")
    fun routeRealSendersAndMockSenderTogether() {
        TestVendorServer().use { vendorServer ->
            val fixture = NotificationWorkerDispatchFixture(vendorServer, clock)

            val slack = fixture.publishedNotification(NotificationChannel.SLACK, "user-slack")
            val discord = fixture.publishedNotification(NotificationChannel.DISCORD, "user-discord")
            val telegram = fixture.publishedNotification(NotificationChannel.TELEGRAM, "telegram-chat")
            val email = fixture.publishedNotification(NotificationChannel.EMAIL, "rgunny@kachi.com")

            val slackAck = FakeAcknowledgment()
            val discordAck = FakeAcknowledgment()
            val telegramAck = FakeAcknowledgment()
            val emailAck = FakeAcknowledgment()

            fixture.listener.consume(fixture.payload(slack), slackAck)
            fixture.listener.consume(fixture.payload(discord), discordAck)
            fixture.listener.consume(fixture.payload(telegram), telegramAck)
            fixture.listener.consume(fixture.payload(email), emailAck)

            assertTrue(slackAck.acked)
            assertTrue(discordAck.acked)
            assertTrue(telegramAck.acked)
            assertTrue(emailAck.acked)
            assertEquals(NotificationStatus.SENT, fixture.persistence.require(slack.id).status)
            assertEquals(NotificationStatus.SENT, fixture.persistence.require(discord.id).status)
            assertEquals(NotificationStatus.SENT, fixture.persistence.require(telegram.id).status)
            assertEquals(NotificationStatus.SENT, fixture.persistence.require(email.id).status)
            assertContains(vendorServer.paths, "/slack")
            assertContains(vendorServer.paths, "/discord")
            assertContains(vendorServer.paths, "/bottelegram-token/sendMessage")
            assertEquals(3, vendorServer.paths.size)
        }
    }

    @Test
    @DisplayName("real sender가 retryable 실패를 반환하면 listener는 retry 예외를 던지고 ack하지 않는다")
    fun throwRetryExceptionWhenRealSenderReturnsRetryableFailure() {
        TestVendorServer(
            responses = mapOf(
                "/slack" to TestVendorResponse(
                    statusCode = 429,
                    headers = mapOf("Retry-After" to "3"),
                    body = "rate_limited",
                )
            )
        ).use { vendorServer ->
            val fixture = NotificationWorkerDispatchFixture(vendorServer, clock)
            val notification = fixture.publishedNotification(NotificationChannel.SLACK, "user-slack")
            val acknowledgment = FakeAcknowledgment()

            assertFailsWith<RetryableDispatchMessageException> {
                fixture.listener.consume(fixture.payload(notification), acknowledgment)
            }

            assertFalse(acknowledgment.acked)
            assertEquals(NotificationStatus.RETRY_WAIT, fixture.persistence.require(notification.id).status)
            assertEquals(listOf("notification:dispatch:${notification.id.id}"), fixture.deduplication.releasedKeys)
        }
    }

    @Test
    @DisplayName("real sender가 non-retryable 실패를 반환하면 listener는 ack하고 DEAD로 완료한다")
    fun ackWhenRealSenderReturnsNonRetryableFailure() {
        TestVendorServer(
            responses = mapOf(
                "/discord" to TestVendorResponse(statusCode = 401, body = """{"message":"unauthorized"}""")
            )
        ).use { vendorServer ->
            val fixture = NotificationWorkerDispatchFixture(vendorServer, clock)
            val notification = fixture.publishedNotification(NotificationChannel.DISCORD, "user-discord")
            val acknowledgment = FakeAcknowledgment()

            fixture.listener.consume(fixture.payload(notification), acknowledgment)

            assertTrue(acknowledgment.acked)
            assertEquals(NotificationStatus.DEAD, fixture.persistence.require(notification.id).status)
        }
    }
}
