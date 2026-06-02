package me.rgunny.kachi.ai.domain

@JvmInline
value class ExpandedKeyword private constructor(
    val value: String
) {
    companion object {

        fun of(value: String): ExpandedKeyword {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "확장 키워드는 빈 값일 수 없습니다" }

            return ExpandedKeyword(normalized)
        }
    }
}
