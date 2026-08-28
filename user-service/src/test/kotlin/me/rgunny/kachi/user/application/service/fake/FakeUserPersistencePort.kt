package me.rgunny.kachi.user.application.service.fake

import me.rgunny.kachi.user.application.port.outbound.user.UserPersistencePort
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.ProviderUserId
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole

/**
 * 메모리 사용자 저장소.
 *
 * `findById`와 `findAllByRole`만 채운다. 나머지는 호출되지 않으므로 빈 응답이다.
 */
class FakeUserPersistencePort(
    private val users: Map<UserId, User>
) : UserPersistencePort {

    override fun findById(userId: UserId): User? = users[userId]

    override fun findByAuthProviderAndProviderUserId(authProvider: AuthProvider, providerUserId: ProviderUserId): User? = null

    override fun existsByEmail(email: Email): Boolean = false

    override fun findAllByRole(role: UserRole): List<User> = users.values.filter { it.role == role }

    override fun save(user: User): User = user
}
