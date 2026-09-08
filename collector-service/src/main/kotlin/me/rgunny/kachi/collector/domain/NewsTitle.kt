package me.rgunny.kachi.collector.domain

/**
 * 기사 제목. 앞뒤 공백을 지운 원문이며 비어 있을 수 없다.
 */
@JvmInline
value class NewsTitle private constructor(
    val value: String
) {
    companion object {

        fun of(value: String): NewsTitle {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "뉴스 제목은 빈 값일 수 없습니다" }

            return NewsTitle(normalized)
        }
    }
}
