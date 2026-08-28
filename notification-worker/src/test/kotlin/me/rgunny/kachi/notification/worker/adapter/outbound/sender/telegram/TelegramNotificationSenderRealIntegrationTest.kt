package me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationResult
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.worker.config.NotificationSenderProperties
import me.rgunny.kachi.notification.worker.config.TelegramNotificationSenderConfig
import me.rgunny.kachi.notification.worker.support.TestSecretEnvironment
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertIs

@DisplayName("TelegramNotificationSender 실제 Bot API 통합 테스트")
class TelegramNotificationSenderRealIntegrationTest {

    @Test
    @DisplayName("실제 Telegram Bot API sendMessage로 테스트 메시지를 전송한다")
    fun sendRealBotMessage() {
        val botToken = TestSecretEnvironment.value(BOT_TOKEN_ENV)
        val chatId = TestSecretEnvironment.value(CHAT_ID_ENV)
        assumeTrue(botToken != null, "KACHI_NOTIFICATION_TELEGRAM_BOT_TOKEN is not configured")
        assumeTrue(chatId != null, "KACHI_NOTIFICATION_TELEGRAM_CHAT_ID is not configured")
        val configuredBotToken = requireNotNull(botToken)
        val configuredChatId = requireNotNull(chatId)

        runBlocking {
            val config = TelegramNotificationSenderConfig()
            val properties = properties(configuredBotToken)
            val sender = config.telegramNotificationSender(
                webClient = config.telegramWebClient(properties),
                properties = properties,
            )

            val result = sender.send(
                SendNotificationCommand(
                notificationId = NotificationId.newId(),
                channel = NotificationChannel.TELEGRAM,
                address = configuredChatId,
                message = "[${currentTimestamp()}] [notification-worker] [telegram-sendMessage-test] TelegramNotificationSender real integration test",
                idempotencyKey = "telegram-real-integration-test",
                )
            )

            assertIs<SendNotificationResult.Success>(result)
        }
    }

    private companion object {
        const val BOT_TOKEN_ENV = "KACHI_NOTIFICATION_TELEGRAM_BOT_TOKEN"
        const val CHAT_ID_ENV = "KACHI_NOTIFICATION_TELEGRAM_CHAT_ID"
        val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        val MESSAGE_TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun properties(botToken: String): NotificationSenderProperties {
            return NotificationSenderProperties(
                mock = NotificationSenderProperties.Mock(
                    enabled = true,
                    channels = listOf("SLACK", "DISCORD", "TELEGRAM", "SMS", "KAKAO", "EMAIL"),
                    mode = "SUCCESS",
                ),
                slack = NotificationSenderProperties.Slack(
                    enabled = false,
                    connectTimeout = Duration.ofSeconds(2),
                    responseTimeout = Duration.ofSeconds(5),
                    readTimeout = Duration.ofSeconds(5),
                    writeTimeout = Duration.ofSeconds(5),
                    maxInMemorySize = 256 * 1024,
                ),
                discord = NotificationSenderProperties.Discord(
                    enabled = false,
                    connectTimeout = Duration.ofSeconds(2),
                    responseTimeout = Duration.ofSeconds(5),
                    readTimeout = Duration.ofSeconds(5),
                    writeTimeout = Duration.ofSeconds(5),
                    maxInMemorySize = 256 * 1024,
                ),
                telegram = NotificationSenderProperties.Telegram(
                    enabled = true,
                    baseUrl = "https://api.telegram.org",
                    botToken = botToken,
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
