package me.rgunny.kachi.notification.worker.config

import me.rgunny.kachi.notification.worker.adapter.outbound.sender.slack.SlackNotificationSender
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Slack sender adapter 설정.
 */
@Configuration
class SlackNotificationSenderConfig {

    /**
     * application.yaml의 kachi.notification.worker.sender.slack.enabled=true일 때만 실제 Slack sender bean을 등록한다.
     *
     * @ConditionalOnProperty의 prefix/name은 별도 설정값이 아니라 같은 설정 key를 조회하는 경로다.
     * mock sender가 SLACK을 포함해도 MockNotificationSenderConfig가 같은 real sender enabled 설정을 보고 mock SLACK을 제외한다.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "kachi.notification.worker.sender.slack",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false,
    )
    fun slackNotificationSender(
        webClientBuilder: WebClient.Builder,
        properties: NotificationWorkerProperties,
    ): SlackNotificationSender {
        val slack = properties.sender.slack
        require(slack.webhookUrl.isNotBlank()) {
            "slack webhookUrl must not be blank when slack sender is enabled"
        }
        require(!slack.timeout.isZero && !slack.timeout.isNegative) {
            "slack timeout must be positive"
        }

        return SlackNotificationSender(
            webClient = webClientBuilder.build(),
            webhookUrl = slack.webhookUrl,
            timeout = slack.timeout,
        )
    }
}
