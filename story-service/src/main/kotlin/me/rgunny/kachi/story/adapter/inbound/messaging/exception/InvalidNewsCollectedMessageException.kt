package me.rgunny.kachi.story.adapter.inbound.messaging.exception

/**
 * 읽을 수 없거나 지원하지 않는 버전이거나 값이 계약에 맞지 않는 기사 이벤트.
 *
 * 같은 레코드를 다시 받아도 결과가 같으므로 재시도하지 않는다.
 */
class InvalidNewsCollectedMessageException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
