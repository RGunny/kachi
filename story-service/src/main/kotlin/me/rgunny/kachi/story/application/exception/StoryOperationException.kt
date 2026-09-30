package me.rgunny.kachi.story.application.exception

/**
 * story 조회·병합·분리 운영 요청을 처리하지 못했음을 알리는 예외.
 */
class StoryOperationException(
    errorCode: StoryOperationErrorCode,
    detail: String? = null,
    cause: Throwable? = null
) : StoryException(
    errorCode = errorCode,
    message = messageOf(errorCode, detail),
    cause = cause
)
