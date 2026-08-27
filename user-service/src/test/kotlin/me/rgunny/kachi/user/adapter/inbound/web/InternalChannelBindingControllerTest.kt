package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.adapter.inbound.web.dto.CompleteTelegramLinkRequest
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeCompleteTelegramLinkUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeResolveChannelBindingUseCase
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.SubscriptionChannel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("InternalChannelBindingController")
class InternalChannelBindingControllerTest {
    private val resolveUseCase = FakeResolveChannelBindingUseCase()
    private val completeUseCase = FakeCompleteTelegramLinkUseCase()
    private val controller = InternalChannelBindingController(resolveUseCase, completeUseCase)

    @Test
    @DisplayName("참조를 질의로 옮기고 평문 주소를 그대로 돌려준다")
    fun resolveChannelBinding() {
        val ref = UUID.randomUUID()

        val response = controller.resolveChannelBinding(ref)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(ref, resolveUseCase.query.ref.value)
        val body = response.body?.data
        assertEquals(SubscriptionChannel.SLACK, body?.channel)
        assertEquals(ChannelBindingStatus.ACTIVE, body?.status)
        assertEquals("https://hooks.slack.com/services/T000/B000/XXXX", body?.address)
    }

    @Test
    @DisplayName("토큰과 chat id를 커맨드로 옮기고 204를 돌려준다")
    fun completeTelegramLink() {
        val response = controller.completeTelegramLink(CompleteTelegramLinkRequest(token = "token", chatId = "123"))

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertEquals("token", completeUseCase.command.token)
        assertEquals("123", completeUseCase.command.chatId)
    }
}
