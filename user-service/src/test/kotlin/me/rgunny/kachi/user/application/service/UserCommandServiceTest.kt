package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.port.`in`.DeactivateUserCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterUserCommand
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("UserCommandService")
class UserCommandServiceTest {
    private val now = Instant.parse("2026-05-20T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Nested
    @DisplayName("register()")
    inner class Register {

        @Test
        @DisplayName("사용자를 등록하고 저장한다")
        fun registerUser() {
            val userPersistencePort = FakeUserPersistencePort()
            val service = userCommandService(userPersistencePort)

            val result = service.register(
                RegisterUserCommand(
                    email = "  rgunny@kachi.COM  ",
                    nickname = "  rgunny  ",
                    authProvider = AuthProvider.GOOGLE,
                    providerUserId = "google-123"
                )
            )

            assertNotNull(result.id.value)
            assertEquals("rgunny@kachi.com", result.email)
            assertEquals("rgunny", result.nickname)
            assertEquals(UserStatus.ACTIVE, result.status)
            assertEquals(UserRole.USER, result.role)
            assertEquals(AuthProvider.GOOGLE, result.authProvider)
            assertEquals(now, result.registeredAt)
            assertEquals(1, userPersistencePort.savedUsers.size)
            assertEquals(result.id, userPersistencePort.savedUsers.single().id)
            assertEquals(ProviderUserId.of("google-123"), userPersistencePort.savedUsers.single().providerUserId)
        }

        @Test
        @DisplayName("이미 등록된 이메일이면 사용자를 저장하지 않는다")
        fun rejectDuplicateEmail() {
            val userPersistencePort = FakeUserPersistencePort(existingEmails = setOf(Email.of("rgunny@kachi.com")))
            val service = userCommandService(userPersistencePort)

            assertFailsWith<DuplicateEmailException> {
                service.register(
                    RegisterUserCommand(
                        email = "rgunny@kachi.com",
                        nickname = "rgunny",
                        authProvider = AuthProvider.LOCAL
                    )
                )
            }

            assertTrue(userPersistencePort.existsByEmailCalled)
            assertFalse(userPersistencePort.saveCalled)
        }
    }

    @Nested
    @DisplayName("deactivate()")
    inner class Deactivate {

        @Test
        @DisplayName("활성 사용자를 탈퇴 상태로 변경한다")
        fun deactivateUser() {
            val userId = UserId.newId()
            val userPersistencePort = FakeUserPersistencePort(users = mapOf(userId to user(userId)))
            val service = userCommandService(userPersistencePort)

            service.deactivate(DeactivateUserCommand(userId))

            val savedUser = userPersistencePort.savedUsers.single()
            assertEquals(userId, savedUser.id)
            assertEquals(UserStatus.DELETED, savedUser.status)
            assertEquals(now, savedUser.deactivatedAt)
        }

        @Test
        @DisplayName("활성 사용자가 아니면 탈퇴 처리하지 않는다")
        fun rejectInactiveUser() {
            val userId = UserId.newId()
            val userPersistencePort = FakeUserPersistencePort(
                users = mapOf(userId to user(userId, status = UserStatus.DELETED))
            )
            val service = userCommandService(userPersistencePort)

            assertFailsWith<InactiveUserException> {
                service.deactivate(DeactivateUserCommand(userId))
            }

            assertFalse(userPersistencePort.saveCalled)
        }
    }

    private fun userCommandService(userPersistencePort: FakeUserPersistencePort): UserCommandService {
        return UserCommandService(
            userPersistencePort = userPersistencePort,
            clock = clock,
            activeUserValidator = ActiveUserValidator(userPersistencePort)
        )
    }

    private class FakeUserPersistencePort(
        private val existingEmails: Set<Email> = emptySet(),
        private val users: Map<UserId, User> = emptyMap()
    ) : UserPersistencePort {
        val savedUsers = mutableListOf<User>()
        var existsByEmailCalled = false
        var saveCalled = false

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
            existsByEmailCalled = true
            return email in existingEmails
        }

        override fun save(user: User): User {
            saveCalled = true
            savedUsers += user
            return user
        }
    }

    private fun user(userId: UserId, status: UserStatus = UserStatus.ACTIVE): User {
        return User.restore(
            id = userId,
            email = Email.of("rgunny@kachi.com"),
            nickname = Nickname.of("rgunny"),
            status = status,
            role = UserRole.USER,
            authProvider = AuthProvider.GOOGLE,
            providerUserId = ProviderUserId.of("google-123"),
            registeredAt = Instant.parse("2026-05-19T00:00:00Z"),
            lastLoginAt = null,
            deactivatedAt = if (status == UserStatus.DELETED) now else null
        )
    }
}
