package me.rgunny.kachi.notification.worker.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@DisplayName("SlackNotificationSenderConfig")
class SlackNotificationSenderConfigTest {

    private val config = SlackNotificationSenderConfig()

    @Test
    @DisplayName("Slack webhook 전용 WebClient를 생성한다")
    fun slackWebClient() {
        val webClient = config.slackWebClient(
            properties = properties(webhookUrl = "https://hooks.slack.test/services/test"),
        )

        assertNotNull(webClient)
    }

    @Test
    @DisplayName("enabled 상태에서 webhookUrl과 timeout이 유효하면 sender를 생성한다")
    fun slackNotificationSender() {
        val sender = config.slackNotificationSender(
            webClient = WebClient.builder().build(),
            properties = properties(webhookUrl = "https://hooks.slack.test/services/test"),
        )

        assertNotNull(sender)
    }

    @Test
    @DisplayName("enabled 상태에서 webhookUrl이 비어 있으면 거부한다")
    fun rejectBlankWebhookUrl() {
        assertFailsWith<IllegalArgumentException> {
            config.slackNotificationSender(
                webClient = WebClient.builder().build(),
                properties = properties(webhookUrl = ""),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 connectTimeout이 양수가 아니면 거부한다")
    fun rejectInvalidConnectTimeout() {
        assertFailsWith<IllegalArgumentException> {
            config.slackWebClient(
                properties = properties(
                    webhookUrl = "https://hooks.slack.test/services/test",
                    connectTimeout = Duration.ZERO,
                ),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 maxInMemorySize가 양수가 아니면 거부한다")
    fun rejectInvalidMaxInMemorySize() {
        assertFailsWith<IllegalArgumentException> {
            config.slackWebClient(
                properties = properties(
                    webhookUrl = "https://hooks.slack.test/services/test",
                    maxInMemorySize = 0,
                )
            )
        }
    }

    private fun properties(
        webhookUrl: String,
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
