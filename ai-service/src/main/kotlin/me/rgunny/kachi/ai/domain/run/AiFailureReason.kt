package me.rgunny.kachi.ai.domain.run

/**
 * AI 처리 실패 사유.
 */
enum class AiFailureReason {
    TIMEOUT,
    RATE_LIMITED,

    /** 요청 본문 거부. */
    CLIENT_ERROR,
    SERVER_ERROR,
    NETWORK_ERROR,
    INVALID_RESPONSE,

    /** 호출 가능한 LLM 모델이 없어 요청을 보내지 못한 실패. */
    PROVIDER_UNAVAILABLE,

    /** 제공이 끝났거나 이름이 틀린 모델 호출. */
    MODEL_NOT_FOUND,

    /** 제공자 계정 문제. */
    ACCOUNT_ERROR,

    /**
     * 요약 대상 뉴스가 없었던 실패. skip으로 분리해(ADR 021) 새 실행은 이 값을 쓰지 않고, 이전 실행 기록을 읽기 위해 남긴다.
     */
    EMPTY_INPUT,
    UNKNOWN
}
