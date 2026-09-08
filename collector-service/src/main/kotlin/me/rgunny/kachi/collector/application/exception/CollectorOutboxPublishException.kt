package me.rgunny.kachi.collector.application.exception

/**
 * outbox 이벤트를 broker로 내보내지 못했을 때 발행 어댑터가 던지는 예외.
 *
 * [retryable]이 false면 같은 payload로 다시 보내도 결과가 달라지지 않는 실패이므로 재시도 한도와 무관하게 DEAD로 간다.
 * 이 예외가 아닌 실패는 원인을 알 수 없으므로 재시도 대상으로 본다.
 */
class CollectorOutboxPublishException(
    errorCode: CollectorOutboxErrorCode,
    val retryable: Boolean,
    detail: String? = null,
    cause: Throwable? = null
) : CollectorException(errorCode, messageOf(errorCode, detail), cause)
