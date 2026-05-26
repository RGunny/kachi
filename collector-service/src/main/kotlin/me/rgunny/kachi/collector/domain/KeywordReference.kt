package me.rgunny.kachi.collector.domain

@JvmInline
value class KeywordReference private constructor(
    val value: String
) {
    companion object {

        fun of(value: String): KeywordReference {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "키워드 참조값은 빈 값일 수 없습니다" }

            return KeywordReference(normalized)
        }
    }
}
