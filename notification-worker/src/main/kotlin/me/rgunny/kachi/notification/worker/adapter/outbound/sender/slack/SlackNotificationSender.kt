package me.rgunny.kachi.notification.worker.adapter.outbound.sender.slack

import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.notification.application.port.dto.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.SendNotificationResult
import me.rgunny.kachi.notification.application.port.outbound.NotificationSender
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.retry.FailureCategory
import me.rgunny.kachi.notification.retry.FailureSource
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryFailureCode
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.VendorHttpExceptionClassifier
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.slack.dto.SlackWebhookResult
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientRequestException

/**
 * Slack incoming webhook 기반 NotificationSender adapter.
 *
 * Slack webhook은 성공 시 2xx를 반환하고, rate limit은 429와 Retry-After header로 표현된다.
 * worker는 sender 결과를 core DispatchNotificationService에 돌려주고, core가 RetryPolicy로 RETRY_WAIT/DEAD를 결정한다.
 * coroutine 취소는 외부 채널 장애가 아니므로 failure 결과로 변환하지 않는다.
 */
class SlackNotificationSender internal constructor(
    private val client: SlackWebhookClient,
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
            val response = client.send(text = command.message)

            classify(response)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: WebClientRequestException) {
            if (VendorHttpExceptionClassifier.isTimeout(exception)) {
                return SendNotificationResult.TransientFailure(
                    RetryFailure.of(RetryFailureCode.VENDOR_TIMEOUT, "slack webhook timeout"),
                )
            }

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

    private fun classify(response: SlackWebhookResult): SendNotificationResult {
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

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        val log = LoggerFactory.getLogger(SlackNotificationSender::class.java)
    }
}
