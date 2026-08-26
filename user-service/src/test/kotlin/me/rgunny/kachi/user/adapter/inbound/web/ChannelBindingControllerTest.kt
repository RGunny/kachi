package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.adapter.inbound.web.dto.ChannelBindingResponse
import me.rgunny.kachi.user.adapter.inbound.web.dto.RegisterWebhookBindingRequest
import me.rgunny.kachi.user.adapter.inbound.web.dto.TelegramLinkResponse
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeIssueTelegramLinkUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeListChannelBindingsUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeRegisterWebhookBindingUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeRevokeChannelBindingUseCase
import me.rgunny.kachi.user.adapter.inbound.web.security.AuthenticatedUser
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@DisplayName("ChannelBindingController")
class ChannelBindingControllerTest {
    private val userId = UserId.newId()
    private val authenticatedUser = AuthenticatedUser(userId = userId, role = UserRole.USER)
    private val registerUseCase = FakeRegisterWebhookBindingUseCase()
    private val issueUseCase = FakeIssueTelegramLinkUseCase()
    private val revokeUseCase = FakeRevokeChannelBindingUseCase()
    private val listUseCase = FakeListChannelBindingsUseCase()
    private val controller = ChannelBindingController(registerUseCase, issueUseCase, revokeUseCase, listUseCase)

    @Nested
    @DisplayName("listMyChannelBindings()")
    inner class ListMyChannelBindings {

        @Test
        @DisplayName("인증 사용자의 바인딩 목록을 마스킹한 주소로 돌려준다")
        fun listMaskedBindings() {
            val response = controller.listMyChannelBindings(authenticatedUser)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(userId, listUseCase.query.userId)
            val body = response.body?.data?.single()
            assertEquals(SubscriptionChannel.SLACK, body?.channel)
            assertEquals("https://hooks.slack.com/****", body?.addressMasked)
        }
    }

    @Nested
    @DisplayName("bindMyChannel()")
    inner class BindMyChannel {

        @Test
        @DisplayName("Slack은 본문의 webhook URL로 등록하고 바인딩을 돌려준다")
        fun bindSlackWebhook() {
            val response = controller.bindMyChannel(
                authenticatedUser = authenticatedUser,
                channel = SubscriptionChannel.SLACK,
                request = RegisterWebhookBindingRequest("https://hooks.slack.com/services/T000/B000/XXXX")
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(userId, registerUseCase.command.userId)
            assertEquals(SubscriptionChannel.SLACK, registerUseCase.command.channel)
            assertEquals("https://hooks.slack.com/services/T000/B000/XXXX", registerUseCase.command.webhookUrl)
            val body = assertIs<ChannelBindingResponse>(response.body?.data)
            assertEquals(ChannelBindingStatus.ACTIVE, body.status)
            assertEquals("https://hooks.slack.com/****", body.addressMasked)
        }

        @Test
        @DisplayName("Telegram은 본문 없이 연결 링크를 돌려준다")
        fun issueTelegramLink() {
            val response = controller.bindMyChannel(
                authenticatedUser = authenticatedUser,
                channel = SubscriptionChannel.TELEGRAM,
                request = null
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(userId, issueUseCase.command.userId)
            val body = assertIs<TelegramLinkResponse>(response.body?.data)
            assertEquals(FakeIssueTelegramLinkUseCase.LINK_URL, body.linkUrl)
            assertEquals(FakeIssueTelegramLinkUseCase.EXPIRES_AT, body.expiresAt)
        }

        @Test
        @DisplayName("webhook 채널에 본문이 없으면 거부한다")
        fun rejectWebhookWithoutBody() {
            assertFailsWith<IllegalArgumentException> {
                controller.bindMyChannel(authenticatedUser, SubscriptionChannel.DISCORD, request = null)
            }
        }
    }

    @Nested
    @DisplayName("revokeMyChannelBinding()")
    inner class RevokeMyChannelBinding {

        @Test
        @DisplayName("해지 요청을 커맨드로 옮기고 204를 돌려준다")
        fun revoke() {
            val response = controller.revokeMyChannelBinding(authenticatedUser, SubscriptionChannel.SLACK)

            assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
            assertEquals(userId, revokeUseCase.command.userId)
            assertEquals(SubscriptionChannel.SLACK, revokeUseCase.command.channel)
        }
    }
}
