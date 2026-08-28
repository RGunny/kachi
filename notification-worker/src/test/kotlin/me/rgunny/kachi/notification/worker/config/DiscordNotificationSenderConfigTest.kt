package me.rgunny.kachi.notification.worker.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@DisplayName("DiscordNotificationSenderConfig")
class DiscordNotificationSenderConfigTest {

    private val config = DiscordNotificationSenderConfig()

    @Test
    @DisplayName("Discord webhook 전용 WebClient를 생성한다")
    fun discordWebClient() {
        val webClient = config.discordWebClient(
            properties = properties(),
        )

        assertNotNull(webClient)
    }

    @Test
    @DisplayName("enabled 상태면 sender를 생성한다")
    fun discordNotificationSender() {
        val sender = config.discordNotificationSender(
            webClient = WebClient.builder().build(),
        )

        assertNotNull(sender)
    }

    @Test
    @DisplayName("enabled 상태에서 connectTimeout이 양수가 아니면 거부한다")
    fun rejectInvalidConnectTimeout() {
        assertFailsWith<IllegalArgumentException> {
            config.discordWebClient(
                properties = properties(
                    connectTimeout = Duration.ZERO,
                ),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 responseTimeout이 양수가 아니면 거부한다")
    fun rejectInvalidResponseTimeout() {
        assertFailsWith<IllegalArgumentException> {
            config.discordWebClient(
                properties = properties(
                    responseTimeout = Duration.ZERO,
                ),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 readTimeout이 양수가 아니면 거부한다")
    fun rejectInvalidReadTimeout() {
        assertFailsWith<IllegalArgumentException> {
            config.discordWebClient(
                properties = properties(
                    readTimeout = Duration.ZERO,
                ),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 writeTimeout이 양수가 아니면 거부한다")
    fun rejectInvalidWriteTimeout() {
        assertFailsWith<IllegalArgumentException> {
            config.discordWebClient(
                properties = properties(
                    writeTimeout = Duration.ZERO,
                ),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 maxInMemorySize가 양수가 아니면 거부한다")
    fun rejectInvalidMaxInMemorySize() {
        assertFailsWith<IllegalArgumentException> {
            config.discordWebClient(
                properties = properties(
                    maxInMemorySize = 0,
                ),
            )
        }
    }

    private fun properties(
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
                enabled = true,
                connectTimeout = Duration.ofSeconds(2),
                responseTimeout = Duration.ofSeconds(5),
                readTimeout = Duration.ofSeconds(5),
                writeTimeout = Duration.ofSeconds(5),
                maxInMemorySize = 256 * 1024,
            ),
            discord = NotificationSenderProperties.Discord(
                enabled = true,
                connectTimeout = connectTimeout,
                responseTimeout = responseTimeout,
                readTimeout = readTimeout,
                writeTimeout = writeTimeout,
                maxInMemorySize = maxInMemorySize,
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
}
