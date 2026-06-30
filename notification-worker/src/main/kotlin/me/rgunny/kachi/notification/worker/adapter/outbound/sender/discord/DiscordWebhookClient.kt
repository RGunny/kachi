package me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord.dto.DiscordWebhookErrorResponse
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord.dto.DiscordWebhookRequest
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord.dto.DiscordWebhookResult
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.math.RoundingMode

internal class DiscordWebhookClient(
    private val webClient: WebClient,
    private val webhookUrl: String,
) {

    suspend fun send(content: String): DiscordWebhookResult {
        return webClient.post()
            .uri(webhookUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(DiscordWebhookRequest(content = content))
            .exchangeToMono { clientResponse ->
                val statusCode = clientResponse.statusCode().value()
                val retryAfterMillis = retryAfterMillis(clientResponse.headers().asHttpHeaders())
                if (statusCode != HTTP_TOO_MANY_REQUESTS || retryAfterMillis != null) {
                    return@exchangeToMono Mono.just(
                        DiscordWebhookResult(
                            statusCode = statusCode,
                            retryAfterMillis = retryAfterMillis,
                        )
                    )
                }

                clientResponse.bodyToMono(DiscordWebhookErrorResponse::class.java)
                    .defaultIfEmpty(DiscordWebhookErrorResponse())
                    .map { body ->
                        DiscordWebhookResult(
                            statusCode = statusCode,
                            retryAfterMillis = retryAfterMillis(body.retryAfter),
                        )
                    }
            }
            .awaitSingle()
    }

    private fun retryAfterMillis(headers: HttpHeaders): Long? {
        return headers.getFirst(HttpHeaders.RETRY_AFTER)
            ?.let(::secondsToMillis)
            ?: headers.getFirst(DISCORD_RESET_AFTER_HEADER)
                ?.let(::secondsToMillis)
    }

    private fun retryAfterMillis(retryAfter: BigDecimal?): Long? {
        return retryAfter
            ?.takeIf { it >= BigDecimal.ZERO }
            ?.let(::secondsToMillis)
    }

    private fun secondsToMillis(value: String): Long? {
        return runCatching { secondsToMillis(BigDecimal(value)) }.getOrNull()
    }

    private fun secondsToMillis(value: BigDecimal): Long {
        return value
            .multiply(MILLIS_PER_SECOND)
            .setScale(0, RoundingMode.CEILING)
            .longValueExact()
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val DISCORD_RESET_AFTER_HEADER = "X-RateLimit-Reset-After"
        val MILLIS_PER_SECOND: BigDecimal = BigDecimal.valueOf(1000)
    }
}
