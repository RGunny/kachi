package me.rgunny.kachi.notification.worker.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord.DiscordNotificationSender
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord.DiscordWebhookClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.util.concurrent.TimeUnit

/**
 * Discord sender adapter 설정.
 */
@Configuration
class DiscordNotificationSenderConfig {

    /**
     * Discord webhook 호출 전용 WebClient.
     */
    @Bean(DISCORD_WEB_CLIENT)
    @ConditionalOnProperty(
        prefix = "kachi.notification.sender.discord",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false,
    )
    fun discordWebClient(properties: NotificationSenderProperties): WebClient {
        val discord = properties.discord
        validateDiscordHttpProperties(discord)

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(discordHttpClient(discord)))
            .codecs { it.defaultCodecs().maxInMemorySize(discord.maxInMemorySize) }
            .build()
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "kachi.notification.sender.discord",
        name = ["enabled"],
        havingValue = "true",
        matchIfMissing = false,
    )
    fun discordNotificationSender(
        @Qualifier(DISCORD_WEB_CLIENT) webClient: WebClient,
        properties: NotificationSenderProperties,
    ): DiscordNotificationSender {
        val discord = properties.discord
        val webhookUrl = validateDiscordSenderProperties(discord)

        return DiscordNotificationSender(
            client = DiscordWebhookClient(
                webClient = webClient,
                webhookUrl = webhookUrl,
            ),
        )
    }

    private fun validateDiscordSenderProperties(discord: NotificationSenderProperties.Discord): String {
        val webhookUrl = discord.webhookUrl
        require(!webhookUrl.isNullOrBlank()) {
            "discord webhookUrl must not be blank when discord sender is enabled"
        }
        return webhookUrl
    }

    private fun validateDiscordHttpProperties(discord: NotificationSenderProperties.Discord) {
        require(!discord.connectTimeout.isZero && !discord.connectTimeout.isNegative) {
            "discord connectTimeout must be positive"
        }
        require(!discord.responseTimeout.isZero && !discord.responseTimeout.isNegative) {
            "discord responseTimeout must be positive"
        }
        require(!discord.readTimeout.isZero && !discord.readTimeout.isNegative) {
            "discord readTimeout must be positive"
        }
        require(!discord.writeTimeout.isZero && !discord.writeTimeout.isNegative) {
            "discord writeTimeout must be positive"
        }
        require(discord.maxInMemorySize > 0) {
            "discord maxInMemorySize must be positive"
        }
    }

    private fun discordHttpClient(discord: NotificationSenderProperties.Discord): HttpClient {
        return HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, discord.connectTimeout.toMillis().toInt())
            .responseTimeout(discord.responseTimeout)
            .doOnConnected { connection ->
                connection
                    .addHandlerLast(ReadTimeoutHandler(discord.readTimeout.toMillis(), TimeUnit.MILLISECONDS))
                    .addHandlerLast(WriteTimeoutHandler(discord.writeTimeout.toMillis(), TimeUnit.MILLISECONDS))
            }
    }

    private companion object {
        const val DISCORD_WEB_CLIENT = "discordWebClient"
    }
}
