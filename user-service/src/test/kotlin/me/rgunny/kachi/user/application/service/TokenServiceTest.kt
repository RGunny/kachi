package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.exception.InvalidTokenException
import me.rgunny.kachi.user.application.port.`in`.IssueAuthTokensCommand
import me.rgunny.kachi.user.application.port.`in`.IssueAuthTokensResult
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenCommand
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenResult
import me.rgunny.kachi.user.application.port.out.RefreshTokenStorePort
import me.rgunny.kachi.user.application.port.out.TokenPort
import me.rgunny.kachi.user.application.port.out.UserPersistencePort
import me.rgunny.kachi.user.application.token.IssuedToken
import me.rgunny.kachi.user.application.token.ParsedToken
import me.rgunny.kachi.user.application.token.StoredRefreshToken
import me.rgunny.kachi.user.application.token.TokenType
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.ProviderUserId
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("TokenService")
class TokenServiceTest {
    private val userId = UserId.newId()
    private val registeredAt = Instant.parse("2026-05-20T00:00:00Z")
    private val loggedInAt = Instant.parse("2026-05-20T01:00:00Z")
    private val clock = Clock.fixed(loggedInAt, ZoneOffset.UTC)

    @Nested
    @DisplayName("issue()")
    inner class Issue {

        @Test
        @DisplayName("활성 사용자에게 access token과 refresh token을 발급하고 refresh token을 저장한다")
        fun issueAuthTokens() {
            val tokenPort = FakeTokenPort()
            val service = tokenService(tokenPort = tokenPort)

            val result = service.issue(IssueAuthTokensCommand(userId))

            assertEquals(userId, tokenPort.issuedAccessTokenUserId)
            assertEquals(UserRole.USER, tokenPort.issuedAccessTokenRole)
            assertEquals(userId, tokenPort.issuedRefreshTokenUserId)
            assertEquals(loggedInAt, service.userPersistencePort.savedUser?.lastLoginAt)
            assertEquals("new-refresh-token-id", service.refreshTokenStorePort.savedToken?.id)
            assertEquals(userId, service.refreshTokenStorePort.savedToken?.userId)
            assertEquals("new-access-token", result.accessToken)
            assertEquals("new-refresh-token", result.refreshToken)
        }

        @Test
        @DisplayName("활성 사용자가 아니면 토큰을 발급하지 않는다")
        fun rejectInactiveUser() {
            val tokenPort = FakeTokenPort()
            val service = tokenService(
                tokenPort = tokenPort,
                users = mapOf(userId to user(status = UserStatus.DELETED))
            )

            assertFailsWith<InactiveUserException> {
                service.issue(IssueAuthTokensCommand(userId))
            }

            assertEquals(null, tokenPort.issuedAccessTokenUserId)
            assertEquals(null, tokenPort.issuedRefreshTokenUserId)
            assertEquals(null, service.refreshTokenStorePort.savedToken)
        }
    }

    @Nested
    @DisplayName("refresh()")
    inner class Refresh {

        @Test
        @DisplayName("refresh token으로 access token과 refresh token을 갱신한다")
        fun refreshToken() {
            val tokenPort = FakeTokenPort()
            val service = tokenService(tokenPort = tokenPort)

            val result = service.refresh(RefreshTokenCommand(refreshToken = "valid-refresh-token"))

            assertEquals("valid-refresh-token", tokenPort.parsedToken)
            assertEquals(userId to "refresh-token-id", service.refreshTokenStorePort.checkedToken)
            assertEquals(userId, tokenPort.issuedAccessTokenUserId)
            assertEquals(UserRole.USER, tokenPort.issuedAccessTokenRole)
            assertEquals(userId, tokenPort.issuedRefreshTokenUserId)
            assertEquals("refresh-token-id", service.refreshTokenStorePort.rotatedOldTokenId)
            assertEquals("new-refresh-token-id", service.refreshTokenStorePort.rotatedNewToken?.id)
            assertEquals("new-access-token", result.accessToken)
            assertEquals("new-refresh-token", result.refreshToken)
        }

        @Test
        @DisplayName("유효하지 않은 토큰이면 실패한다")
        fun rejectInvalidToken() {
            val tokenPort = FakeTokenPort(valid = false)
            val service = tokenService(tokenPort = tokenPort)

            assertFailsWith<InvalidTokenException> {
                service.refresh(RefreshTokenCommand(refreshToken = "invalid-token"))
            }

            assertEquals(null, tokenPort.parsedToken)
        }

        @Test
        @DisplayName("저장소에 없는 refresh token이면 실패한다")
        fun rejectMissingStoredRefreshToken() {
            val tokenPort = FakeTokenPort()
            val service = tokenService(
                tokenPort = tokenPort,
                refreshTokenStorePort = FakeRefreshTokenStorePort(exists = false)
            )

            assertFailsWith<InvalidTokenException> {
                service.refresh(RefreshTokenCommand(refreshToken = "missing-refresh-token"))
            }

            assertEquals(null, tokenPort.issuedAccessTokenUserId)
            assertEquals(null, tokenPort.issuedRefreshTokenUserId)
        }

        @Test
        @DisplayName("refresh token 회전에 실패하면 실패한다")
        fun rejectFailedRefreshTokenRotation() {
            val service = tokenService(
                tokenPort = FakeTokenPort(),
                refreshTokenStorePort = FakeRefreshTokenStorePort(rotate = false)
            )

            assertFailsWith<InvalidTokenException> {
                service.refresh(RefreshTokenCommand(refreshToken = "valid-refresh-token"))
            }
        }

        @Test
        @DisplayName("refresh token이 아니면 실패한다")
        fun rejectNonRefreshToken() {
            val tokenPort = FakeTokenPort(tokenType = TokenType.ACCESS)
            val service = tokenService(tokenPort = tokenPort)

            assertFailsWith<InvalidTokenException> {
                service.refresh(RefreshTokenCommand(refreshToken = "access-token"))
            }
        }

        @Test
        @DisplayName("활성 사용자가 아니면 실패한다")
        fun rejectInactiveUser() {
            val tokenPort = FakeTokenPort()
            val service = tokenService(
                tokenPort = tokenPort,
                users = mapOf(userId to user(status = UserStatus.DELETED))
            )

            assertFailsWith<InactiveUserException> {
                service.refresh(RefreshTokenCommand(refreshToken = "valid-refresh-token"))
            }
        }
    }

    private fun tokenService(
        tokenPort: TokenPort,
        refreshTokenStorePort: FakeRefreshTokenStorePort = FakeRefreshTokenStorePort(),
        users: Map<UserId, User> = mapOf(userId to user())
    ): TokenServiceFixture {
        val userPersistencePort = FakeUserPersistencePort(users)

        val tokenService = TokenService(
            tokenPort = tokenPort,
            refreshTokenStorePort = refreshTokenStorePort,
            userPersistencePort = userPersistencePort,
            clock = clock,
            activeUserValidator = ActiveUserValidator(userPersistencePort)
        )

        return TokenServiceFixture(
            tokenService = tokenService,
            refreshTokenStorePort = refreshTokenStorePort,
            userPersistencePort = userPersistencePort
        )
    }

    private data class TokenServiceFixture(
        val tokenService: TokenService,
        val refreshTokenStorePort: FakeRefreshTokenStorePort,
        val userPersistencePort: FakeUserPersistencePort
    ) {
        fun issue(command: IssueAuthTokensCommand): IssueAuthTokensResult {
            return tokenService.issue(command)
        }

        fun refresh(command: RefreshTokenCommand): RefreshTokenResult {
            return tokenService.refresh(command)
        }
    }

    private inner class FakeTokenPort(
        private val valid: Boolean = true,
        private val tokenType: TokenType = TokenType.REFRESH
    ) : TokenPort {
        var parsedToken: String? = null
        var issuedAccessTokenUserId: UserId? = null
        var issuedAccessTokenRole: UserRole? = null
        var issuedRefreshTokenUserId: UserId? = null

        override fun issueAccessToken(userId: UserId, role: UserRole): IssuedToken {
            issuedAccessTokenUserId = userId
            issuedAccessTokenRole = role

            return IssuedToken(
                id = "new-access-token-id",
                value = "new-access-token",
                expiresAt = Instant.parse("2026-05-20T00:15:00Z")
            )
        }

        override fun issueRefreshToken(userId: UserId): IssuedToken {
            issuedRefreshTokenUserId = userId

            return IssuedToken(
                id = "new-refresh-token-id",
                value = "new-refresh-token",
                expiresAt = Instant.parse("2026-06-03T00:00:00Z")
            )
        }

        override fun parseToken(token: String): ParsedToken {
            parsedToken = token

            return ParsedToken(
                id = "refresh-token-id",
                userId = userId,
                type = tokenType,
                role = null
            )
        }

        override fun isValid(token: String): Boolean {
            return valid
        }
    }

    private class FakeRefreshTokenStorePort(
        private val exists: Boolean = true,
        private val rotate: Boolean = true
    ) : RefreshTokenStorePort {
        var checkedToken: Pair<UserId, String>? = null
        var savedToken: StoredRefreshToken? = null
        var rotatedOldTokenId: String? = null
        var rotatedNewToken: StoredRefreshToken? = null

        override fun save(token: StoredRefreshToken) {
            savedToken = token
        }

        override fun exists(userId: UserId, tokenId: String): Boolean {
            checkedToken = userId to tokenId

            return exists
        }

        override fun rotate(userId: UserId, oldTokenId: String, newToken: StoredRefreshToken): Boolean {
            rotatedOldTokenId = oldTokenId
            rotatedNewToken = newToken

            return rotate
        }
    }

    private class FakeUserPersistencePort(
        private val users: Map<UserId, User>
    ) : UserPersistencePort {
        var savedUser: User? = null

        override fun findById(userId: UserId): User? {
            return users[userId]
        }

        override fun findByAuthProviderAndProviderUserId(
            authProvider: AuthProvider,
            providerUserId: ProviderUserId
        ): User? {
            return null
        }

        override fun existsByEmail(email: Email): Boolean {
            return false
        }

        override fun save(user: User): User {
            savedUser = user

            return user
        }
    }

    private fun user(status: UserStatus = UserStatus.ACTIVE): User {
        return User.restore(
            id = userId,
            email = Email.of("rgunny@kachi.com"),
            nickname = Nickname.of("rgunny"),
            status = status,
            role = UserRole.USER,
            authProvider = AuthProvider.GOOGLE,
            providerUserId = ProviderUserId.of("google-123"),
            registeredAt = registeredAt,
            lastLoginAt = null,
            deactivatedAt = null
        )
    }
}
