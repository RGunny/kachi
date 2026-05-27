package me.rgunny.kachi.collector.domain

enum class ProviderFailureReason {
    TIMEOUT,
    RATE_LIMITED,
    CLIENT_ERROR,
    SERVER_ERROR,
    NETWORK_ERROR,
    INVALID_RESPONSE,
    UNKNOWN
}
