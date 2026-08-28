package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.inbound.internal.model.FindUsersByRoleQuery
import me.rgunny.kachi.user.application.service.fake.FakeChannelBindingPersistencePort
import me.rgunny.kachi.user.application.service.fake.FakeUserPersistencePort
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.ChannelBinding
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import me.rgunny.kachi.user.fixture.UserTestFixture
import me.rgunny.kachi.user.fixture.UserTestFixture.activeBinding
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("UserChannelsQueryService")
class UserChannelsQueryServiceTest {

    @Test
    @DisplayName("역할의 ACTIVE 사용자마다 ACTIVE 바인딩 채널을 모아 사용자 순으로 돌려준다")
    fun findUsersWithActiveChannels() {
        val alice = admin()
        val bob = admin()
        val service = service(
            users = listOf(alice, bob, user(UserRole.USER)),
            bindings = listOf(
                activeBinding(bob.id, SubscriptionChannel.SLACK),
                activeBinding(alice.id, SubscriptionChannel.TELEGRAM),
                activeBinding(alice.id, SubscriptionChannel.SLACK)
            )
        )

        val results = service.findUsersByRole(FindUsersByRoleQuery(UserRole.ADMIN))

        val expected = listOf(alice, bob).sortedBy { it.id.value }
        assertEquals(expected.map { it.id }, results.map { it.userId })
        assertEquals(
            setOf(SubscriptionChannel.SLACK, SubscriptionChannel.TELEGRAM),
            results.single { it.userId == alice.id }.channels
        )
        assertEquals(setOf(SubscriptionChannel.SLACK), results.single { it.userId == bob.id }.channels)
    }

    @Test
    @DisplayName("ACTIVE 바인딩이 하나도 없는 사용자는 제외한다")
    fun excludeUserWithoutActiveBinding() {
        val withBinding = admin()
        val revokedOnly = admin()
        val noBinding = admin()
        val service = service(
            users = listOf(withBinding, revokedOnly, noBinding),
            bindings = listOf(
                activeBinding(withBinding.id, SubscriptionChannel.DISCORD),
                activeBinding(revokedOnly.id, SubscriptionChannel.SLACK).revoke(UserTestFixture.NOW)
            )
        )

        val results = service.findUsersByRole(FindUsersByRoleQuery(UserRole.ADMIN))

        assertEquals(listOf(withBinding.id), results.map { it.userId })
    }

    @Test
    @DisplayName("ACTIVE가 아닌 사용자는 바인딩이 있어도 제외한다")
    fun excludeInactiveUser() {
        val inactive = admin(status = UserStatus.INACTIVE)
        val service = service(
            users = listOf(inactive),
            bindings = listOf(activeBinding(inactive.id, SubscriptionChannel.SLACK))
        )

        assertTrue(service.findUsersByRole(FindUsersByRoleQuery(UserRole.ADMIN)).isEmpty())
    }

    @Test
    @DisplayName("역할에 해당하는 사용자가 없으면 빈 목록이다")
    fun emptyForRoleWithoutUsers() {
        val member = user(UserRole.USER)
        val service = service(
            users = listOf(member),
            bindings = listOf(activeBinding(member.id, SubscriptionChannel.SLACK))
        )

        assertTrue(service.findUsersByRole(FindUsersByRoleQuery(UserRole.ADMIN)).isEmpty())
    }

    private fun admin(status: UserStatus = UserStatus.ACTIVE): User = user(UserRole.ADMIN, status)

    private fun user(role: UserRole, status: UserStatus = UserStatus.ACTIVE): User {
        val id = UserId.newId()

        return User.restore(
            id = id,
            email = Email.of("$id@kachi.com"),
            nickname = Nickname.of("user"),
            status = status,
            role = role,
            authProvider = AuthProvider.LOCAL,
            providerUserId = null,
            registeredAt = UserTestFixture.NOW,
            lastLoginAt = null,
            deactivatedAt = null
        )
    }

    private fun service(users: List<User>, bindings: List<ChannelBinding>): UserChannelsQueryService {
        return UserChannelsQueryService(
            userPersistencePort = FakeUserPersistencePort(users.associateBy { it.id }),
            channelBindingPersistencePort = FakeChannelBindingPersistencePort(bindings)
        )
    }
}
