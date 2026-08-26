package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.inbound.internal.model.FindSubscribersQuery
import me.rgunny.kachi.user.application.service.fake.FakeChannelBindingPersistencePort
import me.rgunny.kachi.user.application.service.fake.FakeKeywordPersistencePort
import me.rgunny.kachi.user.application.service.fake.FakeSubscriptionPersistencePort
import me.rgunny.kachi.user.domain.ChannelBinding
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.Subscription
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.fixture.UserTestFixture
import me.rgunny.kachi.user.fixture.UserTestFixture.activeBinding
import me.rgunny.kachi.user.fixture.UserTestFixture.keyword
import me.rgunny.kachi.user.fixture.UserTestFixture.subscription
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("SubscriberQueryService")
class SubscriberQueryServiceTest {
    private val tesla: Keyword = keyword("Tesla")

    @Test
    @DisplayName("enabled 구독의 채널 중 ACTIVE 바인딩이 있는 것만 사용자·채널 순으로 돌려준다")
    fun findActiveSubscribers() {
        val alice = UserId.newId()
        val bob = UserId.newId()
        val aliceSlack = activeBinding(alice, SubscriptionChannel.SLACK)
        val aliceTelegram = activeBinding(alice, SubscriptionChannel.TELEGRAM)
        val bobSlack = activeBinding(bob, SubscriptionChannel.SLACK)
        val service = service(
            subscriptions = listOf(
                subscription(alice, tesla.id, setOf(SubscriptionChannel.SLACK, SubscriptionChannel.TELEGRAM)),
                subscription(bob, tesla.id, setOf(SubscriptionChannel.SLACK))
            ),
            bindings = listOf(bobSlack, aliceTelegram, aliceSlack)
        )

        val results = service.findSubscribers(FindSubscribersQuery("tesla"))

        val expected = listOf(aliceSlack, aliceTelegram, bobSlack)
            .sortedWith(compareBy({ it.userId.value }, { it.channel }))
        assertEquals(expected.map { it.id }, results.map { it.recipientRef })
        assertEquals(expected.map { it.userId to it.channel }, results.map { it.userId to it.channel })
    }

    @Test
    @DisplayName("해지된 바인딩의 채널은 구독에 남아 있어도 제외한다")
    fun excludeRevokedBindingChannel() {
        val userId = UserId.newId()
        val revoked = activeBinding(userId, SubscriptionChannel.TELEGRAM).revoke(UserTestFixture.NOW)
        val slack = activeBinding(userId, SubscriptionChannel.SLACK)
        val service = service(
            subscriptions = listOf(subscription(userId, tesla.id, setOf(SubscriptionChannel.SLACK, SubscriptionChannel.TELEGRAM))),
            bindings = listOf(revoked, slack)
        )

        val results = service.findSubscribers(FindSubscribersQuery("tesla"))

        assertEquals(listOf(SubscriptionChannel.SLACK), results.map { it.channel })
        assertEquals(slack.id, results.single().recipientRef)
    }

    @Test
    @DisplayName("바인딩이 없는 채널은 제외한다")
    fun excludeChannelWithoutBinding() {
        val userId = UserId.newId()
        val service = service(
            subscriptions = listOf(subscription(userId, tesla.id, setOf(SubscriptionChannel.DISCORD))),
            bindings = listOf(activeBinding(userId, SubscriptionChannel.SLACK))
        )

        assertTrue(service.findSubscribers(FindSubscribersQuery("tesla")).isEmpty())
    }

    @Test
    @DisplayName("disabled 구독은 제외한다")
    fun excludeDisabledSubscription() {
        val userId = UserId.newId()
        val service = service(
            subscriptions = listOf(subscription(userId, tesla.id).disable(UserTestFixture.NOW)),
            bindings = listOf(activeBinding(userId, SubscriptionChannel.SLACK))
        )

        assertTrue(service.findSubscribers(FindSubscribersQuery("tesla")).isEmpty())
    }

    @Test
    @DisplayName("없는 키워드는 빈 목록이다")
    fun emptyForUnknownKeyword() {
        val userId = UserId.newId()
        val service = service(
            subscriptions = listOf(subscription(userId, tesla.id)),
            bindings = listOf(activeBinding(userId, SubscriptionChannel.SLACK))
        )

        assertTrue(service.findSubscribers(FindSubscribersQuery("nvidia")).isEmpty())
    }

    @Test
    @DisplayName("원문 표기로 물어도 정규화해 같은 키워드를 찾는다")
    fun normalizeQueryKeyword() {
        val userId = UserId.newId()
        val service = service(
            subscriptions = listOf(subscription(userId, tesla.id)),
            bindings = listOf(activeBinding(userId, SubscriptionChannel.SLACK))
        )

        assertEquals(1, service.findSubscribers(FindSubscribersQuery(" TESLA ")).size)
    }

    private fun service(subscriptions: List<Subscription>, bindings: List<ChannelBinding>): SubscriberQueryService {
        return SubscriberQueryService(
            keywordPersistencePort = FakeKeywordPersistencePort(listOf(tesla)),
            subscriptionPersistencePort = FakeSubscriptionPersistencePort(subscriptions),
            channelBindingPersistencePort = FakeChannelBindingPersistencePort(bindings)
        )
    }
}
