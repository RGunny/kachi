package me.rgunny.kachi.ai.domain.llm

@JvmInline
value class PromptVersion private constructor(
    val value: String
) {
    companion object {

        fun of(value: String): PromptVersion {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "프롬프트 버전은 빈 값일 수 없습니다" }

            return PromptVersion(normalized)
        }
    }
}
