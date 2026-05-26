package me.rgunny.kachi.collector.domain

@JvmInline
value class CollectedKeyword private constructor(
    val value: String
) {
    companion object {
        fun of(value: String): CollectedKeyword {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "수집 키워드는 빈 값일 수 없습니다" }

            return CollectedKeyword(normalized)
        }
    }
}
