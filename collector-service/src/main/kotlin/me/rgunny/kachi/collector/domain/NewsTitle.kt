package me.rgunny.kachi.collector.domain

import java.security.MessageDigest

@JvmInline
value class NewsTitle private constructor(
    val value: String
) {
    // URL이 다른 유사 제목 뉴스를 빠르게 찾기 위한 후보값이다.
    val fingerprint: String
        get() {
            val normalized = value
                .lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()
            return sha256(normalized.toByteArray())
        }

    companion object {

        fun of(value: String): NewsTitle {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "뉴스 제목은 빈 값일 수 없습니다" }

            return NewsTitle(normalized)
        }

        private fun sha256(input: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input)
                .joinToString("") { "%02x".format(it) }
    }
}
