package me.rgunny.kachi.user.adapter.outbound.persistence

import me.rgunny.kachi.user.application.port.outbound.user.UserPersistencePort
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.ProviderUserId
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import org.springframework.stereotype.Repository

@Repository
class UserPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository
) : UserPersistencePort {

    override fun findById(userId: UserId): User? {
        return userJpaRepository.findById(userId.value)
            .map { it.toDomain() }
            .orElse(null)
    }

    override fun findByAuthProviderAndProviderUserId(
        authProvider: AuthProvider,
        providerUserId: ProviderUserId
    ): User? {
        return userJpaRepository.findByAuthProviderAndProviderUserId(authProvider, providerUserId.value)
            ?.toDomain()
    }

    override fun existsByEmail(email: Email): Boolean {
        return userJpaRepository.existsByEmail(email.value)
    }

    override fun save(user: User): User {
        return userJpaRepository.save(UserJpaEntity.from(user)).toDomain()
    }
}
