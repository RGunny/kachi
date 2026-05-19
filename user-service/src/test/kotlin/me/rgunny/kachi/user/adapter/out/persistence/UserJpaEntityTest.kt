package me.rgunny.kachi.user.adapter.out.persistence

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
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("UserJpaEntity")
class UserJpaEntityTest {
    private val registeredAt = Instant.parse("2026-05-20T00:00:00Z")
    private val lastLoginAt = Instant.parse("2026-05-20T01:00:00Z")
    private val deactivatedAt = Instant.parse("2026-05-20T02:00:00Z")

    @Nested
    @DisplayName("from()")
    inner class From {

        @Test
        @DisplayName("User 도메인을 JPA 엔티티로 변환한다")
        fun convertUserToJpaEntity() {
            val user = restoredUser()

            val entity = UserJpaEntity.from(user)

            assertEquals(user.id.value, entity.id)
            assertEquals(user.email.value, entity.email)
            assertEquals(user.nickname.value, entity.nickname)
            assertEquals(user.status, entity.status)
            assertEquals(user.role, entity.role)
            assertEquals(user.authProvider, entity.authProvider)
            assertEquals(user.registeredAt, entity.registeredAt)
            assertEquals(user.lastLoginAt, entity.lastLoginAt)
            assertEquals(user.deactivatedAt, entity.deactivatedAt)
        }
    }

    @Nested
    @DisplayName("toDomain()")
    inner class ToDomain {

        @Test
        @DisplayName("JPA 엔티티를 User 도메인으로 복원한다")
        fun convertJpaEntityToUser() {
            val entity = UserJpaEntity.from(restoredUser())

            val user = entity.toDomain()

            assertEquals(UserId.of(entity.id), user.id)
            assertEquals(Email.of(entity.email), user.email)
            assertEquals(Nickname.of(entity.nickname), user.nickname)
            assertEquals(entity.status, user.status)
            assertEquals(entity.role, user.role)
            assertEquals(entity.authProvider, user.authProvider)
            assertEquals(entity.registeredAt, user.registeredAt)
            assertEquals(entity.lastLoginAt, user.lastLoginAt)
            assertEquals(entity.deactivatedAt, user.deactivatedAt)
        }
    }

    private fun restoredUser(): User {
        return User.restore(
            id = UserId.of(UUID.randomUUID()),
            email = Email.of("rgunny@kachi.com"),
            nickname = Nickname.of("rgunny"),
            status = UserStatus.DELETED,
            role = UserRole.ADMIN,
            authProvider = AuthProvider.LOCAL,
            registeredAt = registeredAt,
            lastLoginAt = lastLoginAt,
            deactivatedAt = deactivatedAt
        )
    }
}
