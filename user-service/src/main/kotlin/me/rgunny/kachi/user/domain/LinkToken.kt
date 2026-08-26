package me.rgunny.kachi.user.domain

import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * 연결 토큰 원문.
 *
 * 사용자에게 한 번 돌려주는 값이며 저장은 [hash]로만 한다.
 * 무작위 32바이트를 base64url로 표기한다.
 */
class LinkToken private constructor(
    val value: String,
    val expiresAt: Instant
) {

    companion object {
        private const val TOKEN_BYTES = 32
        private val random = SecureRandom()
        private val encoder = Base64.getUrlEncoder().withoutPadding()

        fun issue(now: Instant, ttl: Duration): LinkToken {
            require(!ttl.isNegative && !ttl.isZero) { "토큰 만료 시간은 0보다 커야 합니다" }

            val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)

            return LinkToken(value = encoder.encodeToString(bytes), expiresAt = now.plus(ttl))
        }
    }

    fun hash(): LinkTokenHash = LinkTokenHash.of(value)

    override fun toString(): String = "LinkToken(expiresAt=$expiresAt)"
}
