package me.rgunny.kachi.notification.worker.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram.TelegramNotificationSender
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram.TelegramSendMessageClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeUnit

/**
 * Telegram sender adapter 설정.
 */
@Configuration
class TelegramNotificationSenderConfig {

    /**
     * Telegram Bot API 호출 전용 WebClient.
     */
    @Bean(TELEGRAM_WEB_CLIENT)
    @ConditionalOnProperty(
        prefix = "kachi.notification.worker.sender.telegram",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false,
    )
    fun telegramWebClient(properties: NotificationWorkerProperties): WebClient {
        val telegram = properties.sender.telegram
        validateTelegramHttpProperties(telegram)

        return WebClient.builder()
            .baseUrl(telegram.baseUrl)
            .clientConnector(ReactorClientHttpConnector(telegramHttpClient(telegram)))
            .codecs { it.defaultCodecs().maxInMemorySize(telegram.maxInMemorySize) }
            .build()
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "kachi.notification.worker.sender.telegram",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false,
    )
    fun telegramNotificationSender(
        @Qualifier(TELEGRAM_WEB_CLIENT) webClient: WebClient,
        properties: NotificationWorkerProperties,
    ): TelegramNotificationSender {
        val telegram = properties.sender.telegram
        val botToken = validateTelegramSenderProperties(telegram)

        return TelegramNotificationSender(
            client = TelegramSendMessageClient(
                webClient = webClient,
                botToken = botToken,
                sendMessagePath = telegram.sendMessagePath,
            ),
        )
    }

    private fun validateTelegramSenderProperties(telegram: NotificationWorkerProperties.Sender.Telegram): String {
        val botToken = telegram.botToken
        require(!botToken.isNullOrBlank()) {
            "telegram botToken must not be blank when telegram sender is enabled"
        }
        return botToken
    }

    private fun validateTelegramHttpProperties(telegram: NotificationWorkerProperties.Sender.Telegram) {
        require(telegram.baseUrl.isNotBlank()) {
            "telegram baseUrl must not be blank"
        }
        require(telegram.sendMessagePath.startsWith("/")) {
            "telegram sendMessagePath must start with /"
        }
        require(!telegram.connectTimeout.isZero && !telegram.connectTimeout.isNegative) {
            "telegram connectTimeout must be positive"
        }
        require(!telegram.responseTimeout.isZero && !telegram.responseTimeout.isNegative) {
            "telegram responseTimeout must be positive"
        }
        require(!telegram.readTimeout.isZero && !telegram.readTimeout.isNegative) {
            "telegram readTimeout must be positive"
        }
        require(!telegram.writeTimeout.isZero && !telegram.writeTimeout.isNegative) {
            "telegram writeTimeout must be positive"
        }
        require(telegram.maxInMemorySize > 0) {
            "telegram maxInMemorySize must be positive"
        }
    }

    private fun telegramHttpClient(telegram: NotificationWorkerProperties.Sender.Telegram): HttpClient {
        return HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, telegram.connectTimeout.toMillis().toInt())
            .responseTimeout(telegram.responseTimeout)
            .doOnConnected { connection ->
                connection
                    .addHandlerLast(ReadTimeoutHandler(telegram.readTimeout.toMillis(), TimeUnit.MILLISECONDS))
                    .addHandlerLast(WriteTimeoutHandler(telegram.writeTimeout.toMillis(), TimeUnit.MILLISECONDS))
            }
    }

    private companion object {
        const val TELEGRAM_WEB_CLIENT = "telegramWebClient"
    }
}
