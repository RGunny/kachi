package me.rgunny.kachi.user.adapter.`in`.web.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private val issuedAt = Instant.parse("2026-05-20T00:00:00Z")
    private val clock = Clock.fixed(issuedAt, ZoneOffset.UTC)
    private val accessTokenTtl = Duration.ofMinutes(15)
    private val refreshTokenTtl = Duration.ofDays(14)
    private val tokenProvider = JwtTokenProvider(
        secret = SECRET,
        accessTokenTtl = accessTokenTtl,
        refreshTokenTtl = refreshTokenTtl,
        clock = clock
    )

    @Nested
    @DisplayName("createAccessToken()")
    inner class CreateAccessToken {

        @Test
        @DisplayName("사용자 ID와 권한을 포함한 access token을 생성한다")
        fun createAccessToken() {
            val userId = UserId.newId()

            val token = tokenProvider.createAccessToken(userId, UserRole.USER)

            val claims = tokenProvider.parse(token.value)
            assertEquals(userId, claims.userId)
            assertEquals(JwtTokenType.ACCESS, claims.type)
            assertEquals(UserRole.USER, claims.role)
            assertEquals(issuedAt.plus(Duration.ofMinutes(15)), token.expiresAt)
        }
    }

    @Nested
    @DisplayName("createRefreshToken()")
    inner class CreateRefreshToken {

        @Test
        @DisplayName("사용자 ID를 포함한 refresh token을 생성한다")
        fun createRefreshToken() {
            val userId = UserId.newId()

            val token = tokenProvider.createRefreshToken(userId)

            val claims = tokenProvider.parse(token.value)
            assertEquals(userId, claims.userId)
            assertEquals(JwtTokenType.REFRESH, claims.type)
            assertEquals(null, claims.role)
            assertEquals(issuedAt.plus(Duration.ofDays(14)), token.expiresAt)
        }
    }

    @Nested
    @DisplayName("isValid()")
    inner class IsValid {

        @Test
        @DisplayName("서명과 만료 시간이 유효하면 true를 반환한다")
        fun validToken() {
            val token = tokenProvider.createAccessToken(UserId.newId(), UserRole.USER)

            assertTrue(tokenProvider.isValid(token.value))
        }

        @Test
        @DisplayName("다른 secret으로 서명된 token이면 false를 반환한다")
        fun invalidSignature() {
            val token = tokenProvider.createAccessToken(UserId.newId(), UserRole.USER)
            val otherTokenProvider = JwtTokenProvider(
                secret = OTHER_SECRET,
                accessTokenTtl = accessTokenTtl,
                refreshTokenTtl = refreshTokenTtl,
                clock = clock
            )

            assertFalse(otherTokenProvider.isValid(token.value))
        }

        @Test
        @DisplayName("만료된 token이면 false를 반환한다")
        fun expiredToken() {
            val token = tokenProvider.createAccessToken(UserId.newId(), UserRole.USER)
            val expiredClock = Clock.fixed(issuedAt.plus(Duration.ofMinutes(16)), ZoneOffset.UTC)
            val expiredTokenProvider = JwtTokenProvider(
                secret = SECRET,
                accessTokenTtl = accessTokenTtl,
                refreshTokenTtl = refreshTokenTtl,
                clock = expiredClock
            )

            assertFalse(expiredTokenProvider.isValid(token.value))
        }

        @Test
        @DisplayName("type claim이 없으면 false를 반환한다")
        fun missingTypeClaim() {
            val token = createSignedToken(claims = mapOf("role" to UserRole.USER.name))

            assertFalse(tokenProvider.isValid(token))
        }

        @Test
        @DisplayName("access token에 role claim이 없으면 false를 반환한다")
        fun accessTokenWithoutRoleClaim() {
            val token = createSignedToken(claims = mapOf("type" to JwtTokenType.ACCESS.name))

            assertFalse(tokenProvider.isValid(token))
        }
    }

    private fun createSignedToken(claims: Map<String, Any>): String {
        val secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET))

        return Jwts.builder()
            .subject(UserId.newId().value.toString())
            .claims(claims)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plus(accessTokenTtl)))
            .signWith(secretKey)
            .compact()
    }

    companion object {
        // openssl rand -base64 32
        private const val SECRET = "morCcncONBqndWq56eP75u8LgYDg+HLWlfhqugyfDA4="
        private const val OTHER_SECRET = "03w0ZE7pE7EVgMspJoAu1xsvKp5QlHv5p9mtgWJCqX0="
    }
}
