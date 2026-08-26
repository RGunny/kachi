package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.inbound.binding.model.ListChannelBindingsQuery
import me.rgunny.kachi.user.application.service.fake.FakeChannelBindingPersistencePort
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.fixture.UserTestFixture
import me.rgunny.kachi.user.fixture.UserTestFixture.activeBinding
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("ChannelBindingQueryService")
class ChannelBindingQueryServiceTest {
    private val userId = UserId.newId()

    @Test
    @DisplayName("사용자의 바인딩만 채널 순으로 돌려주고 주소는 마스킹한다")
    fun listMaskedBindingsOfUser() {
        val revoked = activeBinding(userId, SubscriptionChannel.TELEGRAM).revoke(UserTestFixture.NOW)
        val port = FakeChannelBindingPersistencePort(
            listOf(
                revoked,
                activeBinding(userId, SubscriptionChannel.SLACK),
                activeBinding(UserId.newId(), SubscriptionChannel.DISCORD)
            )
        )

        val results = ChannelBindingQueryService(port).list(ListChannelBindingsQuery(userId))

        assertEquals(listOf(SubscriptionChannel.SLACK, SubscriptionChannel.TELEGRAM), results.map { it.channel })
        assertEquals(ChannelBindingStatus.ACTIVE, results[0].status)
        assertEquals("https://hooks.slack.com/****", results[0].addressMasked)
        assertEquals(ChannelBindingStatus.REVOKED, results[1].status)
        assertNull(results[1].addressMasked)
    }
}
