package me.rgunny.kachi.notification.worker.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@DisplayName("TelegramNotificationSenderConfig")
class TelegramNotificationSenderConfigTest {

    private val config = TelegramNotificationSenderConfig()

    @Test
    @DisplayName("Telegram Bot API 전용 WebClient를 생성한다")
    fun telegramWebClient() {
        val webClient = config.telegramWebClient(
            properties = properties(botToken = "telegram-bot-token"),
        )

        assertNotNull(webClient)
    }

    @Test
    @DisplayName("enabled 상태에서 botToken과 HTTP 설정이 유효하면 sender를 생성한다")
    fun telegramNotificationSender() {
        val sender = config.telegramNotificationSender(
            webClient = WebClient.builder().build(),
            properties = properties(botToken = "telegram-bot-token"),
        )

        assertNotNull(sender)
    }

    @Test
    @DisplayName("enabled 상태에서 botToken이 null이면 거부한다")
    fun rejectNullBotToken() {
        assertFailsWith<IllegalArgumentException> {
            config.telegramNotificationSender(
                webClient = WebClient.builder().build(),
                properties = properties(botToken = null),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 botToken이 비어 있으면 거부한다")
    fun rejectBlankBotToken() {
        assertFailsWith<IllegalArgumentException> {
            config.telegramNotificationSender(
                webClient = WebClient.builder().build(),
                properties = properties(botToken = ""),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 baseUrl이 비어 있으면 거부한다")
    fun rejectBlankBaseUrl() {
        assertFailsWith<IllegalArgumentException> {
            config.telegramWebClient(
                properties = properties(
                    botToken = "telegram-bot-token",
                    baseUrl = "",
                ),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 sendMessagePath가 /로 시작하지 않으면 거부한다")
    fun rejectInvalidSendMessagePath() {
        assertFailsWith<IllegalArgumentException> {
            config.telegramWebClient(
                properties = properties(
                    botToken = "telegram-bot-token",
                    sendMessagePath = "sendMessage",
                ),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 connectTimeout이 양수가 아니면 거부한다")
    fun rejectInvalidConnectTimeout() {
        assertFailsWith<IllegalArgumentException> {
            config.telegramWebClient(
                properties = properties(
                    botToken = "telegram-bot-token",
                    connectTimeout = Duration.ZERO,
                ),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 responseTimeout이 양수가 아니면 거부한다")
    fun rejectInvalidResponseTimeout() {
        assertFailsWith<IllegalArgumentException> {
            config.telegramWebClient(
                properties = properties(
                    botToken = "telegram-bot-token",
                    responseTimeout = Duration.ZERO,
                ),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 readTimeout이 양수가 아니면 거부한다")
    fun rejectInvalidReadTimeout() {
        assertFailsWith<IllegalArgumentException> {
            config.telegramWebClient(
                properties = properties(
                    botToken = "telegram-bot-token",
                    readTimeout = Duration.ZERO,
                ),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 writeTimeout이 양수가 아니면 거부한다")
    fun rejectInvalidWriteTimeout() {
        assertFailsWith<IllegalArgumentException> {
            config.telegramWebClient(
                properties = properties(
                    botToken = "telegram-bot-token",
                    writeTimeout = Duration.ZERO,
                ),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 maxInMemorySize가 양수가 아니면 거부한다")
    fun rejectInvalidMaxInMemorySize() {
        assertFailsWith<IllegalArgumentException> {
            config.telegramWebClient(
                properties = properties(
                    botToken = "telegram-bot-token",
                    maxInMemorySize = 0,
                ),
            )
        }
    }

    private fun properties(
        botToken: String?,
        baseUrl: String = "https://api.telegram.test",
        sendMessagePath: String = "/sendMessage",
        connectTimeout: Duration = Duration.ofSeconds(2),
        responseTimeout: Duration = Duration.ofSeconds(5),
        readTimeout: Duration = Duration.ofSeconds(5),
        writeTimeout: Duration = Duration.ofSeconds(5),
        maxInMemorySize: Int = 256 * 1024,
    ): NotificationSenderProperties {
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
                enabled = false,
                webhookUrl = "https://discord.test/api/webhooks/test",
                connectTimeout = Duration.ofSeconds(2),
                responseTimeout = Duration.ofSeconds(5),
                readTimeout = Duration.ofSeconds(5),
                writeTimeout = Duration.ofSeconds(5),
                maxInMemorySize = 256 * 1024,
            ),
            telegram = NotificationSenderProperties.Telegram(
                enabled = true,
                baseUrl = baseUrl,
                botToken = botToken,
                sendMessagePath = sendMessagePath,
                connectTimeout = connectTimeout,
                responseTimeout = responseTimeout,
                readTimeout = readTimeout,
                writeTimeout = writeTimeout,
                maxInMemorySize = maxInMemorySize,
            ),
        )
    }
}
