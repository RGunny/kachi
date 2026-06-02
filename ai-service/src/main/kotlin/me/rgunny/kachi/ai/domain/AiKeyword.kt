package me.rgunny.kachi.ai.domain

@JvmInline
value class AiKeyword private constructor(
    val value: String
) {
    companion object {

        fun of(value: String): AiKeyword {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "AI 키워드는 빈 값일 수 없습니다" }

            return AiKeyword(normalized)
        }
    }
}
