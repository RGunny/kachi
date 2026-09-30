package me.rgunny.kachi.notification.worker.adapter.inbound.messaging

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationStatus
import me.rgunny.kachi.notification.worker.adapter.inbound.messaging.exception.RetryableDispatchMessageException
import me.rgunny.kachi.notification.worker.adapter.monitoring.NotificationWorkerMetricContract
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
    @DisplayName("조회한 주소로 Slack/Discord/Telegram은 real sender가 보내고 나머지 채널은 mock sender가 처리한다")
    fun routeRealSendersAndMockSenderTogether() {
        TestVendorServer().use { vendorServer ->
            val fixture = NotificationWorkerDispatchFixture(vendorServer, clock)

            val slack = fixture.publishedNotification(NotificationChannel.SLACK)
            val discord = fixture.publishedNotification(NotificationChannel.DISCORD)
            val telegram = fixture.publishedNotification(NotificationChannel.TELEGRAM)
            val email = fixture.publishedNotification(NotificationChannel.EMAIL)
            fixture.binding(slack.recipientId, NotificationChannel.SLACK, "ACTIVE", "${vendorServer.baseUrl}/slack")
            fixture.binding(discord.recipientId, NotificationChannel.DISCORD, "ACTIVE", "${vendorServer.baseUrl}/discord")
            fixture.binding(telegram.recipientId, NotificationChannel.TELEGRAM, "ACTIVE", "123456789")
            fixture.binding(email.recipientId, NotificationChannel.EMAIL, "ACTIVE", "rgunny@kachi.com")

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
            assertContains(vendorServer.bodies.getValue("/bottelegram-token/sendMessage").single(), "\"chat_id\":\"123456789\"")
            assertEquals(3, vendorServer.paths.count { !it.startsWith("/api/v1/internal/") })
            assertEquals(4, vendorServer.paths.count { it.startsWith("/api/v1/internal/") })
        }
    }

    @Test
    @DisplayName("바인딩이 해지된 수신자는 vendor를 부르지 않고 SUPPRESSED로 끝내며 ack한다")
    fun suppressRevokedRecipient() {
        TestVendorServer().use { vendorServer ->
            val fixture = NotificationWorkerDispatchFixture(vendorServer, clock)
            val notification = fixture.publishedNotification(NotificationChannel.SLACK)
            fixture.binding(notification.recipientId, NotificationChannel.SLACK, "REVOKED", null)
            val acknowledgment = FakeAcknowledgment()

            fixture.listener.consume(fixture.payload(notification), acknowledgment)

            assertTrue(acknowledgment.acked)
            val saved = fixture.persistence.require(notification.id)
            assertEquals(NotificationStatus.SUPPRESSED, saved.status)
            assertEquals("recipient unavailable: REVOKED", saved.failureReason)
            assertEquals(0, saved.dispatchAttempts)
            assertEquals(listOf(fixture.bindingPath(notification.recipientId, NotificationChannel.SLACK)), vendorServer.paths)
            assertTrue(fixture.deduplication.releasedKeys.isEmpty())
            assertEquals(
                1.0,
                fixture.meterRegistry.get(NotificationWorkerMetricContract.Names.DISPATCH)
                    .tags("channel", "SLACK", "result", "suppressed")
                    .counter()
                    .count(),
            )
            assertEquals(
                1.0,
                fixture.meterRegistry.get(NotificationWorkerMetricContract.Names.RECIPIENT_RESOLVE)
                    .tags("channel", "SLACK", "result", "unavailable", "source", "user_service")
                    .counter()
                    .count(),
            )
        }
    }

    @Test
    @DisplayName("바인딩이 없는 수신자는 SUPPRESSED로 끝낸다")
    fun suppressUnknownRecipient() {
        TestVendorServer().use { vendorServer ->
            val fixture = NotificationWorkerDispatchFixture(vendorServer, clock)
            val notification = fixture.publishedNotification(NotificationChannel.SLACK)
            fixture.bindingResponse(
                notification.recipientId,
                NotificationChannel.SLACK,
                TestVendorResponse(statusCode = 404, body = """{"success":false,"data":null,"error":{"code":"CHANNEL_BINDING_NOT_FOUND"}}"""),
            )

            fixture.listener.consume(fixture.payload(notification), FakeAcknowledgment())

            assertEquals(NotificationStatus.SUPPRESSED, fixture.persistence.require(notification.id).status)
            assertEquals("recipient unavailable: NOT_FOUND", fixture.persistence.require(notification.id).failureReason)
        }
    }

    @Test
    @DisplayName("user-service가 5xx를 돌려주면 발송 없이 RETRY_WAIT로 두고 retry 예외를 던진다")
    fun retryWhenUserServiceFails() {
        TestVendorServer().use { vendorServer ->
            val fixture = NotificationWorkerDispatchFixture(vendorServer, clock)
            val notification = fixture.publishedNotification(NotificationChannel.SLACK)
            fixture.bindingResponse(
                notification.recipientId,
                NotificationChannel.SLACK,
                TestVendorResponse(statusCode = 503, body = """{"success":false}"""),
            )
            val acknowledgment = FakeAcknowledgment()

            assertFailsWith<RetryableDispatchMessageException> {
                fixture.listener.consume(fixture.payload(notification), acknowledgment)
            }

            assertFalse(acknowledgment.acked)
            val saved = fixture.persistence.require(notification.id)
            assertEquals(NotificationStatus.RETRY_WAIT, saved.status)
            assertEquals(1, saved.dispatchAttempts)
            assertFalse(vendorServer.paths.contains("/slack"))
            assertEquals(listOf("notification:dispatch:${notification.id.id}"), fixture.deduplication.releasedKeys)
        }
    }

    @Test
    @DisplayName("같은 수신자의 두 번째 알림은 캐시된 주소를 써서 user-service를 다시 부르지 않는다")
    fun reuseCachedAddress() {
        TestVendorServer().use { vendorServer ->
            val fixture = NotificationWorkerDispatchFixture(vendorServer, clock)
            val first = fixture.publishedNotification(NotificationChannel.SLACK)
            val second = fixture.publishedNotification(NotificationChannel.SLACK, recipientId = first.recipientId)
            fixture.binding(first.recipientId, NotificationChannel.SLACK, "ACTIVE", "${vendorServer.baseUrl}/slack")

            fixture.listener.consume(fixture.payload(first), FakeAcknowledgment())
            fixture.listener.consume(fixture.payload(second), FakeAcknowledgment())

            assertEquals(NotificationStatus.SENT, fixture.persistence.require(second.id).status)
            assertEquals(1, vendorServer.paths.count { it.startsWith("/api/v1/internal/") })
            assertEquals(2, vendorServer.paths.count { it == "/slack" })
        }
    }

    @Test
    @DisplayName("이미 vendor까지 간 requestId가 RETRY_WAIT로 돌아와 다시 오면 sent 마커에 걸려 SUPPRESSED로 끝낸다")
    fun suppressResendOfAlreadySentRequest() {
        TestVendorServer().use { vendorServer ->
            val fixture = NotificationWorkerDispatchFixture(vendorServer, clock)
            val notification = fixture.publishedNotification(NotificationChannel.SLACK)
            fixture.binding(notification.recipientId, NotificationChannel.SLACK, "ACTIVE", "${vendorServer.baseUrl}/slack")

            fixture.listener.consume(fixture.payload(notification), FakeAcknowledgment())
            assertEquals(NotificationStatus.SENT, fixture.persistence.require(notification.id).status)

            // vendor 호출 뒤 저장 전에 worker가 죽어 stale 회수로 RETRY_WAIT가 된 상황을 재현한다.
            // dispatch dedupe 마커는 TTL이 지나 사라졌고 sent 마커만 남아 있다.
            fixture.persistence.put(fixture.retryWaitCopy(notification))
            fixture.deduplication.expire("notification:dispatch:${notification.id.id}")
            val acknowledgment = FakeAcknowledgment()

            fixture.listener.consume(fixture.payload(notification), acknowledgment)

            assertTrue(acknowledgment.acked)
            val saved = fixture.persistence.require(notification.id)
            assertEquals(NotificationStatus.SUPPRESSED, saved.status)
            assertEquals("already sent", saved.failureReason)
            assertEquals(1, vendorServer.paths.count { it == "/slack" })
            assertTrue(fixture.deduplication.isHeld("notification:sent:${notification.requestId}"))
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
            val notification = fixture.publishedNotification(NotificationChannel.SLACK)
            fixture.binding(notification.recipientId, NotificationChannel.SLACK, "ACTIVE", "${vendorServer.baseUrl}/slack")
            val acknowledgment = FakeAcknowledgment()

            assertFailsWith<RetryableDispatchMessageException> {
                fixture.listener.consume(fixture.payload(notification), acknowledgment)
            }

            assertFalse(acknowledgment.acked)
            assertEquals(NotificationStatus.RETRY_WAIT, fixture.persistence.require(notification.id).status)
            assertEquals(
                listOf("notification:sent:${notification.requestId}", "notification:dispatch:${notification.id.id}"),
                fixture.deduplication.releasedKeys,
            )
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
            val notification = fixture.publishedNotification(NotificationChannel.DISCORD)
            fixture.binding(notification.recipientId, NotificationChannel.DISCORD, "ACTIVE", "${vendorServer.baseUrl}/discord")
            val acknowledgment = FakeAcknowledgment()

            fixture.listener.consume(fixture.payload(notification), acknowledgment)

            assertTrue(acknowledgment.acked)
            assertEquals(NotificationStatus.DEAD, fixture.persistence.require(notification.id).status)
        }
    }
}
