package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.port.`in`.ResolveOAuthUserCommand
import me.rgunny.kachi.user.application.port.out.UserPersistencePort
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
import kotlin.test.assertFalse

@DisplayName("OAuthUserService")
class OAuthUserServiceTest {
    private val now = Instant.parse("2026-05-20T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val userId = UserId.newId()

    @Nested
    @DisplayName("resolve()")
    inner class Resolve {

        @Test
        @DisplayName("OAuth 사용자 식별자로 기존 사용자를 찾으면 해당 사용자 ID를 반환한다")
        fun resolveExistingOAuthUser() {
            val existingUser = user()
            val userPersistencePort = FakeUserPersistencePort(existingUsers = listOf(existingUser))
            val service = OAuthUserService(userPersistencePort, clock)

            val result = service.resolve(command())

            assertEquals(existingUser.id, result.userId)
            assertFalse(userPersistencePort.saveCalled)
        }

        @Test
        @DisplayName("기존 사용자가 없으면 OAuth 사용자 정보를 기반으로 신규 사용자를 등록한다")
        fun registerOAuthUser() {
            val userPersistencePort = FakeUserPersistencePort()
            val service = OAuthUserService(userPersistencePort, clock)

            val result = service.resolve(command())

            val savedUser = userPersistencePort.savedUsers.single()
            assertEquals(savedUser.id, result.userId)
            assertEquals(Email.of("rgunny@kachi.com"), savedUser.email)
            assertEquals(Nickname.of("rgunny"), savedUser.nickname)
            assertEquals(AuthProvider.GOOGLE, savedUser.authProvider)
            assertEquals(ProviderUserId.of("google-123"), savedUser.providerUserId)
            assertEquals(now, savedUser.registeredAt)
        }

        @Test
        @DisplayName("기존 OAuth 사용자가 없지만 email이 이미 등록되어 있으면 실패한다")
        fun rejectDuplicateEmail() {
            val userPersistencePort = FakeUserPersistencePort(
                existingEmails = setOf(Email.of("rgunny@kachi.com"))
            )
            val service = OAuthUserService(userPersistencePort, clock)

            assertFailsWith<DuplicateEmailException> {
                service.resolve(command())
            }

            assertFalse(userPersistencePort.saveCalled)
        }

        @Test
        @DisplayName("LOCAL provider는 OAuth 로그인에 사용할 수 없다")
        fun rejectLocalProvider() {
            val userPersistencePort = FakeUserPersistencePort()
            val service = OAuthUserService(userPersistencePort, clock)

            assertFailsWith<IllegalArgumentException> {
                service.resolve(command(authProvider = AuthProvider.LOCAL))
            }

            assertFalse(userPersistencePort.saveCalled)
        }
    }

    private fun command(authProvider: AuthProvider = AuthProvider.GOOGLE): ResolveOAuthUserCommand {
        return ResolveOAuthUserCommand(
            authProvider = authProvider,
            providerUserId = "google-123",
            email = "rgunny@kachi.com",
            nickname = "rgunny"
        )
    }

    private fun user(): User {
        return User.restore(
            id = userId,
            email = Email.of("rgunny@kachi.com"),
            nickname = Nickname.of("rgunny"),
            status = UserStatus.ACTIVE,
            role = UserRole.USER,
            authProvider = AuthProvider.GOOGLE,
            providerUserId = ProviderUserId.of("google-123"),
            registeredAt = now,
            lastLoginAt = null,
            deactivatedAt = null
        )
    }

    private class FakeUserPersistencePort(
        existingUsers: List<User> = emptyList(),
        private val existingEmails: Set<Email> = emptySet()
    ) : UserPersistencePort {
        private val usersByOAuth = existingUsers.associateBy { it.authProvider to it.providerUserId }
        val savedUsers = mutableListOf<User>()
        var saveCalled = false

        override fun findById(userId: UserId): User? {
            return null
        }

        override fun findByAuthProviderAndProviderUserId(
            authProvider: AuthProvider,
            providerUserId: ProviderUserId
        ): User? {
            return usersByOAuth[authProvider to providerUserId]
        }

        override fun existsByEmail(email: Email): Boolean {
            return email in existingEmails
        }

        override fun save(user: User): User {
            saveCalled = true
            savedUsers += user

            return user
        }
    }
}
