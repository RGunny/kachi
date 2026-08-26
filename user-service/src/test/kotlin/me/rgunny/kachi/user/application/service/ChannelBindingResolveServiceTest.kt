package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.ChannelBindingRefNotFoundException
import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolveChannelBindingQuery
import me.rgunny.kachi.user.application.service.fake.FakeChannelBindingPersistencePort
import me.rgunny.kachi.user.domain.ChannelBinding
import me.rgunny.kachi.user.domain.ChannelBindingId
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.LinkToken
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.fixture.UserTestFixture
import me.rgunny.kachi.user.fixture.UserTestFixture.activeBinding
import me.rgunny.kachi.user.fixture.UserTestFixture.address
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("ChannelBindingResolveService")
class ChannelBindingResolveServiceTest {
    private val userId = UserId.newId()

    @Test
    @DisplayName("ACTIVE 바인딩은 평문 주소를 돌려준다")
    fun resolveActiveAddress() {
        val binding = activeBinding(userId, SubscriptionChannel.SLACK)
        val service = ChannelBindingResolveService(FakeChannelBindingPersistencePort(listOf(binding)))

        val result = service.resolve(ResolveChannelBindingQuery(binding.id))

        assertEquals(SubscriptionChannel.SLACK, result.channel)
        assertEquals(ChannelBindingStatus.ACTIVE, result.status)
        assertEquals(address(SubscriptionChannel.SLACK).value, result.address)
    }

    @Test
    @DisplayName("PENDING·REVOKED 바인딩은 상태만 있고 주소는 없다")
    fun resolveInactiveWithoutAddress() {
        val pending = ChannelBinding.createPending(
            userId, SubscriptionChannel.TELEGRAM, LinkToken.issue(UserTestFixture.NOW, Duration.ofMinutes(10)), UserTestFixture.NOW
        )
        val revoked = activeBinding(userId, SubscriptionChannel.SLACK).revoke(UserTestFixture.NOW)
        val service = ChannelBindingResolveService(FakeChannelBindingPersistencePort(listOf(pending, revoked)))

        val pendingResult = service.resolve(ResolveChannelBindingQuery(pending.id))
        val revokedResult = service.resolve(ResolveChannelBindingQuery(revoked.id))

        assertEquals(ChannelBindingStatus.PENDING, pendingResult.status)
        assertNull(pendingResult.address)
        assertEquals(ChannelBindingStatus.REVOKED, revokedResult.status)
        assertNull(revokedResult.address)
    }

    @Test
    @DisplayName("없는 참조는 예외다")
    fun rejectUnknownRef() {
        val service = ChannelBindingResolveService(FakeChannelBindingPersistencePort())
        val ref = ChannelBindingId.newId()

        val exception = assertFailsWith<ChannelBindingRefNotFoundException> {
            service.resolve(ResolveChannelBindingQuery(ref))
        }

        assertEquals(ref, exception.ref)
    }
}
