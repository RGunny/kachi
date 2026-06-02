package me.rgunny.kachi.collector.domain

import java.security.MessageDigest

@JvmInline
value class NewsUrl private constructor(
    val value: String
) {
    val hash: String
        get() = sha256(value.toByteArray())

    companion object {

        fun of(value: String): NewsUrl {
            val normalized = value.trim()

            require(normalized.isNotBlank()) { "뉴스 URL은 빈 값일 수 없습니다" }

            return NewsUrl(normalized)
        }

        private fun sha256(input: ByteArray): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input)
                .joinToString("") { "%02x".format(it) }
    }
}
