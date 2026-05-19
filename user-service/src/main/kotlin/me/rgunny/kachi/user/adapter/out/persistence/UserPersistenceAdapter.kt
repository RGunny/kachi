package me.rgunny.kachi.user.adapter.out.persistence

import me.rgunny.kachi.user.application.port.out.UserPersistencePort
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.User
import org.springframework.stereotype.Repository

@Repository
class UserPersistenceAdapter(
    private val userJpaRepository: UserJpaRepository
) : UserPersistencePort {

    override fun existsByEmail(email: Email): Boolean {
        return userJpaRepository.existsByEmail(email.value)
    }

    override fun save(user: User): User {
        return userJpaRepository.save(UserJpaEntity.from(user)).toDomain()
    }
}
