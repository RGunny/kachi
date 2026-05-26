package me.rgunny.kachi.user.adapter.out.persistence

import me.rgunny.kachi.user.domain.AuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {
    fun findByAuthProviderAndProviderUserId(authProvider: AuthProvider, providerUserId: String): UserJpaEntity?

    fun existsByEmail(email: String): Boolean
}
