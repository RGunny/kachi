package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.adapter.inbound.web.dto.CompleteTelegramLinkRequest
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeCompleteTelegramLinkUseCase
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

@DisplayName("InternalChannelBindingController")
class InternalChannelBindingControllerTest {
    private val completeUseCase = FakeCompleteTelegramLinkUseCase()
    private val controller = InternalChannelBindingController(completeUseCase)

    @Test
    @DisplayName("토큰과 chat id를 커맨드로 옮기고 204를 돌려준다")
    fun completeTelegramLink() {
        val response = controller.completeTelegramLink(CompleteTelegramLinkRequest(token = "token", chatId = "123"))

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertEquals("token", completeUseCase.command.token)
        assertEquals("123", completeUseCase.command.chatId)
    }
}
