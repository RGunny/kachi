package me.rgunny.kachi.ai.domain.llm

/**
 * 제공자 계정의 과금 방식.
 *
 * 설정 검증과 실호출 검증의 opt-in 기준이다. [SELF_HOSTED]만 인증 키가 없어도 된다.
 */
enum class LlmBilling {
    FREE_TIER,
    METERED,
    SUBSCRIPTION,
    SELF_HOSTED
}
