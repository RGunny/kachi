package me.rgunny.kachi.ai.adapter.inbound.messaging.exception

/**
 * 계약에 맞지 않아 재시도 없이 DLT로 보내는 story 이벤트 메시지의 실패.
 */
class InvalidStoryEventMessageException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
