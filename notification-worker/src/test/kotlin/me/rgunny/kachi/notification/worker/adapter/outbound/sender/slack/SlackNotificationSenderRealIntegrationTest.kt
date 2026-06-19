package me.rgunny.kachi.notification.worker.adapter.outbound.sender.slack

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.dto.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.SendNotificationResult
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.worker.support.TestSecretEnvironment
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertIs

@DisplayName("SlackNotificationSender 실제 webhook 통합 테스트")
class SlackNotificationSenderRealIntegrationTest {

    @Test
    @DisplayName("실제 Slack incoming webhook으로 테스트 메시지를 전송한다")
    fun sendRealWebhook() {
        val webhookUrl = TestSecretEnvironment.value(WEBHOOK_URL_ENV)
        assumeTrue(webhookUrl != null, "KACHI_NOTIFICATION_SLACK_WEBHOOK_URL is not configured")
        val configuredWebhookUrl = requireNotNull(webhookUrl)

        runBlocking {
            val sender = SlackNotificationSender(
                webClient = WebClient.builder().build(),
                webhookUrl = configuredWebhookUrl,
                timeout = Duration.ofSeconds(5),
            )

            val result = sender.send(
                SendNotificationCommand(
                    notificationId = NotificationId.newId(),
                    channel = NotificationChannel.SLACK,
                    recipient = "slack-webhook",
                    message = "[${currentTimestamp()}] [notification-worker] [slack-webhook-test] SlackNotificationSender real integration test",
                    idempotencyKey = "slack-real-integration-test",
                )
            )

            assertIs<SendNotificationResult.Success>(result)
        }
    }

    private companion object {
        const val WEBHOOK_URL_ENV = "KACHI_NOTIFICATION_SLACK_WEBHOOK_URL"
        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val MESSAGE_TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun currentTimestamp(): String {
            return ZonedDateTime.now(SEOUL_ZONE).format(MESSAGE_TIMESTAMP_FORMATTER)
        }
    }
}
