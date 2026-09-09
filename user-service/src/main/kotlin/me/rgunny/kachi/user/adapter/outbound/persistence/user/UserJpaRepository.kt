package me.rgunny.kachi.user.adapter.outbound.persistence.user

import java.util.UUID
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.UserRole
import org.springframework.data.jpa.repository.JpaRepository

interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {
    fun findByAuthProviderAndProviderUserId(authProvider: AuthProvider, providerUserId: String): UserJpaEntity?

    fun findByEmail(email: String): UserJpaEntity?

    fun existsByEmail(email: String): Boolean

    fun findAllByRole(role: UserRole): List<UserJpaEntity>
}
