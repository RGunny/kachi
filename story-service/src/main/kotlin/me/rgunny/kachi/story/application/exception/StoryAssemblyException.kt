package me.rgunny.kachi.story.application.exception

/**
 * 기사를 story에 붙이지 못했음을 알리는 예외.
 */
class StoryAssemblyException(
    errorCode: StoryAssemblyErrorCode,
    detail: String? = null,
    cause: Throwable? = null
) : StoryException(
    errorCode = errorCode,
    message = messageOf(errorCode, detail),
    cause = cause
)
