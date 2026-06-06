package me.rgunny.kachi.ai.domain.llm

@JvmInline
value class LlmModelName private constructor(
    val value: String
) {
    companion object {

        fun of(value: String): LlmModelName {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "LLM model 이름은 빈 값일 수 없습니다" }

            return LlmModelName(normalized)
        }
    }
}
