package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateSubscriptionException
import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.exception.SubscriptionAccessDeniedException
import me.rgunny.kachi.user.application.exception.SubscriptionNotFoundException
import me.rgunny.kachi.user.application.exception.UserNotFoundException
import me.rgunny.kachi.user.application.port.inbound.subscription.model.RegisterSubscriptionCommand
import me.rgunny.kachi.user.application.port.inbound.subscription.model.UpdateSubscriptionCommand
import me.rgunny.kachi.user.application.service.fake.FakeKeywordPersistencePort
import me.rgunny.kachi.user.application.service.fake.FakeSubscriptionPersistencePort
import me.rgunny.kachi.user.application.service.fake.FakeUserPersistencePort
import me.rgunny.kachi.user.fixture.UserTestFixture.keyword
import me.rgunny.kachi.user.fixture.UserTestFixture.subscription
import me.rgunny.kachi.user.fixture.UserTestFixture.user
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserStatus
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("SubscriptionCommandService")
class SubscriptionCommandServiceTest {
    private val now = UserTestFixture.NOW
    private val userId = UserId.newId()
    private val slack = setOf(SubscriptionChannel.SLACK)

    @Nested
    @DisplayName("register()")
    inner class Register {

        @Test
        @DisplayName("canonical 키워드가 없으면 만들고 구독을 저장한다")
        fun createKeywordAndSubscription() {
            val keywordPort = FakeKeywordPersistencePort()
            val subscriptionPort = FakeSubscriptionPersistencePort()
            val service = service(keywordPort, subscriptionPort)

            val result = service.register(RegisterSubscriptionCommand(userId, "  SPACE-X ", slack))

            assertEquals("SPACE-X", result.name)
            assertEquals("space-x", result.canonicalKey)
            assertEquals(slack, result.channels)
            assertEquals(true, result.enabled)
            assertEquals(now, result.registeredAt)
            assertEquals(1, keywordPort.savedKeywords.size)
            assertEquals(keywordPort.savedKeywords.single().id, result.keywordId)
            assertEquals(1, subscriptionPort.savedSubscriptions.size)
        }

        @Test
        @DisplayName("표기가 달라도 canonical 키워드가 있으면 그 키워드에 구독을 건다")
        fun reuseExistingCanonicalKeyword() {
            val keyword = keyword("Tesla")
            val keywordPort = FakeKeywordPersistencePort(keywords = listOf(keyword))
            val subscriptionPort = FakeSubscriptionPersistencePort()
            val service = service(keywordPort, subscriptionPort)

            val result = service.register(RegisterSubscriptionCommand(userId, "TESLA", slack))

            assertEquals(keyword.id, result.keywordId)
            assertEquals("Tesla", result.name)
            assertTrue(keywordPort.savedKeywords.isEmpty())
        }

        @Test
        @DisplayName("같은 사용자가 같은 canonical 키워드를 다시 구독하면 거부한다")
        fun rejectDuplicateSubscription() {
            val keyword = keyword("Tesla")
            val existing = subscription(userId, keyword.id)
            val keywordPort = FakeKeywordPersistencePort(keywords = listOf(keyword))
            val subscriptionPort = FakeSubscriptionPersistencePort(listOf(existing))
            val service = service(keywordPort, subscriptionPort)

            val exception = assertFailsWith<DuplicateSubscriptionException> {
                service.register(RegisterSubscriptionCommand(userId, "tesla", slack))
            }

            assertEquals("Tesla", exception.displayName.value)
            assertTrue(subscriptionPort.savedSubscriptions.isEmpty())
        }

        @Test
        @DisplayName("사용자가 없으면 구독할 수 없다")
        fun rejectMissingUser() {
            val subscriptionPort = FakeSubscriptionPersistencePort()
            val service = service(FakeKeywordPersistencePort(), subscriptionPort, users = emptyMap())

            assertFailsWith<UserNotFoundException> {
                service.register(RegisterSubscriptionCommand(userId, "Tesla", slack))
            }

            assertFalse(subscriptionPort.existsCalled)
        }

        @Test
        @DisplayName("활성 사용자가 아니면 구독할 수 없다")
        fun rejectInactiveUser() {
            val subscriptionPort = FakeSubscriptionPersistencePort()
            val service = service(
                FakeKeywordPersistencePort(),
                subscriptionPort,
                users = mapOf(userId to user(userId, UserStatus.DELETED))
            )

            assertFailsWith<InactiveUserException> {
                service.register(RegisterSubscriptionCommand(userId, "Tesla", slack))
            }

            assertFalse(subscriptionPort.existsCalled)
        }
    }

    @Nested
    @DisplayName("update()")
    inner class Update {
        private val keyword = keyword("Tesla")
        private val subscription = subscription(userId, keyword.id)

        @Test
        @DisplayName("채널을 바꾸고 저장한다")
        fun changeChannels() {
            val subscriptionPort = FakeSubscriptionPersistencePort(listOf(subscription))
            val service = service(FakeKeywordPersistencePort(listOf(keyword)), subscriptionPort)

            val result = service.update(
                UpdateSubscriptionCommand(subscription.id, userId, channels = setOf(SubscriptionChannel.DISCORD))
            )

            assertEquals(setOf(SubscriptionChannel.DISCORD), result.channels)
            assertEquals("Tesla", result.name)
            assertEquals(setOf(SubscriptionChannel.DISCORD), subscriptionPort.savedSubscriptions.single().channels)
        }

        @Test
        @DisplayName("구독을 비활성화한다")
        fun disable() {
            val subscriptionPort = FakeSubscriptionPersistencePort(listOf(subscription))
            val service = service(FakeKeywordPersistencePort(listOf(keyword)), subscriptionPort)

            val result = service.update(UpdateSubscriptionCommand(subscription.id, userId, enabled = false))

            assertEquals(false, result.enabled)
            assertEquals(now, result.disabledAt)
        }

        @Test
        @DisplayName("비활성 구독을 활성화한다")
        fun enable() {
            val disabled = subscription.disable(now)
            val subscriptionPort = FakeSubscriptionPersistencePort(listOf(disabled))
            val service = service(FakeKeywordPersistencePort(listOf(keyword)), subscriptionPort)

            val result = service.update(UpdateSubscriptionCommand(disabled.id, userId, enabled = true))

            assertEquals(true, result.enabled)
            assertEquals(null, result.disabledAt)
        }

        @Test
        @DisplayName("수정할 값이 없으면 실패한다")
        fun rejectEmptyCommand() {
            assertFailsWith<IllegalArgumentException> {
                UpdateSubscriptionCommand(SubscriptionId.newId(), userId)
            }
        }

        @Test
        @DisplayName("구독이 없으면 실패한다")
        fun rejectMissingSubscription() {
            val service = service(FakeKeywordPersistencePort(), FakeSubscriptionPersistencePort())

            assertFailsWith<SubscriptionNotFoundException> {
                service.update(UpdateSubscriptionCommand(SubscriptionId.newId(), userId, enabled = false))
            }
        }

        @Test
        @DisplayName("소유자가 아니면 수정할 수 없다")
        fun rejectNonOwner() {
            val otherUserId = UserId.newId()
            val subscriptionPort = FakeSubscriptionPersistencePort(listOf(subscription))
            val service = service(
                FakeKeywordPersistencePort(listOf(keyword)),
                subscriptionPort,
                users = mapOf(userId to user(userId), otherUserId to user(otherUserId))
            )

            assertFailsWith<SubscriptionAccessDeniedException> {
                service.update(UpdateSubscriptionCommand(subscription.id, otherUserId, enabled = false))
            }

            assertTrue(subscriptionPort.savedSubscriptions.isEmpty())
        }

        @Test
        @DisplayName("소유자가 활성 사용자가 아니면 수정할 수 없다")
        fun rejectInactiveOwner() {
            val subscriptionPort = FakeSubscriptionPersistencePort(listOf(subscription))
            val service = service(
                FakeKeywordPersistencePort(listOf(keyword)),
                subscriptionPort,
                users = mapOf(userId to user(userId, UserStatus.DELETED))
            )

            assertFailsWith<InactiveUserException> {
                service.update(UpdateSubscriptionCommand(subscription.id, userId, enabled = false))
            }

            assertTrue(subscriptionPort.savedSubscriptions.isEmpty())
        }
    }

    private fun service(
        keywordPort: FakeKeywordPersistencePort,
        subscriptionPort: FakeSubscriptionPersistencePort,
        users: Map<UserId, User> = mapOf(userId to user(userId))
    ): SubscriptionCommandService {
        return SubscriptionCommandService(
            keywordPersistencePort = keywordPort,
            subscriptionPersistencePort = subscriptionPort,
            activeUserValidator = ActiveUserValidator(FakeUserPersistencePort(users)),
            clock = UserTestFixture.CLOCK
        )
    }
}
