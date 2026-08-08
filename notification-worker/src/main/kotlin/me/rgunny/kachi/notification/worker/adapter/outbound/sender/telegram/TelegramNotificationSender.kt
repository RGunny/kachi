package me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram

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
import me.rgunny.kachi.notification.worker.adapter.outbound.sender.telegram.dto.TelegramSendMessageResult
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.web.reactive.function.client.WebClientRequestException

/**
 * Telegram Bot API sendMessage 기반 NotificationSender adapter.
 *
 * Telegram Bot API 응답은 HTTP status와 별개로 JSON body의 ok/error_code/parameters.retry_after를 같이 확인해야 한다.
 * coroutine 취소는 외부 채널 장애가 아니므로 failure 결과로 변환하지 않는다.
 */
class TelegramNotificationSender internal constructor(
    private val client: TelegramSendMessageClient,
) : NotificationSender {

    override fun supports(channel: NotificationChannel): Boolean {
        return channel == NotificationChannel.TELEGRAM
    }

    override suspend fun send(command: SendNotificationCommand): SendNotificationResult {
        require(command.channel == NotificationChannel.TELEGRAM) {
            "TelegramNotificationSender only supports TELEGRAM channel"
        }
        require(command.message.isNotBlank()) { "message must not be blank" }

        if (command.recipient.isBlank()) {
            return SendNotificationResult.PermanentFailure(
                RetryFailure.external(
                    code = "TELEGRAM_INVALID_RECIPIENT",
                    message = "telegram chat_id must not be blank",
                    source = FailureSource.VENDOR,
                    category = FailureCategory.VALIDATION_ERROR,
                )
            )
        }

        if (command.message.length > MAX_TEXT_LENGTH) {
            return SendNotificationResult.PermanentFailure(
                RetryFailure.external(
                    code = "TELEGRAM_MESSAGE_TOO_LONG",
                    message = "telegram message text length must be less than or equal to $MAX_TEXT_LENGTH",
                    source = FailureSource.VENDOR,
                    category = FailureCategory.VALIDATION_ERROR,
                )
            )
        }

        return try {
            val response = client.sendMessage(
                chatId = command.recipient,
                text = command.message,
            )

            classify(response)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: WebClientRequestException) {
            if (VendorHttpExceptionClassifier.isTimeout(exception)) {
                return SendNotificationResult.TransientFailure(
                    RetryFailure.of(RetryFailureCode.VENDOR_TIMEOUT, "telegram sendMessage timeout"),
                )
            }

            SendNotificationResult.TransientFailure(
                RetryFailure.external(
                    code = "TELEGRAM_SEND_MESSAGE_REQUEST_FAILED",
                    message = exception.message ?: "telegram sendMessage request failed",
                    source = FailureSource.NETWORK,
                    category = FailureCategory.TRANSIENT_ERROR,
                )
            )
        } catch (exception: Exception) {
            log.warn("telegram sendMessage failed by unexpected exception", exception)
            SendNotificationResult.TransientFailure(
                RetryFailure.external(
                    code = "TELEGRAM_SEND_MESSAGE_UNKNOWN_ERROR",
                    message = exception.message ?: "telegram sendMessage unknown error",
                    source = FailureSource.VENDOR,
                    category = FailureCategory.UNKNOWN,
                )
            )
        }
    }

    private fun classify(response: TelegramSendMessageResult): SendNotificationResult {
        val status = HttpStatusCode.valueOf(response.statusCode)

        return when {
            status.is2xxSuccessful && response.ok -> SendNotificationResult.Success()
            response.errorCode == HTTP_TOO_MANY_REQUESTS || response.statusCode == HTTP_TOO_MANY_REQUESTS -> {
                SendNotificationResult.RateLimited(
                    RetryFailure.of(
                        code = RetryFailureCode.VENDOR_RATE_LIMITED,
                        message = failureMessage("telegram sendMessage rate limited", response),
                        statusCode = response.statusCode,
                        retryAfterMillis = response.retryAfterMillis,
                    )
                )
            }
            status.is5xxServerError -> SendNotificationResult.TransientFailure(
                RetryFailure.of(
                    code = RetryFailureCode.VENDOR_TRANSIENT_ERROR,
                    message = failureMessage("telegram sendMessage transient error", response),
                    statusCode = response.statusCode,
                )
            )
            else -> SendNotificationResult.PermanentFailure(
                RetryFailure.external(
                    code = "TELEGRAM_SEND_MESSAGE_PERMANENT_ERROR",
                    message = failureMessage("telegram sendMessage permanent error", response),
                    source = FailureSource.VENDOR,
                    category = permanentFailureCategory(response),
                    statusCode = response.statusCode,
                )
            )
        }
    }

    private fun permanentFailureCategory(response: TelegramSendMessageResult): FailureCategory {
        return if (
            response.statusCode == HTTP_UNAUTHORIZED ||
            response.statusCode == HTTP_FORBIDDEN ||
            response.errorCode == HTTP_UNAUTHORIZED ||
            response.errorCode == HTTP_FORBIDDEN
        ) {
            FailureCategory.AUTHORIZATION_ERROR
        } else {
            FailureCategory.VALIDATION_ERROR
        }
    }

    private fun failureMessage(prefix: String, response: TelegramSendMessageResult): String {
        val description = response.description
        return if (description.isNullOrBlank()) {
            "$prefix. status=${response.statusCode}"
        } else {
            "$prefix. status=${response.statusCode}, description=$description"
        }
    }

    private companion object {
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val MAX_TEXT_LENGTH = 4096
        val log = LoggerFactory.getLogger(TelegramNotificationSender::class.java)
    }
}
