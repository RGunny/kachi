package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.adapter.inbound.web.dto.RegisterSubscriptionRequest
import me.rgunny.kachi.user.adapter.inbound.web.dto.UpdateSubscriptionRequest
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeListSubscriptionsUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeRegisterSubscriptionUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeUpdateSubscriptionUseCase
import me.rgunny.kachi.user.adapter.inbound.web.security.AuthenticatedUser
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("SubscriptionController")
class SubscriptionControllerTest {
    private val userId = UserId.newId()
    private val authenticatedUser = AuthenticatedUser(userId = userId, role = UserRole.USER)
    private val registerUseCase = FakeRegisterSubscriptionUseCase()
    private val updateUseCase = FakeUpdateSubscriptionUseCase()
    private val listUseCase = FakeListSubscriptionsUseCase()
    private val controller = SubscriptionController(registerUseCase, updateUseCase, listUseCase)

    @Nested
    @DisplayName("listMySubscriptions()")
    inner class ListMySubscriptions {

        @Test
        @DisplayName("인증 사용자의 구독 목록을 조회한다")
        fun listMySubscriptions() {
            val response = controller.listMySubscriptions(authenticatedUser)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(userId, listUseCase.query.userId)
            val body = response.body?.data?.single()
            assertEquals("Trump", body?.name)
            assertEquals("trump", body?.canonicalKey)
            assertEquals(setOf(SubscriptionChannel.SLACK), body?.channels)
        }
    }

    @Nested
    @DisplayName("registerMySubscription()")
    inner class RegisterMySubscription {

        @Test
        @DisplayName("구독 등록 요청을 커맨드로 옮기고 201을 돌려준다")
        fun registerMySubscription() {
            val response = controller.registerMySubscription(
                authenticatedUser = authenticatedUser,
                request = RegisterSubscriptionRequest(name = "SPACE-X", channels = setOf(SubscriptionChannel.TELEGRAM))
            )

            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals(userId, registerUseCase.command.userId)
            assertEquals("SPACE-X", registerUseCase.command.name)
            assertEquals(setOf(SubscriptionChannel.TELEGRAM), registerUseCase.command.channels)
            assertEquals("space-x", response.body?.data?.canonicalKey)
        }
    }

    @Nested
    @DisplayName("updateMySubscription()")
    inner class UpdateMySubscription {

        @Test
        @DisplayName("구독 수정 요청을 커맨드로 옮긴다")
        fun updateMySubscription() {
            val subscriptionId = UUID.randomUUID()

            val response = controller.updateMySubscription(
                authenticatedUser = authenticatedUser,
                subscriptionId = subscriptionId,
                request = UpdateSubscriptionRequest(channels = setOf(SubscriptionChannel.DISCORD), enabled = false)
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(SubscriptionId.of(subscriptionId), updateUseCase.command.subscriptionId)
            assertEquals(userId, updateUseCase.command.userId)
            assertEquals(setOf(SubscriptionChannel.DISCORD), updateUseCase.command.channels)
            assertEquals(false, updateUseCase.command.enabled)
            assertEquals(FakeUpdateSubscriptionUseCase.DISABLED_AT, response.body?.data?.disabledAt)
        }
    }
}
