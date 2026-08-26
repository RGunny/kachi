package me.rgunny.kachi.ai.domain.llm

/**
 * LLM 실패가 발생한 원천.
 *
 * ai-service의 외부 I/O는 LLM provider와 MongoDB뿐이고 MongoDB 실패는 이 모델을 거치지 않는다.
 */
enum class LlmFailureSource {
    PROVIDER,
    NETWORK,
    APPLICATION
}
