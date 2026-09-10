package me.rgunny.kachi.story.application.exception

import me.rgunny.kachi.story.domain.inference.InferenceFailure

/**
 * 추론 서버 호출 실패를 분류 결과와 함께 전달하는 예외.
 */
class InferenceException(
    val failure: InferenceFailure,
    cause: Throwable? = null
) : StoryException(
    errorCode = InferenceErrorCode.INFERENCE_CALL_FAILED,
    message = "${failure.code.code} ${failure.message} (target=${failure.target})",
    cause = cause
)
