package me.rgunny.kachi.collector.domain

/**
 * 기사의 발췌문.
 *
 * provider가 기사와 함께 주는 짧은 설명이며 본문이 아니다.
 * 연속 공백은 하나로 접고 앞뒤 공백을 지우며, 비어 있을 수 없다. 발췌문이 없는 item은 기사가 되지 않는다.
 * HTML 제거는 형식을 아는 provider adapter의 몫이다.
 */
@JvmInline
value class NewsExcerpt private constructor(
    val value: String
) {
    companion object {
        const val MAX_LENGTH = 1000

        private val WHITESPACE = Regex("[\\s\\p{Z}]+")

        /**
         * 상한을 넘는 부분은 잘라 낸다.
         */
        fun of(value: String): NewsExcerpt {
            val normalized = value.replace(WHITESPACE, " ").trim()

            require(normalized.isNotEmpty()) { "발췌문은 빈 값일 수 없습니다" }

            return NewsExcerpt(normalized.take(MAX_LENGTH))
        }
    }
}
