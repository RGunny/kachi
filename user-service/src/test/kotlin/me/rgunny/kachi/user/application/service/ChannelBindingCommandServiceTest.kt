package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.ChannelBindingNotFoundException
import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.exception.InvalidChannelAddressException
import me.rgunny.kachi.user.application.exception.LinkTokenInvalidException
import me.rgunny.kachi.user.application.port.inbound.binding.model.CompleteTelegramLinkCommand
import me.rgunny.kachi.user.application.port.inbound.binding.model.IssueTelegramLinkCommand
import me.rgunny.kachi.user.application.port.inbound.binding.model.RegisterWebhookBindingCommand
import me.rgunny.kachi.user.application.port.inbound.binding.model.RevokeChannelBindingCommand
import me.rgunny.kachi.user.application.service.fake.FakeChannelBindingPersistencePort
import me.rgunny.kachi.user.application.service.fake.FakeUserPersistencePort
import me.rgunny.kachi.user.domain.ChannelBinding
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.LinkToken
import me.rgunny.kachi.user.domain.LinkTokenHash
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserStatus
import me.rgunny.kachi.user.fixture.UserTestFixture
import me.rgunny.kachi.user.fixture.UserTestFixture.activeBinding
import me.rgunny.kachi.user.fixture.UserTestFixture.user
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("ChannelBindingCommandService")
class ChannelBindingCommandServiceTest {
    private val now = UserTestFixture.NOW
    private val userId = UserId.newId()
    private val ttl = Duration.ofMinutes(10)
    private val slackUrl = "https://hooks.slack.com/services/T000/B000/NEW"

    @Nested
    @DisplayName("register()")
    inner class Register {

        @Test
        @DisplayName("바인딩이 없으면 ACTIVE 바인딩을 새로 만든다")
        fun createActiveBinding() {
            val port = FakeChannelBindingPersistencePort()
            val service = service(port)

            val result = service.register(RegisterWebhookBindingCommand(userId, SubscriptionChannel.SLACK, slackUrl))

            assertEquals(ChannelBindingStatus.ACTIVE, result.status)
            assertEquals(SubscriptionChannel.SLACK, result.channel)
            assertEquals(now, result.boundAt)
            assertEquals("https://hooks.slack.com/****", result.addressMasked)
            assertEquals(slackUrl, port.savedBindings.single().address?.value)
        }

        @Test
        @DisplayName("바인딩이 있으면 같은 id로 주소를 교체한다")
        fun replaceAddressKeepingId() {
            val existing = activeBinding(userId, SubscriptionChannel.SLACK)
            val port = FakeChannelBindingPersistencePort(listOf(existing))
            val service = service(port)

            val result = service.register(RegisterWebhookBindingCommand(userId, SubscriptionChannel.SLACK, slackUrl))

            assertEquals(existing.id, result.id)
            assertEquals(slackUrl, port.savedBindings.single().address?.value)
        }

        @Test
        @DisplayName("해지된 바인딩은 같은 id로 되살아난다")
        fun reviveRevokedBinding() {
            val revoked = activeBinding(userId, SubscriptionChannel.SLACK).revoke(now)
            val port = FakeChannelBindingPersistencePort(listOf(revoked))
            val service = service(port)

            val result = service.register(RegisterWebhookBindingCommand(userId, SubscriptionChannel.SLACK, slackUrl))

            assertEquals(revoked.id, result.id)
            assertEquals(ChannelBindingStatus.ACTIVE, result.status)
            assertNull(result.revokedAt)
        }

        @Test
        @DisplayName("채널 형식에 맞지 않는 주소는 거부한다")
        fun rejectInvalidAddress() {
            val port = FakeChannelBindingPersistencePort()
            val service = service(port)

            val exception = assertFailsWith<InvalidChannelAddressException> {
                service.register(RegisterWebhookBindingCommand(userId, SubscriptionChannel.DISCORD, slackUrl))
            }

            assertEquals(SubscriptionChannel.DISCORD, exception.channel)
            assertTrue(port.savedBindings.isEmpty())
        }

        @Test
        @DisplayName("Telegram은 webhook으로 등록할 수 없다")
        fun rejectTelegramWebhook() {
            val service = service(FakeChannelBindingPersistencePort())

            assertFailsWith<IllegalArgumentException> {
                service.register(RegisterWebhookBindingCommand(userId, SubscriptionChannel.TELEGRAM, "123"))
            }
        }

        @Test
        @DisplayName("활성 사용자가 아니면 등록할 수 없다")
        fun rejectInactiveUser() {
            val port = FakeChannelBindingPersistencePort()
            val service = service(port, users = mapOf(userId to user(userId, UserStatus.DELETED)))

            assertFailsWith<InactiveUserException> {
                service.register(RegisterWebhookBindingCommand(userId, SubscriptionChannel.SLACK, slackUrl))
            }

            assertTrue(port.savedBindings.isEmpty())
        }
    }

    @Nested
    @DisplayName("issue()")
    inner class Issue {

        @Test
        @DisplayName("PENDING 바인딩을 만들고 봇 링크와 만료 시각을 돌려준다")
        fun issueLink() {
            val port = FakeChannelBindingPersistencePort()
            val service = service(port)

            val result = service.issue(IssueTelegramLinkCommand(userId))

            val saved = port.savedBindings.single()
            assertEquals(ChannelBindingStatus.PENDING, saved.status)
            assertEquals(SubscriptionChannel.TELEGRAM, saved.channel)
            assertEquals(now.plus(ttl), result.expiresAt)
            assertEquals(now.plus(ttl), saved.linkTokenExpiresAt)
            val token = result.linkUrl.removePrefix("https://t.me/kachi_test_bot?start=")
            assertTrue(token.isNotEmpty() && token != result.linkUrl)
            assertEquals(LinkTokenHash.of(token), saved.linkTokenHash)
        }

        @Test
        @DisplayName("다시 발급하면 같은 바인딩의 토큰이 바뀌고 이전 주소는 사라진다")
        fun reissueReplacesToken() {
            val existing = activeBinding(userId, SubscriptionChannel.TELEGRAM)
            val port = FakeChannelBindingPersistencePort(listOf(existing))
            val service = service(port)

            service.issue(IssueTelegramLinkCommand(userId))

            val saved = port.savedBindings.single()
            assertEquals(existing.id, saved.id)
            assertEquals(ChannelBindingStatus.PENDING, saved.status)
            assertNull(saved.address)
        }
    }

    @Nested
    @DisplayName("complete()")
    inner class Complete {

        @Test
        @DisplayName("토큰이 맞으면 chat id를 주소로 ACTIVE가 된다")
        fun completeLink() {
            val token = LinkToken.issue(now, ttl)
            val pending = ChannelBinding.createPending(userId, SubscriptionChannel.TELEGRAM, token, now)
            val port = FakeChannelBindingPersistencePort(listOf(pending))
            val service = service(port)

            service.complete(CompleteTelegramLinkCommand(token.value, "987654321"))

            val saved = port.savedBindings.single()
            assertEquals(ChannelBindingStatus.ACTIVE, saved.status)
            assertEquals("987654321", saved.address?.value)
            assertNull(saved.linkTokenHash)
            assertNotNull(saved.boundAt)
        }

        @Test
        @DisplayName("모르는 토큰은 거부한다")
        fun rejectUnknownToken() {
            val service = service(FakeChannelBindingPersistencePort())

            assertFailsWith<LinkTokenInvalidException> {
                service.complete(CompleteTelegramLinkCommand("unknown", "987654321"))
            }
        }

        @Test
        @DisplayName("만료된 토큰은 거부한다")
        fun rejectExpiredToken() {
            val token = LinkToken.issue(now.minus(ttl), ttl)
            val pending = ChannelBinding.createPending(userId, SubscriptionChannel.TELEGRAM, token, now.minus(ttl))
            val port = FakeChannelBindingPersistencePort(listOf(pending))
            val service = service(port)

            assertFailsWith<LinkTokenInvalidException> {
                service.complete(CompleteTelegramLinkCommand(token.value, "987654321"))
            }

            assertTrue(port.savedBindings.isEmpty())
        }

        @Test
        @DisplayName("chat id가 숫자가 아니면 거부한다")
        fun rejectInvalidChatId() {
            val token = LinkToken.issue(now, ttl)
            val pending = ChannelBinding.createPending(userId, SubscriptionChannel.TELEGRAM, token, now)
            val service = service(FakeChannelBindingPersistencePort(listOf(pending)))

            assertFailsWith<InvalidChannelAddressException> {
                service.complete(CompleteTelegramLinkCommand(token.value, "not-a-number"))
            }
        }
    }

    @Nested
    @DisplayName("revoke()")
    inner class Revoke {

        @Test
        @DisplayName("주소를 지우고 REVOKED로 저장한다")
        fun revokeBinding() {
            val existing = activeBinding(userId, SubscriptionChannel.SLACK)
            val port = FakeChannelBindingPersistencePort(listOf(existing))
            val service = service(port)

            service.revoke(RevokeChannelBindingCommand(userId, SubscriptionChannel.SLACK))

            val saved = port.savedBindings.single()
            assertEquals(ChannelBindingStatus.REVOKED, saved.status)
            assertNull(saved.address)
            assertEquals(now, saved.revokedAt)
        }

        @Test
        @DisplayName("바인딩이 없으면 해지할 수 없다")
        fun rejectMissingBinding() {
            val service = service(FakeChannelBindingPersistencePort())

            assertFailsWith<ChannelBindingNotFoundException> {
                service.revoke(RevokeChannelBindingCommand(userId, SubscriptionChannel.SLACK))
            }
        }

        @Test
        @DisplayName("다른 사용자의 바인딩은 보이지 않는다")
        fun ignoreOtherUsersBinding() {
            val port = FakeChannelBindingPersistencePort(listOf(activeBinding(UserId.newId(), SubscriptionChannel.SLACK)))
            val service = service(port)

            assertFailsWith<ChannelBindingNotFoundException> {
                service.revoke(RevokeChannelBindingCommand(userId, SubscriptionChannel.SLACK))
            }
        }
    }

    private fun service(
        port: FakeChannelBindingPersistencePort,
        users: Map<UserId, User> = mapOf(userId to user(userId))
    ): ChannelBindingCommandService {
        return ChannelBindingCommandService(
            channelBindingPersistencePort = port,
            activeUserValidator = ActiveUserValidator(FakeUserPersistencePort(users)),
            clock = UserTestFixture.CLOCK,
            linkTokenTtl = ttl,
            telegramBotUsername = "kachi_test_bot"
        )
    }
}
