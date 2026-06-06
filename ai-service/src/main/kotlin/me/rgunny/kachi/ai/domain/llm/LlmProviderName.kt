package me.rgunny.kachi.ai.domain.llm

@JvmInline
value class LlmProviderName private constructor(
    val value: String
) {
    companion object {

        fun of(value: String): LlmProviderName {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "LLM provider 이름은 빈 값일 수 없습니다" }

            return LlmProviderName(normalized)
        }
    }
}
