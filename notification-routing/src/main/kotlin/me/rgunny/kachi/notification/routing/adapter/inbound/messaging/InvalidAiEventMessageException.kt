package me.rgunny.kachi.notification.routing.adapter.inbound.messaging

/**
 * 읽을 수 없거나 지원하지 않는 버전의 ai 이벤트.
 * 같은 레코드를 다시 받아도 결과가 같으므로 재시도하지 않는다.
 */
class InvalidAiEventMessageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
