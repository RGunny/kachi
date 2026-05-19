package me.rgunny.kachi.user.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_users_email", columnNames = ["email"])
    ],
    indexes = [
        Index(name = "idx_users_status", columnList = "status")
    ]
)
class UserJpaEntity(

    @Id
    @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
    val id: UUID,

    @Column(name = "email", nullable = false, length = 255)
    val email: String,

    @Column(name = "nickname", nullable = false, length = 100)
    val nickname: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: UserStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    val role: UserRole,

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    val authProvider: AuthProvider,

    @Column(name = "registered_at", nullable = false)
    val registeredAt: Instant,

    @Column(name = "last_login_at")
    val lastLoginAt: Instant?,

    @Column(name = "deactivated_at")
    val deactivatedAt: Instant?

) {

    companion object {
        fun from(user: User): UserJpaEntity {
            return UserJpaEntity(
                id = user.id.value,
                email = user.email.value,
                nickname = user.nickname.value,
                status = user.status,
                role = user.role,
                authProvider = user.authProvider,
                registeredAt = user.registeredAt,
                lastLoginAt = user.lastLoginAt,
                deactivatedAt = user.deactivatedAt
            )
        }
    }

    fun toDomain(): User {
        return User.restore(
            id = UserId.of(id),
            email = Email.of(email),
            nickname = Nickname.of(nickname),
            status = status,
            role = role,
            authProvider = authProvider,
            registeredAt = registeredAt,
            lastLoginAt = lastLoginAt,
            deactivatedAt = deactivatedAt
        )
    }

}
