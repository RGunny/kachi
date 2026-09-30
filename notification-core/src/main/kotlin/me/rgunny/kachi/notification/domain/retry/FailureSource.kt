package me.rgunny.kachi.notification.domain.retry

enum class FailureSource {
    VENDOR,
    BROKER,
    DATABASE,
    NETWORK,
    APPLICATION,
}
