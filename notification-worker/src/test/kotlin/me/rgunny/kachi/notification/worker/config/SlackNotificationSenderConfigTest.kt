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
    @DisplayName("enabled 상태에서 webhookUrl과 timeout이 유효하면 sender를 생성한다")
    fun slackNotificationSender() {
        val sender = config.slackNotificationSender(
            webClientBuilder = WebClient.builder(),
            properties = properties(webhookUrl = "https://hooks.slack.test/services/test"),
        )

        assertNotNull(sender)
    }

    @Test
    @DisplayName("enabled 상태에서 webhookUrl이 비어 있으면 거부한다")
    fun rejectBlankWebhookUrl() {
        assertFailsWith<IllegalArgumentException> {
            config.slackNotificationSender(
                webClientBuilder = WebClient.builder(),
                properties = properties(webhookUrl = ""),
            )
        }
    }

    @Test
    @DisplayName("enabled 상태에서 timeout이 양수가 아니면 거부한다")
    fun rejectInvalidTimeout() {
        assertFailsWith<IllegalArgumentException> {
            config.slackNotificationSender(
                webClientBuilder = WebClient.builder(),
                properties = properties(
                    webhookUrl = "https://hooks.slack.test/services/test",
                    timeout = Duration.ZERO,
                ),
            )
        }
    }

    private fun properties(
        webhookUrl: String,
        timeout: Duration = Duration.ofSeconds(3),
    ): NotificationWorkerProperties {
        return NotificationWorkerProperties(
            sender = NotificationWorkerProperties.Sender(
                slack = NotificationWorkerProperties.Sender.Slack(
                    enabled = true,
                    webhookUrl = webhookUrl,
                    timeout = timeout,
                )
            )
        )
    }
}
