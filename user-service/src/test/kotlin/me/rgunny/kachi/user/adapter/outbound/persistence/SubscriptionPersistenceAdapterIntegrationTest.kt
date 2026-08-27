package me.rgunny.kachi.user.adapter.outbound.persistence

import jakarta.persistence.PersistenceException
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.Subscription
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("SubscriptionPersistenceAdapter 통합 테스트")
class SubscriptionPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var keywordPersistenceAdapter: KeywordPersistenceAdapter

    @Autowired
    private lateinit var subscriptionPersistenceAdapter: SubscriptionPersistenceAdapter

    private val now = UserTestFixture.NOW
    private lateinit var keyword: Keyword

    @BeforeEach
    fun setUp() {
        keyword = keywordPersistenceAdapter.save(Keyword.create(KeywordName.of("Tesla"), now))
    }

    @Nested
    @DisplayName("save()")
    inner class Save {

        @Test
        @DisplayName("채널 집합까지 저장하고 복원한다")
        fun saveWithChannels() {
            val channels = setOf(SubscriptionChannel.SLACK, SubscriptionChannel.TELEGRAM)
            val saved = subscriptionPersistenceAdapter.save(subscription(UserId.newId(), channels))
            flushAndClear()

            val found = subscriptionPersistenceAdapter.findById(saved.id)

            assertNotNull(found)
            assertEquals(channels, found.channels)
            assertEquals(keyword.id, found.keywordId)
            assertTrue(found.enabled)
        }

        @Test
        @DisplayName("채널을 바꿔 다시 저장하면 컬렉션이 교체된다")
        fun replaceChannels() {
            val saved = subscriptionPersistenceAdapter.save(subscription(UserId.newId(), setOf(SubscriptionChannel.SLACK)))
            flushAndClear()

            subscriptionPersistenceAdapter.save(saved.changeChannels(setOf(SubscriptionChannel.DISCORD)))
            flushAndClear()

            assertEquals(setOf(SubscriptionChannel.DISCORD), subscriptionPersistenceAdapter.findById(saved.id)?.channels)
        }

        @Test
        @DisplayName("같은 사용자는 같은 키워드를 두 번 구독할 수 없다")
        fun rejectDuplicateUserKeyword() {
            val userId = UserId.newId()
            subscriptionPersistenceAdapter.save(subscription(userId, setOf(SubscriptionChannel.SLACK)))
            subscriptionPersistenceAdapter.save(subscription(userId, setOf(SubscriptionChannel.SLACK)))

            assertFailsWith<PersistenceException> {
                flushAndClear()
            }
        }
    }

    @Nested
    @DisplayName("조회")
    inner class Find {

        @Test
        @DisplayName("사용자 ID로 구독 목록을 조회하고 존재 여부를 확인한다")
        fun findAllByUserIdAndExists() {
            val userId = UserId.newId()
            val other = keywordPersistenceAdapter.save(Keyword.create(KeywordName.of("NVIDIA"), now))
            subscriptionPersistenceAdapter.save(subscription(userId, setOf(SubscriptionChannel.SLACK)))
            subscriptionPersistenceAdapter.save(
                Subscription.create(userId, other.id, setOf(SubscriptionChannel.SLACK), now)
            )
            subscriptionPersistenceAdapter.save(subscription(UserId.newId(), setOf(SubscriptionChannel.SLACK)))
            flushAndClear()

            val subscriptions = subscriptionPersistenceAdapter.findAllByUserId(userId)

            assertEquals(setOf(keyword.id, other.id), subscriptions.map { it.keywordId }.toSet())
            assertTrue(subscriptionPersistenceAdapter.existsByUserIdAndKeywordId(userId, keyword.id))
            assertEquals(false, subscriptionPersistenceAdapter.existsByUserIdAndKeywordId(UserId.newId(), keyword.id))
        }

        @Test
        @DisplayName("키워드의 enabled 구독만 조회한다")
        fun findAllEnabledByKeywordId() {
            val enabledUser = UserId.newId()
            val other = keywordPersistenceAdapter.save(Keyword.create(KeywordName.of("NVIDIA"), now))
            subscriptionPersistenceAdapter.save(subscription(enabledUser, setOf(SubscriptionChannel.SLACK)))
            subscriptionPersistenceAdapter.save(subscription(UserId.newId(), setOf(SubscriptionChannel.SLACK)).disable(now))
            subscriptionPersistenceAdapter.save(
                Subscription.create(UserId.newId(), other.id, setOf(SubscriptionChannel.SLACK), now)
            )
            flushAndClear()

            val subscriptions = subscriptionPersistenceAdapter.findAllEnabledByKeywordId(keyword.id)

            assertEquals(listOf(enabledUser), subscriptions.map { it.userId })
        }
    }

    private fun subscription(userId: UserId, channels: Set<SubscriptionChannel>): Subscription {
        return Subscription.create(
            userId = userId,
            keywordId = keyword.id,
            channels = channels,
            registeredAt = now
        )
    }
}
