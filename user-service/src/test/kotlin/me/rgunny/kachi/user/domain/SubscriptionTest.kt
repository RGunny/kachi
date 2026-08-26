package me.rgunny.kachi.user.domain

import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("Subscription")
class SubscriptionTest {
    private val userId = UserId.newId()
    private val keywordId = KeywordId.newId()
    private val registeredAt = UserTestFixture.NOW

    @Nested
    @DisplayName("create()")
    inner class Create {
        @Test
        @DisplayName("구독을 생성하면 활성 상태이고 채널을 가진다")
        fun createSubscriptionAsEnabled() {
            val subscription = activeSubscription(setOf(SubscriptionChannel.SLACK, SubscriptionChannel.TELEGRAM))

            assertNotNull(subscription.id.value)
            assertEquals(userId, subscription.userId)
            assertEquals(keywordId, subscription.keywordId)
            assertEquals(setOf(SubscriptionChannel.SLACK, SubscriptionChannel.TELEGRAM), subscription.channels)
            assertEquals(true, subscription.enabled)
            assertEquals(registeredAt, subscription.registeredAt)
            assertNull(subscription.disabledAt)
        }

        @Test
        @DisplayName("채널이 없으면 생성할 수 없다")
        fun rejectEmptyChannels() {
            assertFailsWith<IllegalArgumentException> {
                activeSubscription(emptySet())
            }
        }
    }

    @Nested
    @DisplayName("changeChannels()")
    inner class ChangeChannels {
        @Test
        @DisplayName("채널을 바꾼 새 구독을 돌려주고 원본은 바뀌지 않는다")
        fun changeChannelsImmutably() {
            val subscription = activeSubscription(setOf(SubscriptionChannel.SLACK))

            val changed = subscription.changeChannels(setOf(SubscriptionChannel.DISCORD))

            assertEquals(setOf(SubscriptionChannel.DISCORD), changed.channels)
            assertEquals(setOf(SubscriptionChannel.SLACK), subscription.channels)
            assertEquals(subscription.id, changed.id)
        }

        @Test
        @DisplayName("채널을 비울 수 없다")
        fun rejectEmptyChannels() {
            assertFailsWith<IllegalArgumentException> {
                activeSubscription(setOf(SubscriptionChannel.SLACK)).changeChannels(emptySet())
            }
        }
    }

    @Nested
    @DisplayName("enable()")
    inner class Enable {
        @Test
        @DisplayName("비활성 구독을 활성화하고 비활성 시각을 지운다")
        fun enableSubscription() {
            val disabled = activeSubscription(setOf(SubscriptionChannel.SLACK)).disable(registeredAt.plus(Duration.ofHours(1)))

            val enabled = disabled.enable()

            assertEquals(true, enabled.enabled)
            assertNull(enabled.disabledAt)
        }
    }

    @Nested
    @DisplayName("disable()")
    inner class Disable {
        @Test
        @DisplayName("활성 구독을 비활성화한다")
        fun disableSubscription() {
            val disabledAt = registeredAt.plus(Duration.ofHours(1))

            val disabled = activeSubscription(setOf(SubscriptionChannel.SLACK)).disable(disabledAt)

            assertEquals(false, disabled.enabled)
            assertEquals(disabledAt, disabled.disabledAt)
        }

        @Test
        @DisplayName("이미 비활성화된 구독은 다시 비활성화할 수 없다")
        fun rejectDisablingTwice() {
            val disabled = activeSubscription(setOf(SubscriptionChannel.SLACK)).disable(registeredAt.plus(Duration.ofHours(1)))

            assertFailsWith<IllegalArgumentException> {
                disabled.disable(registeredAt.plus(Duration.ofHours(2)))
            }
        }
    }

    private fun activeSubscription(channels: Set<SubscriptionChannel>): Subscription {
        return Subscription.create(
            userId = userId,
            keywordId = keywordId,
            channels = channels,
            registeredAt = registeredAt
        )
    }
}
