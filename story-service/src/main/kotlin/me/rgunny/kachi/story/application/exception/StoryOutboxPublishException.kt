package me.rgunny.kachi.story.application.exception

/**
 * outbox 이벤트를 broker로 내보내지 못했을 때 발행 어댑터가 던지는 예외.
 */
class StoryOutboxPublishException(
    errorCode: StoryOutboxErrorCode,
    val retryable: Boolean,
    detail: String? = null,
    cause: Throwable? = null
) : StoryException(errorCode, messageOf(errorCode, detail), cause)
