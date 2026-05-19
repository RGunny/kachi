package me.rgunny.kachi.user.domain

@JvmInline
value class KeywordName private constructor(
    val value: String
) {
    companion object {
        fun of(value: String): KeywordName {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "키워드는 빈 값일 수 없습니다" }
            require(normalized.length <= 100) { "키워드는 100자를 초과할 수 없습니다" }

            return KeywordName(normalized)
        }
    }
}
