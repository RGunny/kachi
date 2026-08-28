package me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationResult
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.worker.config.DiscordNotificationSenderConfig
import me.rgunny.kachi.notification.worker.config.NotificationSenderProperties
import me.rgunny.kachi.notification.worker.support.TestSecretEnvironment
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertIs

@DisplayName("DiscordNotificationSender 실제 webhook 통합 테스트")
class DiscordNotificationSenderRealIntegrationTest {

    @Test
    @DisplayName("실제 Discord incoming webhook으로 테스트 메시지를 전송한다")
    fun sendRealWebhook() {
        val webhookUrl = TestSecretEnvironment.value(WEBHOOK_URL_ENV)
        assumeTrue(webhookUrl != null, "KACHI_NOTIFICATION_DISCORD_WEBHOOK_URL is not configured")
        val configuredWebhookUrl = requireNotNull(webhookUrl)

        runBlocking {
            val config = DiscordNotificationSenderConfig()
            val properties = properties(configuredWebhookUrl)
            val sender = config.discordNotificationSender(
                webClient = config.discordWebClient(properties),
                properties = properties,
            )

            val result = sender.send(
                SendNotificationCommand(
                notificationId = NotificationId.newId(),
                channel = NotificationChannel.DISCORD,
                recipientId = "user-1",
                message = "[${currentTimestamp()}] [notification-worker] [discord-webhook-test] DiscordNotificationSender real integration test",
                idempotencyKey = "discord-real-integration-test",
                )
            )

            assertIs<SendNotificationResult.Success>(result)
        }
    }

    private companion object {
        const val WEBHOOK_URL_ENV = "KACHI_NOTIFICATION_DISCORD_WEBHOOK_URL"
        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val MESSAGE_TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun properties(webhookUrl: String): NotificationSenderProperties {
            return NotificationSenderProperties(
                mock = NotificationSenderProperties.Mock(
                    enabled = true,
                    channels = listOf("SLACK", "DISCORD", "TELEGRAM", "SMS", "KAKAO", "EMAIL"),
                    mode = "SUCCESS",
                ),
                slack = NotificationSenderProperties.Slack(
                    enabled = false,
                    webhookUrl = "https://hooks.slack.test/services/test",
                    connectTimeout = Duration.ofSeconds(2),
                    responseTimeout = Duration.ofSeconds(5),
                    readTimeout = Duration.ofSeconds(5),
                    writeTimeout = Duration.ofSeconds(5),
                    maxInMemorySize = 256 * 1024,
                ),
                discord = NotificationSenderProperties.Discord(
                    enabled = true,
                    webhookUrl = webhookUrl,
                    connectTimeout = Duration.ofSeconds(2),
                    responseTimeout = Duration.ofSeconds(5),
                    readTimeout = Duration.ofSeconds(5),
                    writeTimeout = Duration.ofSeconds(5),
                    maxInMemorySize = 256 * 1024,
                ),
                telegram = NotificationSenderProperties.Telegram(
                    enabled = false,
                    baseUrl = "https://api.telegram.test",
                    botToken = null,
                    sendMessagePath = "/sendMessage",
                    connectTimeout = Duration.ofSeconds(2),
                    responseTimeout = Duration.ofSeconds(5),
                    readTimeout = Duration.ofSeconds(5),
                    writeTimeout = Duration.ofSeconds(5),
                    maxInMemorySize = 256 * 1024,
                ),
            )
        }

        fun currentTimestamp(): String {
            return ZonedDateTime.now(SEOUL_ZONE).format(MESSAGE_TIMESTAMP_FORMATTER)
        }
    }
}
