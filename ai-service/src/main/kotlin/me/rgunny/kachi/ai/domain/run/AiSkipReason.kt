package me.rgunny.kachi.ai.domain.run

/**
 * 키워드를 요약하지 않고 건너뛴 이유 분류.
 *
 * skip은 실패가 아니다. 조치할 것이 없는 실행과 원인을 봐야 하는 실행을 실행 기록에서 구분하기 위해 나눈다.
 */
enum class AiSkipReason {
    /** 이 구간에 요약할 뉴스가 없음. */
    NO_INPUT,

    /** 전역 LLM 장애로 이번 실행에서 호출하지 않음. */
    PROVIDER_UNAVAILABLE
}
