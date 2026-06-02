package me.rgunny.kachi.ai.domain

enum class AiFailureReason {
    TIMEOUT,
    RATE_LIMITED,
    CLIENT_ERROR,
    SERVER_ERROR,
    NETWORK_ERROR,
    INVALID_RESPONSE,
    EMPTY_INPUT,
    UNKNOWN
}
