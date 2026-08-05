package me.rgunny.kachi.notification.application.port.dto

import me.rgunny.kachi.notification.retry.RetryFailure

/**
 * 외부 채널 발송 결과.
 *
 * rate limit은 재시도 대상이지만 vendor 장애와는 성격이 다르므로 transient failure와 분리한다.
 */
sealed interface SendNotificationResult {

    data class Success(
        val providerMessageId: String? = null,
    ) : SendNotificationResult

    data class RateLimited(
        val failure: RetryFailure,
    ) : SendNotificationResult

    data class TransientFailure(
        val failure: RetryFailure,
    ) : SendNotificationResult

    data class PermanentFailure(
        val failure: RetryFailure,
    ) : SendNotificationResult
}
