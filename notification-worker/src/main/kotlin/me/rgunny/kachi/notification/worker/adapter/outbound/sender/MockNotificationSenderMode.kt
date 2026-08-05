package me.rgunny.kachi.notification.worker.adapter.outbound.sender

enum class MockNotificationSenderMode {
    SUCCESS,
    TRANSIENT_FAILURE,
    RATE_LIMITED,
    PERMANENT_FAILURE,
}
