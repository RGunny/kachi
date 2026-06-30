package me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.application.port.dto.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.SendNotificationResult
import me.rgunny.kachi.notification.application.port.outbound.NotificationSender
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.retry.FailureCategory
import me.rgunny.kachi.notification.retry.FailureSource
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryFailureCode
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.VendorHttpExceptionClassifier
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Discord incoming webhook 기반 NotificationSender adapter.
 *
 * Discord webhook은 wait=false 기본값에서 성공 시 204 No Content를 반환할 수 있고,
 * rate limit은 429와 Retry-After/X-RateLimit-Reset-After header로 표현된다.
 */
class DiscordNotificationSender(
    private val webClient: WebClient,
    private val webhookUrl: String,
) : NotificationSender {

    override fun supports(channel: NotificationChannel): Boolean {
        return channel == NotificationChannel.DISCORD
    }

    override suspend fun send(command: SendNotificationCommand): SendNotificationResult {
        require(command.channel == NotificationChannel.DISCORD) {
            "DiscordNotificationSender only supports DISCORD channel"
        }
        require(command.message.isNotBlank()) { "message must not be blank" }

        return try {
            val response = webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(DiscordWebhookRequest(content = command.message))
                .exchangeToMono { clientResponse ->
                    Mono.just(
                        DiscordWebhookResponse(
                            statusCode = clientResponse.statusCode().value(),
                            retryAfterMillis = retryAfterMillis(clientResponse.headers().asHttpHeaders()),
                        )
                    )
                }
                .awaitSingle()

            classify(response)
        } catch (exception: WebClientRequestException) {
            if (VendorHttpExceptionClassifier.isTimeout(exception)) {
                return SendNotificationResult.TransientFailure(
                    RetryFailure.of(RetryFailureCode.VENDOR_TIMEOUT, "discord webhook timeout"),
                )
            }

            SendNotificationResult.TransientFailure(
                RetryFailure.external(
                    code = "DISCORD_WEBHOOK_REQUEST_FAILED",
                    message = exception.message ?: "discord webhook request failed",
                    source = FailureSource.NETWORK,
                    category = FailureCategory.TRANSIENT_ERROR,
                )
            )
        } catch (exception: Exception) {
            log.warn("discord webhook send failed by unexpected exception", exception)
            SendNotificationResult.TransientFailure(
                RetryFailure.external(
                    code = "DISCORD_WEBHOOK_UNKNOWN_ERROR",
                    message = exception.message ?: "discord webhook unknown error",
                    source = FailureSource.VENDOR,
                    category = FailureCategory.UNKNOWN,
                )
            )
        }
    }

    private fun classify(response: DiscordWebhookResponse): SendNotificationResult {
        val status = HttpStatusCode.valueOf(response.statusCode)

        return when {
            status.is2xxSuccessful -> SendNotificationResult.Success()
            response.statusCode == HTTP_TOO_MANY_REQUESTS -> SendNotificationResult.RateLimited(
                RetryFailure.of(
                    code = RetryFailureCode.VENDOR_RATE_LIMITED,
                    message = "discord webhook rate limited",
                    statusCode = response.statusCode,
                    retryAfterMillis = response.retryAfterMillis,
                )
            )
            status.is5xxServerError -> SendNotificationResult.TransientFailure(
                RetryFailure.of(
                    code = RetryFailureCode.VENDOR_TRANSIENT_ERROR,
                    message = "discord webhook transient error. status=${response.statusCode}",
                    statusCode = response.statusCode,
                )
            )
            else -> SendNotificationResult.PermanentFailure(
                RetryFailure.external(
                    code = "DISCORD_WEBHOOK_PERMANENT_ERROR",
                    message = "discord webhook permanent error. status=${response.statusCode}",
                    source = FailureSource.VENDOR,
                    category = permanentFailureCategory(response.statusCode),
                    statusCode = response.statusCode,
                )
            )
        }
    }

    private fun permanentFailureCategory(statusCode: Int): FailureCategory {
        return if (statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN) {
            FailureCategory.AUTHORIZATION_ERROR
        } else {
            FailureCategory.VALIDATION_ERROR
        }
    }

    private fun retryAfterMillis(headers: HttpHeaders): Long? {
        return headers.getFirst(HttpHeaders.RETRY_AFTER)
            ?.let(::secondsToMillis)
            ?: headers.getFirst(DISCORD_RESET_AFTER_HEADER)
                ?.let(::secondsToMillis)
    }

    private fun secondsToMillis(value: String): Long? {
        return runCatching {
            BigDecimal(value)
                .takeIf { it >= BigDecimal.ZERO }
                ?.multiply(MILLIS_PER_SECOND)
                ?.setScale(0, RoundingMode.CEILING)
                ?.longValueExact()
        }.getOrNull()
    }

    private data class DiscordWebhookRequest(
        val content: String,
    )

    private data class DiscordWebhookResponse(
        val statusCode: Int,
        val retryAfterMillis: Long?,
    )

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val DISCORD_RESET_AFTER_HEADER = "X-RateLimit-Reset-After"
        val MILLIS_PER_SECOND: BigDecimal = BigDecimal.valueOf(1000)
        val log = LoggerFactory.getLogger(DiscordNotificationSender::class.java)
    }
}
