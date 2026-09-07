package me.rgunny.kachi.ai.domain.llm

/**
 * LLM 요청·응답의 모양.
 *
 * 코드가 분기하는 유일한 축이다. 제공자가 달라도 규격이 같으면 같은 adapter가 부른다.
 * 규격마다 adapter를 전략 패턴으로 두고 이 enum을 키로 고르므로, 규격이 늘면 상수와 adapter가 하나씩 는다.
 */
enum class LlmApi {
    OPENAI_CHAT_COMPLETIONS
}
