package me.rgunny.kachi.notification.worker.adapter.outbound.sender.slack

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.notification.application.port.dto.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.SendNotificationResult
import me.rgunny.kachi.notification.application.port.outbound.NotificationSender
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.retry.FailureCategory
import me.rgunny.kachi.notification.retry.FailureSource
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryFailureCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * Slack incoming webhook 기반 NotificationSender adapter.
 *
 * Slack webhook은 성공 시 2xx를 반환하고, rate limit은 429와 Retry-After header로 표현된다.
 * worker는 sender 결과를 core DispatchNotificationService에 돌려주고, core가 RetryPolicy로 RETRY_WAIT/DEAD를 결정한다.
 */
class SlackNotificationSender(
    private val webClient: WebClient,
    private val webhookUrl: String,
    private val timeout: Duration,
) : NotificationSender {

    override fun supports(channel: NotificationChannel): Boolean {
        return channel == NotificationChannel.SLACK
    }

    override suspend fun send(command: SendNotificationCommand): SendNotificationResult {
        require(command.channel == NotificationChannel.SLACK) {
            "SlackNotificationSender only supports SLACK channel"
        }
        require(command.message.isNotBlank()) { "message must not be blank" }

        return try {
            // 1. Slack incoming webhook으로 메시지 본문을 전송한다.
            val response = webClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(SlackWebhookRequest(text = command.message))
                .exchangeToMono { clientResponse ->
                    Mono.just(
                        SlackWebhookResponse(
                            statusCode = clientResponse.statusCode().value(),
                            retryAfterMillis = retryAfterMillis(clientResponse.headers().asHttpHeaders()),
                        )
                    )
                }
                .timeout(timeout)
                .awaitSingle()

            // 2. HTTP status를 core의 표준 sender 결과로 분류한다.
            classify(response)
        } catch (exception: TimeoutException) {
            SendNotificationResult.TransientFailure(
                RetryFailure.of(RetryFailureCode.VENDOR_TIMEOUT, "slack webhook timeout"),
            )
        } catch (exception: WebClientRequestException) {
            SendNotificationResult.TransientFailure(
                RetryFailure.external(
                    code = "SLACK_WEBHOOK_REQUEST_FAILED",
                    message = exception.message ?: "slack webhook request failed",
                    source = FailureSource.NETWORK,
                    category = FailureCategory.TRANSIENT_ERROR,
                )
            )
        } catch (exception: Exception) {
            log.warn("slack webhook send failed by unexpected exception", exception)
            SendNotificationResult.TransientFailure(
                RetryFailure.external(
                    code = "SLACK_WEBHOOK_UNKNOWN_ERROR",
                    message = exception.message ?: "slack webhook unknown error",
                    source = FailureSource.VENDOR,
                    category = FailureCategory.UNKNOWN,
                )
            )
        }
    }

    private fun classify(response: SlackWebhookResponse): SendNotificationResult {
        val status = HttpStatus.valueOf(response.statusCode)

        return when {
            status.is2xxSuccessful -> SendNotificationResult.Success()
            response.statusCode == HTTP_TOO_MANY_REQUESTS -> SendNotificationResult.RateLimited(
                RetryFailure.of(
                    code = RetryFailureCode.VENDOR_RATE_LIMITED,
                    message = "slack webhook rate limited",
                    statusCode = response.statusCode,
                    retryAfterMillis = response.retryAfterMillis,
                )
            )
            status.is5xxServerError -> SendNotificationResult.TransientFailure(
                RetryFailure.of(
                    code = RetryFailureCode.VENDOR_TRANSIENT_ERROR,
                    message = "slack webhook transient error. status=${response.statusCode}",
                    statusCode = response.statusCode,
                )
            )
            else -> SendNotificationResult.PermanentFailure(
                RetryFailure.external(
                    code = "SLACK_WEBHOOK_PERMANENT_ERROR",
                    message = "slack webhook permanent error. status=${response.statusCode}",
                    source = FailureSource.VENDOR,
                    category = if (response.statusCode == HTTP_UNAUTHORIZED || response.statusCode == HTTP_FORBIDDEN) {
                        FailureCategory.AUTHORIZATION_ERROR
                    } else {
                        FailureCategory.VALIDATION_ERROR
                    },
                    statusCode = response.statusCode,
                )
            )
        }
    }

    private fun retryAfterMillis(headers: HttpHeaders): Long? {
        return headers.getFirst(HttpHeaders.RETRY_AFTER)
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
            ?.let { it * 1000 }
    }

    private data class SlackWebhookRequest(
        val text: String,
    )

    private data class SlackWebhookResponse(
        val statusCode: Int,
        val retryAfterMillis: Long?,
    )

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        val log = LoggerFactory.getLogger(SlackNotificationSender::class.java)
    }
}
