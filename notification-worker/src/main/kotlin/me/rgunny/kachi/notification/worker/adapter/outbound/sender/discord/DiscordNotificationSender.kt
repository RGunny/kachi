package me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord

import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationResult
import me.rgunny.kachi.notification.application.port.outbound.sender.NotificationSender
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.retry.FailureCategory
import me.rgunny.kachi.notification.retry.FailureSource
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryFailureCode
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.VendorHttpExceptionClassifier
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.discord.dto.DiscordWebhookResult
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.WebClientRequestException

/**
 * Discord incoming webhook 기반 NotificationSender adapter.
 *
 * Discord webhook은 wait=false 기본값에서 성공 시 204 No Content를 반환할 수 있고,
 * rate limit은 429와 Retry-After/X-RateLimit-Reset-After header로 표현된다.
 * coroutine 취소는 외부 채널 장애가 아니므로 failure 결과로 변환하지 않는다.
 */
class DiscordNotificationSender internal constructor(
    private val client: DiscordWebhookClient,
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
            val response = client.send(content = command.message)

            classify(response)
        } catch (exception: CancellationException) {
            throw exception
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

    private fun classify(response: DiscordWebhookResult): SendNotificationResult {
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

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        val log = LoggerFactory.getLogger(DiscordNotificationSender::class.java)
    }
}
