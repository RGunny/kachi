package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.adapter.inbound.web.dto.RegisterKeywordRequest
import me.rgunny.kachi.user.adapter.inbound.web.dto.UpdateKeywordRequest
import me.rgunny.kachi.user.adapter.inbound.web.security.AuthenticatedUser
import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListKeywordResult
import me.rgunny.kachi.user.application.port.inbound.keyword.model.ListKeywordsQuery
import me.rgunny.kachi.user.application.port.inbound.keyword.ListKeywordsUseCase
import me.rgunny.kachi.user.application.port.inbound.keyword.model.RegisterKeywordCommand
import me.rgunny.kachi.user.application.port.inbound.keyword.model.RegisterKeywordResult
import me.rgunny.kachi.user.application.port.inbound.keyword.RegisterKeywordUseCase
import me.rgunny.kachi.user.application.port.inbound.keyword.model.UpdateKeywordCommand
import me.rgunny.kachi.user.application.port.inbound.keyword.model.UpdateKeywordResult
import me.rgunny.kachi.user.application.port.inbound.keyword.UpdateKeywordUseCase
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("KeywordController")
class KeywordControllerTest {
    private val userId = UUID.randomUUID()
    private val keywordId = UUID.randomUUID()
    private val registeredAt = UserTestFixture.NOW
    private val disabledAt = UserTestFixture.NOW.plus(Duration.ofHours(1))

    @Nested
    @DisplayName("listMyKeywords()")
    inner class ListMyKeywords {

        @Test
        @DisplayName("인증 사용자 기준 관심 키워드 목록을 조회한다")
        fun listMyKeywords() {
            val registerUseCase = FakeRegisterKeywordUseCase()
            val updateUseCase = FakeUpdateKeywordUseCase()
            val listUseCase = FakeListKeywordsUseCase()
            val controller = KeywordController(registerUseCase, updateUseCase, listUseCase)
            val authenticatedUser = AuthenticatedUser(
                userId = UserId.of(userId),
                role = UserRole.USER
            )

            val response = controller.listMyKeywords(authenticatedUser)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(true, response.body?.success)
            assertEquals(null, response.body?.error)
            assertEquals(UserId.of(userId), listUseCase.query.userId)
            assertEquals(1, response.body?.data?.size)
            assertEquals("Trump", response.body?.data?.single()?.name)
            assertEquals(false, response.body?.data?.single()?.enabled)
            assertEquals(disabledAt, response.body?.data?.single()?.disabledAt)
        }
    }

    @Nested
    @DisplayName("register()")
    inner class Register {

        @Test
        @DisplayName("인증 사용자 기준 관심 키워드 등록 요청을 처리하고 201 응답을 반환한다")
        fun registerMyKeyword() {
            val registerUseCase = FakeRegisterKeywordUseCase()
            val updateUseCase = FakeUpdateKeywordUseCase()
            val listUseCase = FakeListKeywordsUseCase()
            val controller = KeywordController(registerUseCase, updateUseCase, listUseCase)
            val authenticatedUser = AuthenticatedUser(
                userId = UserId.of(userId),
                role = UserRole.USER
            )

            val response = controller.registerMyKeyword(
                authenticatedUser = authenticatedUser,
                request = RegisterKeywordRequest(name = "Trump")
            )

            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals(true, response.body?.success)
            assertEquals(null, response.body?.error)
            assertEquals(UserId.of(userId), registerUseCase.command.userId)
            assertEquals("Trump", registerUseCase.command.name)
            assertEquals("Trump", response.body?.data?.name)
            assertEquals(true, response.body?.data?.enabled)
            assertEquals(null, response.body?.data?.disabledAt)
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
            val listUseCase = FakeListKeywordsUseCase()
            val controller = KeywordController(registerUseCase, updateUseCase, listUseCase)
            val authenticatedUser = AuthenticatedUser(
                userId = UserId.of(userId),
                role = UserRole.USER
            )

            val response = controller.update(
                authenticatedUser = authenticatedUser,
                keywordId = keywordId,
                request = UpdateKeywordRequest(name = "Tesla", enabled = false)
            )

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(true, response.body?.success)
            assertEquals(null, response.body?.error)
            assertEquals(KeywordId.of(keywordId), updateUseCase.command.keywordId)
            assertEquals(UserId.of(userId), updateUseCase.command.userId)
            assertEquals("Tesla", updateUseCase.command.name)
            assertEquals(false, updateUseCase.command.enabled)
            assertEquals("Tesla", response.body?.data?.name)
            assertEquals(false, response.body?.data?.enabled)
            assertEquals(disabledAt, response.body?.data?.disabledAt)
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

    private inner class FakeListKeywordsUseCase : ListKeywordsUseCase {
        lateinit var query: ListKeywordsQuery

        override fun list(query: ListKeywordsQuery): List<ListKeywordResult> {
            this.query = query

            return listOf(
                ListKeywordResult(
                    id = KeywordId.of(keywordId),
                    userId = query.userId,
                    name = "Trump",
                    enabled = false,
                    registeredAt = registeredAt,
                    disabledAt = disabledAt
                )
            )
        }
    }
}
