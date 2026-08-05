package me.rgunny.kachi.notification.retry

enum class FailureSource {
    VENDOR,
    BROKER,
    DATABASE,
    NETWORK,
    APPLICATION,
}
