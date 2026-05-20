package me.rgunny.kachi.user.adapter.`in`.web.security

import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {
    private val issuedAt = Instant.parse("2026-05-20T00:00:00Z")
    private val tokenProvider = JwtTokenProvider(
        secret = SECRET,
        accessTokenTtl = Duration.ofMinutes(15),
        refreshTokenTtl = Duration.ofDays(14),
        clock = Clock.fixed(issuedAt, ZoneOffset.UTC)
    )
    private val filter = JwtAuthenticationFilter(tokenProvider)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Nested
    @DisplayName("doFilterInternal()")
    inner class DoFilterInternal {

        @Test
        @DisplayName("유효한 access token이면 인증 정보를 저장한다")
        fun authenticateWithAccessToken() {
            val userId = UserId.newId()
            val token = tokenProvider.createAccessToken(userId, UserRole.USER)
            val request = requestWithBearerToken(token.value)

            filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

            val authentication = SecurityContextHolder.getContext().authentication
            assertNotNull(authentication)
            val principal = assertIs<AuthenticatedUser>(authentication.principal)
            assertEquals(userId, principal.userId)
            assertEquals(UserRole.USER, principal.role)
            assertTrue(authentication.isAuthenticated)
            assertEquals("ROLE_USER", authentication.authorities.first().authority)
        }

        @Test
        @DisplayName("refresh token은 인증 정보로 저장하지 않는다")
        fun skipRefreshToken() {
            val token = tokenProvider.createRefreshToken(UserId.newId())
            val request = requestWithBearerToken(token.value)

            filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

            assertNull(SecurityContextHolder.getContext().authentication)
        }

        @Test
        @DisplayName("잘못된 token이면 인증 정보로 저장하지 않는다")
        fun skipInvalidToken() {
            val request = requestWithBearerToken("invalid-token")

            filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

            assertNull(SecurityContextHolder.getContext().authentication)
        }

        @Test
        @DisplayName("Bearer token이 없으면 인증 정보로 저장하지 않는다")
        fun skipMissingBearerToken() {
            filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), MockFilterChain())

            assertNull(SecurityContextHolder.getContext().authentication)
        }

        @Test
        @DisplayName("기존 인증 정보가 있으면 덮어쓰지 않는다")
        fun keepExistingAuthentication() {
            val existingAuthentication = TestingAuthenticationToken(
                "existing",
                "credentials",
                "ROLE_ADMIN"
            )
            SecurityContextHolder.getContext().authentication = existingAuthentication
            val token = tokenProvider.createAccessToken(UserId.newId(), UserRole.USER)
            val request = requestWithBearerToken(token.value)

            filter.doFilter(request, MockHttpServletResponse(), MockFilterChain())

            assertEquals(existingAuthentication, SecurityContextHolder.getContext().authentication)
            assertFalse(SecurityContextHolder.getContext().authentication?.principal is AuthenticatedUser)
        }
    }

    private fun requestWithBearerToken(token: String): MockHttpServletRequest {
        return MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer $token")
        }
    }

    companion object {
        private const val SECRET = "morCcncONBqndWq56eP75u8LgYDg+HLWlfhqugyfDA4="
    }
}
