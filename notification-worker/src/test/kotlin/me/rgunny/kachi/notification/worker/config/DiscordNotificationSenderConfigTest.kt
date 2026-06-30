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
            properties = properties(webhookUrl = "https://discord.test/api/webhooks/test"),
        )

        assertNotNull(webClient)
    }

    @Test
    @DisplayName("enabled 상태에서 webhookUrl과 timeout이 유효하면 sender를 생성한다")
    fun discordNotificationSender() {
        val sender = config.discordNotificationSender(
            webClient = WebClient.builder().build(),
            properties = properties(webhookUrl = "https://discord.test/api/webhooks/test"),
        )

        assertNotNull(sender)
    }

    @Test
    @DisplayName("enabled 상태에서 webhookUrl이 null이면 거부한다")
    fun rejectNullWebhookUrl() {
        assertFailsWith<IllegalArgumentException> {
            config.discordNotificationSender(
                webClient = WebClient.builder().build(),
                properties = properties(webhookUrl = null),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 webhookUrl이 비어 있으면 거부한다")
    fun rejectBlankWebhookUrl() {
        assertFailsWith<IllegalArgumentException> {
            config.discordNotificationSender(
                webClient = WebClient.builder().build(),
                properties = properties(webhookUrl = ""),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 connectTimeout이 양수가 아니면 거부한다")
    fun rejectInvalidConnectTimeout() {
        assertFailsWith<IllegalArgumentException> {
            config.discordWebClient(
                properties = properties(
                    webhookUrl = "https://discord.test/api/webhooks/test",
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
                    webhookUrl = "https://discord.test/api/webhooks/test",
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
                    webhookUrl = "https://discord.test/api/webhooks/test",
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
                    webhookUrl = "https://discord.test/api/webhooks/test",
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
                    webhookUrl = "https://discord.test/api/webhooks/test",
                    maxInMemorySize = 0,
                )
            )
        }
    }

    private fun properties(
        webhookUrl: String?,
        connectTimeout: Duration = Duration.ofSeconds(2),
        responseTimeout: Duration = Duration.ofSeconds(5),
        readTimeout: Duration = Duration.ofSeconds(5),
        writeTimeout: Duration = Duration.ofSeconds(5),
        maxInMemorySize: Int = 256 * 1024,
    ): NotificationWorkerProperties {
        return NotificationWorkerProperties(
            workerId = "test-worker",
            dispatch = NotificationWorkerProperties.Dispatch(
                topic = "notification.dispatch",
                groupId = "notification-worker",
                dedupeTtl = Duration.ofMinutes(5),
                idempotencyKeyTtl = Duration.ofHours(24),
                retry = NotificationWorkerProperties.Dispatch.Retry(
                    maxAttempts = 3,
                    backoff = Duration.ofSeconds(1),
                ),
                dlt = NotificationWorkerProperties.Dispatch.Dlt(
                    topic = "notification.dispatch.dlt",
                ),
            ),
            sender = NotificationWorkerProperties.Sender(
                mock = NotificationWorkerProperties.Sender.Mock(
                    enabled = true,
                    channels = listOf("SLACK", "DISCORD", "TELEGRAM", "SMS", "KAKAO", "EMAIL"),
                    mode = "SUCCESS",
                ),
                slack = NotificationWorkerProperties.Sender.Slack(
                    enabled = true,
                    webhookUrl = "https://hooks.slack.test/services/test",
                    connectTimeout = Duration.ofSeconds(2),
                    responseTimeout = Duration.ofSeconds(5),
                    readTimeout = Duration.ofSeconds(5),
                    writeTimeout = Duration.ofSeconds(5),
                    maxInMemorySize = 256 * 1024,
                ),
                discord = NotificationWorkerProperties.Sender.Discord(
                    enabled = true,
                    webhookUrl = webhookUrl,
                    connectTimeout = connectTimeout,
                    responseTimeout = responseTimeout,
                    readTimeout = readTimeout,
                    writeTimeout = writeTimeout,
                    maxInMemorySize = maxInMemorySize,
                ),
            ),
        )
    }
}
