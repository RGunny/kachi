package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.port.`in`.RegisterUserCommand
import me.rgunny.kachi.user.application.port.out.UserPersistencePort
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.User
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
            val service = UserCommandService(userPersistencePort, clock)

            val result = service.register(
                RegisterUserCommand(
                    email = "  rgunny@kachi.COM  ",
                    nickname = "  rgunny  ",
                    authProvider = AuthProvider.GOOGLE
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
        }

        @Test
        @DisplayName("이미 등록된 이메일이면 사용자를 저장하지 않는다")
        fun rejectDuplicateEmail() {
            val userPersistencePort = FakeUserPersistencePort(existingEmails = setOf(Email.of("rgunny@kachi.com")))
            val service = UserCommandService(userPersistencePort, clock)

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

    private class FakeUserPersistencePort(
        private val existingEmails: Set<Email> = emptySet()
    ) : UserPersistencePort {
        val savedUsers = mutableListOf<User>()
        var existsByEmailCalled = false
        var saveCalled = false

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
}
