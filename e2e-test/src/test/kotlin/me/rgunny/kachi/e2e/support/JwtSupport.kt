package me.rgunny.kachi.e2e.support

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * user-service의 공개 API가 요구하는 access token을 테스트가 직접 만든다.
 *
 * user-service에는 OAuth2 로그인밖에 없어 HTTP만으로는 토큰을 받을 수 없다.
 * 테스트가 user-service 컨테이너에 넘긴 secret과 같은 키로 서명하고,
 * claim 모양(`sub`=userId, `type`=ACCESS, `role`)은 user-service의 `JwtTokenProvider`와 맞춘다.
 */
class JwtSupport(secret: String) {
    private val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))

    fun accessToken(userId: String, role: String = "USER"): String {
        val issuedAt = Instant.now()
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(userId)
            .claim("type", "ACCESS")
            .claim("role", role)
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plus(TTL)))
            .signWith(key)
            .compact()
    }

    private companion object {
        val TTL: Duration = Duration.ofMinutes(15)
    }
}
