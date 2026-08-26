package me.rgunny.kachi.user.domain

import java.security.MessageDigest

/**
 * 연결 토큰의 SHA-256 해시.
 * 저장소에는 이것만 남는다.
 */
class LinkTokenHash private constructor(
    val value: ByteArray
) {

    companion object {
        private const val LENGTH = 32

        fun of(rawToken: String): LinkTokenHash {
            return LinkTokenHash(MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8)))
        }

        fun restore(value: ByteArray): LinkTokenHash {
            require(value.size == LENGTH) { "토큰 해시는 ${LENGTH}바이트여야 합니다" }

            return LinkTokenHash(value.copyOf())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LinkTokenHash) return false

        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = "LinkTokenHash(****)"
}
