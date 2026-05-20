package me.rgunny.kachi.user.adapter.`in`.web

import me.rgunny.kachi.user.adapter.`in`.web.dto.RegisterKeywordRequest
import me.rgunny.kachi.user.adapter.`in`.web.dto.UpdateKeywordRequest
import me.rgunny.kachi.user.adapter.`in`.web.security.AuthenticatedUser
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordResult
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordUseCase
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordResult
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordUseCase
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("KeywordController")
class KeywordControllerTest {
    private val userId = UUID.randomUUID()
    private val keywordId = UUID.randomUUID()
    private val registeredAt = Instant.parse("2026-05-20T00:00:00Z")
    private val disabledAt = Instant.parse("2026-05-20T01:00:00Z")

    @Nested
    @DisplayName("register()")
    inner class Register {

        @Test
        @DisplayName("인증 사용자 기준 관심 키워드 등록 요청을 처리하고 201 응답을 반환한다")
        fun registerMyKeyword() {
            val registerUseCase = FakeRegisterKeywordUseCase()
            val updateUseCase = FakeUpdateKeywordUseCase()
            val controller = KeywordController(registerUseCase, updateUseCase)
            val authenticatedUser = AuthenticatedUser(
                userId = UserId.of(userId),
                role = UserRole.USER
            )

            val response = controller.registerMyKeyword(
                authenticatedUser = authenticatedUser,
                request = RegisterKeywordRequest(name = "Trump")
            )

            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals(UserId.of(userId), registerUseCase.command.userId)
            assertEquals("Trump", registerUseCase.command.name)
            assertEquals("Trump", response.body?.name)
            assertEquals(true, response.body?.enabled)
            assertEquals(null, response.body?.disabledAt)
        }

        @Test
        @DisplayName("관심 키워드 등록 요청을 처리하고 201 응답을 반환한다")
        fun registerKeyword() {
            val registerUseCase = FakeRegisterKeywordUseCase()
            val updateUseCase = FakeUpdateKeywordUseCase()
            val controller = KeywordController(registerUseCase, updateUseCase)

            val response = controller.register(
                userId = userId,
                request = RegisterKeywordRequest(name = "Trump")
            )

            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals(UserId.of(userId), registerUseCase.command.userId)
            assertEquals("Trump", registerUseCase.command.name)
            assertEquals("Trump", response.body?.name)
            assertEquals(true, response.body?.enabled)
            assertEquals(null, response.body?.disabledAt)
        }
    }

    @Nested
    @DisplayName("update()")
    inner class Update {
        @Test
        @DisplayName("관심 키워드 수정 요청을 처리한다")
        fun updateKeyword() {
            val registerUseCase = FakeRegisterKeywordUseCase()
            val updateUseCase = FakeUpdateKeywordUseCase()
            val controller = KeywordController(registerUseCase, updateUseCase)

            val response = controller.update(
                keywordId = keywordId,
                request = UpdateKeywordRequest(name = "Tesla", enabled = false)
            )

            assertEquals(KeywordId.of(keywordId), updateUseCase.command.keywordId)
            assertEquals("Tesla", updateUseCase.command.name)
            assertEquals(false, updateUseCase.command.enabled)
            assertEquals("Tesla", response.name)
            assertEquals(false, response.enabled)
            assertEquals(disabledAt, response.disabledAt)
        }
    }

    private inner class FakeRegisterKeywordUseCase : RegisterKeywordUseCase {
        lateinit var command: RegisterKeywordCommand

        override fun register(command: RegisterKeywordCommand): RegisterKeywordResult {
            this.command = command

            return RegisterKeywordResult(
                id = KeywordId.of(keywordId),
                userId = command.userId,
                name = command.name,
                enabled = true,
                registeredAt = registeredAt
            )
        }
    }

    private inner class FakeUpdateKeywordUseCase : UpdateKeywordUseCase {
        lateinit var command: UpdateKeywordCommand

        override fun update(command: UpdateKeywordCommand): UpdateKeywordResult {
            this.command = command

            return UpdateKeywordResult(
                id = command.keywordId,
                userId = UserId.of(userId),
                name = command.name ?: "Trump",
                enabled = command.enabled ?: true,
                registeredAt = registeredAt,
                disabledAt = if (command.enabled == false) disabledAt else null
            )
        }
    }
}
