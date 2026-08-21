package me.rgunny.kachi.ai.adapter.outbound.llm.openai

/**
 * OpenAI 계열 chat completions adapter가 지원하는 provider 종류.
 *
 * 각 value는 `kachi.ai.providers.<provider>` 설정 key와 매칭된다.
 */
enum class OpenAiProviderType(
    val value: String
) {
    OPENROUTER("openrouter"),
    GROQ("groq"),
    TOGETHER("together"),
    CEREBRAS("cerebras"),
    MISTRAL("mistral")
}
