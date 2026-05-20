package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.exception.UserNotFoundException
import me.rgunny.kachi.user.application.port.`in`.GetUserQuery
import me.rgunny.kachi.user.application.port.out.UserPersistencePort
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("UserQueryService")
class UserQueryServiceTest {
    private val userId = UserId.newId()
    private val registeredAt = Instant.parse("2026-05-20T00:00:00Z")

    @Nested
    @DisplayName("get()")
    inner class Get {

        @Test
        @DisplayName("사용자 ID로 사용자를 조회한다")
        fun getUser() {
            val user = user()
            val userPersistencePort = FakeUserPersistencePort(users = mapOf(user.id to user))
            val service = UserQueryService(ActiveUserValidator(userPersistencePort))

            val result = service.get(GetUserQuery(userId))

            assertEquals(userId, userPersistencePort.userId)
            assertEquals(userId, result.id)
            assertEquals("rgunny@kachi.com", result.email)
            assertEquals("rgunny", result.nickname)
            assertEquals(registeredAt, result.registeredAt)
        }

        @Test
        @DisplayName("사용자가 없으면 실패한다")
        fun rejectMissingUser() {
            val userPersistencePort = FakeUserPersistencePort()
            val service = UserQueryService(ActiveUserValidator(userPersistencePort))

            assertFailsWith<UserNotFoundException> {
                service.get(GetUserQuery(userId))
            }

            assertEquals(userId, userPersistencePort.userId)
        }

        @Test
        @DisplayName("활성 사용자가 아니면 실패한다")
        fun rejectInactiveUser() {
            val user = user(status = UserStatus.DELETED)
            val userPersistencePort = FakeUserPersistencePort(users = mapOf(user.id to user))
            val service = UserQueryService(ActiveUserValidator(userPersistencePort))

            assertFailsWith<InactiveUserException> {
                service.get(GetUserQuery(userId))
            }

            assertEquals(userId, userPersistencePort.userId)
        }
    }

    private class FakeUserPersistencePort(
        private val users: Map<UserId, User> = emptyMap()
    ) : UserPersistencePort {
        var userId: UserId? = null

        override fun findById(userId: UserId): User? {
            this.userId = userId
            return users[userId]
        }

        override fun existsByEmail(email: Email): Boolean {
            return false
        }

        override fun save(user: User): User {
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
            registeredAt = registeredAt,
            lastLoginAt = null,
            deactivatedAt = null
        )
    }
}
