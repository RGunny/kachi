package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.port.inbound.subscription.model.ListSubscriptionsQuery
import me.rgunny.kachi.user.application.service.fake.FakeKeywordPersistencePort
import me.rgunny.kachi.user.application.service.fake.FakeSubscriptionPersistencePort
import me.rgunny.kachi.user.application.service.fake.FakeUserPersistencePort
import me.rgunny.kachi.user.fixture.UserTestFixture.keyword
import me.rgunny.kachi.user.fixture.UserTestFixture.subscription
import me.rgunny.kachi.user.fixture.UserTestFixture.user
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserStatus
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("SubscriptionQueryService")
class SubscriptionQueryServiceTest {
    private val now = UserTestFixture.NOW
    private val userId = UserId.newId()

    @Nested
    @DisplayName("list()")
    inner class ListSubscriptions {

        @Test
        @DisplayName("내 구독을 키워드 이름과 함께 등록일 역순으로 조회한다")
        fun listSubscriptions() {
            val trump = keyword("Trump")
            val tesla = keyword("Tesla")
            val older = subscription(userId, trump.id)
            val newer = subscription(userId, tesla.id, setOf(SubscriptionChannel.TELEGRAM), now.plus(Duration.ofHours(1)))
            val otherUsers = subscription(UserId.newId(), trump.id)
            val service = service(
                FakeKeywordPersistencePort(listOf(trump, tesla)),
                FakeSubscriptionPersistencePort(listOf(older, newer, otherUsers))
            )

            val results = service.list(ListSubscriptionsQuery(userId))

            assertEquals(listOf(newer.id, older.id), results.map { it.id })
            assertEquals(listOf("Tesla", "Trump"), results.map { it.name })
            assertEquals(listOf("tesla", "trump"), results.map { it.canonicalKey })
        }

        @Test
        @DisplayName("활성 사용자가 아니면 조회할 수 없다")
        fun rejectInactiveUser() {
            val service = service(
                FakeKeywordPersistencePort(),
                FakeSubscriptionPersistencePort(),
                users = mapOf(userId to user(userId, UserStatus.DELETED))
            )

            assertFailsWith<InactiveUserException> {
                service.list(ListSubscriptionsQuery(userId))
            }
        }
    }

    private fun service(
        keywordPort: FakeKeywordPersistencePort,
        subscriptionPort: FakeSubscriptionPersistencePort,
        users: Map<UserId, User> = mapOf(userId to user(userId))
    ): SubscriptionQueryService {
        return SubscriptionQueryService(
            subscriptionPersistencePort = subscriptionPort,
            keywordPersistencePort = keywordPort,
            activeUserValidator = ActiveUserValidator(FakeUserPersistencePort(users))
        )
    }
}
