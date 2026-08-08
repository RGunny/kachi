package me.rgunny.kachi.user.adapter.outbound.persistence

import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.ProviderUserId
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("UserJpaEntity")
class UserJpaEntityTest {
    private val registeredAt = UserTestFixture.NOW
    private val lastLoginAt = UserTestFixture.NOW.plus(Duration.ofHours(1))
    private val deactivatedAt = UserTestFixture.NOW.plus(Duration.ofHours(2))

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
            assertEquals(user.providerUserId?.value, entity.providerUserId)
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
            assertEquals(entity.providerUserId?.let(ProviderUserId::of), user.providerUserId)
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
            providerUserId = null,
            registeredAt = registeredAt,
            lastLoginAt = lastLoginAt,
            deactivatedAt = deactivatedAt
        )
    }
}
