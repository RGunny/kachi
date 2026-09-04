package me.rgunny.kachi.ai.domain.llm

/**
 * 앱이 LLM을 부르는 이유.
 *
 * 상수는 호출 포트의 메서드와 1:1이다. 용도마다 시도할 모델의 순서가 설정에서 따로 정해진다.
 */
enum class LlmUse {
    NEWS_SUMMARY,
    KEYWORD_EXPANSION
}
