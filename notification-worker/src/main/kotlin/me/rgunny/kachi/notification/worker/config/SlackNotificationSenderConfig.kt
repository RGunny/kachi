package me.rgunny.kachi.notification.worker.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.slack.SlackNotificationSender
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.slack.SlackWebhookClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeUnit

/**
 * Slack sender adapter 설정.
 */
@Configuration
class SlackNotificationSenderConfig {

    /**
     * Slack webhook 호출 전용 WebClient.
     */
    @Bean(SLACK_WEB_CLIENT)
    @ConditionalOnProperty(
        prefix = "kachi.notification.sender.slack",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false,
    )
    fun slackWebClient(properties: NotificationSenderProperties): WebClient {
        val slack = properties.slack
        validateSlackHttpProperties(slack)

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(slackHttpClient(slack)))
            .codecs { it.defaultCodecs().maxInMemorySize(slack.maxInMemorySize) }
            .build()
    }

    /**
     * application.yaml의 kachi.notification.sender.slack.enabled=true일 때만 실제 Slack sender bean을 등록한다.
     *
     * @ConditionalOnProperty의 prefix/name은 별도 설정값이 아니라 같은 설정 key를 조회하는 경로다.
     * mock sender가 SLACK을 포함해도 MockNotificationSenderConfig가 같은 real sender enabled 설정을 보고 mock SLACK을 제외한다.
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "kachi.notification.sender.slack",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false,
    )
    fun slackNotificationSender(
        @Qualifier(SLACK_WEB_CLIENT) webClient: WebClient,
        properties: NotificationSenderProperties,
    ): SlackNotificationSender {
        val slack = properties.slack
        val webhookUrl = validateSlackSenderProperties(slack)

        return SlackNotificationSender(
            client = SlackWebhookClient(
                webClient = webClient,
                webhookUrl = webhookUrl,
            ),
        )
    }

    private fun validateSlackSenderProperties(slack: NotificationSenderProperties.Slack): String {
        val webhookUrl = slack.webhookUrl
        require(!webhookUrl.isNullOrBlank()) {
            "slack webhookUrl must not be blank when slack sender is enabled"
        }
        return webhookUrl
    }

    private fun validateSlackHttpProperties(slack: NotificationSenderProperties.Slack) {
        require(!slack.connectTimeout.isZero && !slack.connectTimeout.isNegative) {
            "slack connectTimeout must be positive"
        }
        require(!slack.responseTimeout.isZero && !slack.responseTimeout.isNegative) {
            "slack responseTimeout must be positive"
        }
        require(!slack.readTimeout.isZero && !slack.readTimeout.isNegative) {
            "slack readTimeout must be positive"
        }
        require(!slack.writeTimeout.isZero && !slack.writeTimeout.isNegative) {
            "slack writeTimeout must be positive"
        }
        require(slack.maxInMemorySize > 0) {
            "slack maxInMemorySize must be positive"
        }
    }

    private fun slackHttpClient(slack: NotificationSenderProperties.Slack): HttpClient {
        return HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, slack.connectTimeout.toMillis().toInt())
            .responseTimeout(slack.responseTimeout)
            .doOnConnected { connection ->
                connection
                    .addHandlerLast(ReadTimeoutHandler(slack.readTimeout.toMillis(), TimeUnit.MILLISECONDS))
                    .addHandlerLast(WriteTimeoutHandler(slack.writeTimeout.toMillis(), TimeUnit.MILLISECONDS))
            }
    }

    private companion object {
        const val SLACK_WEB_CLIENT = "slackWebClient"
    }
}
