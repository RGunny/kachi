package me.rgunny.kachi.notification.domain.retry

enum class FailureCategory {
    TIMEOUT,
    RATE_LIMITED,
    TRANSIENT_ERROR,
    PERMANENT_ERROR,
    VALIDATION_ERROR,
    AUTHORIZATION_ERROR,
    CONFLICT,
    UNKNOWN,
}
