package me.rgunny.kachi.user.application.service.fake

import me.rgunny.kachi.user.application.port.outbound.user.UserPersistencePort
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.ProviderUserId
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId

/**
 * id 조회만 지원하는 사용자 저장소.
 *
 * `ActiveUserValidator`가 활성 사용자를 판정하는 데 필요한 `findById`만 채운다. 나머지는 호출되지 않으므로 빈 응답이다.
 */
class FakeUserPersistencePort(
    private val users: Map<UserId, User>
) : UserPersistencePort {

    override fun findById(userId: UserId): User? = users[userId]

    override fun findByAuthProviderAndProviderUserId(authProvider: AuthProvider, providerUserId: ProviderUserId): User? = null

    override fun existsByEmail(email: Email): Boolean = false

    override fun save(user: User): User = user
}
