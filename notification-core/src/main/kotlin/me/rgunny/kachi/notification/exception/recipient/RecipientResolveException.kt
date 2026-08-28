package me.rgunny.kachi.notification.exception.recipient

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.exception.NotificationErrorCode
import me.rgunny.kachi.notification.retry.RetryFailure
import me.rgunny.kachi.notification.retry.RetryableException

/**
 * 수신 주소 조회가 실패했을 때의 예외. 원천 장애·응답 지연·캐시 장애가 여기 해당하며
 * 발송 실패와 같은 재시도 정책으로 RETRY_WAIT 또는 한도에 닿으면 DEAD가 된다.
 *
 * 메시지에 수신 주소나 원천 응답 본문을 넣지 않는다.
 */
class RecipientResolveException(
    val recipientId: String,
    val channel: NotificationChannel,
    failure: RetryFailure,
    cause: Throwable? = null,
) : RetryableException(
    errorCode = NotificationErrorCode.RECIPIENT_RESOLVE_FAILED,
    failure = failure,
    cause = cause,
)
