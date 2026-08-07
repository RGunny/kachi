package me.rgunny.kachi.ai.domain.run

enum class AiFailureReason {
    TIMEOUT,
    RATE_LIMITED,
    CLIENT_ERROR,
    SERVER_ERROR,
    NETWORK_ERROR,
    INVALID_RESPONSE,

    /**
     * 요약 대상 뉴스가 없는 경우는 skip으로 분리했다(ADR 021).
     * 새 실행은 이 값을 쓰지 않고, 이전 실행 기록을 읽기 위해 남긴다.
     */
    EMPTY_INPUT,
    UNKNOWN
}
