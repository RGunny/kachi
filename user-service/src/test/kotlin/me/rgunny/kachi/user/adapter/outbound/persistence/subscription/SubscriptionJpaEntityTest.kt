package me.rgunny.kachi.user.adapter.outbound.persistence.subscription

import java.time.Duration
import kotlin.test.assertEquals
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.Subscription
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("SubscriptionJpaEntity")
class SubscriptionJpaEntityTest {

    @Test
    @DisplayName("도메인과 엔티티를 왕복 변환한다")
    fun roundTrip() {
        val subscription = Subscription.restore(
            id = SubscriptionId.newId(),
            userId = UserId.newId(),
            keywordId = KeywordId.newId(),
            channels = setOf(SubscriptionChannel.SLACK, SubscriptionChannel.DISCORD),
            enabled = false,
            registeredAt = UserTestFixture.NOW,
            disabledAt = UserTestFixture.NOW.plus(Duration.ofHours(1))
        )

        val restored = SubscriptionJpaEntity.from(subscription).toDomain()

        assertEquals(subscription.id, restored.id)
        assertEquals(subscription.userId, restored.userId)
        assertEquals(subscription.keywordId, restored.keywordId)
        assertEquals(subscription.channels, restored.channels)
        assertEquals(subscription.enabled, restored.enabled)
        assertEquals(subscription.registeredAt, restored.registeredAt)
        assertEquals(subscription.disabledAt, restored.disabledAt)
    }
}
