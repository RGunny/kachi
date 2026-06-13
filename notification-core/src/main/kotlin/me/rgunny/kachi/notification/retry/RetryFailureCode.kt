package me.rgunny.kachi.notification.retry

enum class RetryFailureCode(
    val code: String,
    val defaultMessage: String,
    val source: FailureSource,
    val category: FailureCategory,
) {
    VENDOR_TIMEOUT(
        code = "VENDOR_TIMEOUT",
        defaultMessage = "vendor timeout",
        source = FailureSource.VENDOR,
        category = FailureCategory.TIMEOUT,
    ),
    VENDOR_RATE_LIMITED(
        code = "VENDOR_RATE_LIMITED",
        defaultMessage = "vendor rate limited",
        source = FailureSource.VENDOR,
        category = FailureCategory.RATE_LIMITED,
    ),
    VENDOR_TRANSIENT_ERROR(
        code = "VENDOR_TRANSIENT_ERROR",
        defaultMessage = "vendor transient error",
        source = FailureSource.VENDOR,
        category = FailureCategory.TRANSIENT_ERROR,
    ),
    INVALID_RECIPIENT(
        code = "INVALID_RECIPIENT",
        defaultMessage = "invalid recipient",
        source = FailureSource.VENDOR,
        category = FailureCategory.VALIDATION_ERROR,
    ),
    DISPATCH_NOT_READY(
        code = "DISPATCH_NOT_READY",
        defaultMessage = "dispatch not ready",
        source = FailureSource.APPLICATION,
        category = FailureCategory.TRANSIENT_ERROR,
    ),
}
