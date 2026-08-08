package me.rgunny.kachi.notification.application.port.inbound.dispatch.model

/**
 * dispatch 과정에서 발생한 실패의 재처리 가능 여부.
 */
enum class DispatchFailureClassification {
    NONE,
    RETRYABLE,
    NON_RETRYABLE,
}
