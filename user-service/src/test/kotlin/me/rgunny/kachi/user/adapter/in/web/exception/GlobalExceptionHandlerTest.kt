package me.rgunny.kachi.user.adapter.`in`.web.exception

import me.rgunny.kachi.user.adapter.`in`.web.KeywordController
import me.rgunny.kachi.user.adapter.`in`.web.UserController
import me.rgunny.kachi.user.adapter.`in`.web.fake.FakeRegisterKeywordUseCase
import me.rgunny.kachi.user.adapter.`in`.web.fake.FakeRegisterUserUseCase
import me.rgunny.kachi.user.adapter.`in`.web.fake.FakeUpdateKeywordUseCase
import me.rgunny.kachi.user.adapter.`in`.web.fake.WebMvcFakeUseCaseConfig
import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.exception.DuplicateKeywordException
import me.rgunny.kachi.user.application.exception.KeywordNotFoundException
import me.rgunny.kachi.user.config.ApiVersionConfig
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebMvcTest(controllers = [UserController::class, KeywordController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiVersionConfig::class, WebMvcFakeUseCaseConfig::class)
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val registerUserUseCase: FakeRegisterUserUseCase,
    private val registerKeywordUseCase: FakeRegisterKeywordUseCase,
    private val updateKeywordUseCase: FakeUpdateKeywordUseCase
) {

    @BeforeEach
    fun setUp() {
        registerUserUseCase.exception = null
        registerKeywordUseCase.exception = null
        updateKeywordUseCase.exception = null
    }

    @Nested
    @DisplayName("application exception")
    inner class ApplicationException {

        @Test
        @DisplayName("중복 이메일 예외는 409 응답으로 변환한다")
        fun handleDuplicateEmail() {
            registerUserUseCase.exception = DuplicateEmailException(Email.of("rgunny@kachi.com"))

            val response = mockMvc.post("/api/v1/users") {
                contentType = MediaType.APPLICATION_JSON
                content = registerUserBody(email = "rgunny@kachi.com")
            }.andExpect {
                status { isConflict() }
            }.andReturn().response

            assertErrorResponse(
                actual = response.contentAsString,
                code = "DUPLICATE_EMAIL",
                message = "이미 사용 중인 이메일입니다: rgunny@kachi.com"
            )
        }

        @Test
        @DisplayName("중복 키워드 예외는 409 응답으로 변환한다")
        fun handleDuplicateKeyword() {
            val userId = UserId.newId()
            registerKeywordUseCase.exception = DuplicateKeywordException(userId, KeywordName.of("Trump"))

            val response = mockMvc.post("/api/v1/users/${userId.value}/keywords") {
                contentType = MediaType.APPLICATION_JSON
                content = registerKeywordBody(name = "Trump")
            }.andExpect {
                status { isConflict() }
            }.andReturn().response

            assertErrorResponse(
                actual = response.contentAsString,
                code = "DUPLICATE_KEYWORD",
                message = "이미 등록된 키워드입니다: Trump"
            )
        }

        @Test
        @DisplayName("키워드 없음 예외는 404 응답으로 변환한다")
        fun handleKeywordNotFound() {
            val keywordId = KeywordId.newId()
            updateKeywordUseCase.exception = KeywordNotFoundException(keywordId)

            val response = mockMvc.patch("/api/v1/keywords/${keywordId.value}") {
                contentType = MediaType.APPLICATION_JSON
                content = updateKeywordBody(name = "Trump")
            }.andExpect {
                status { isNotFound() }
            }.andReturn().response

            assertErrorResponse(
                actual = response.contentAsString,
                code = "KEYWORD_NOT_FOUND",
                message = "키워드를 찾을 수 없습니다: ${keywordId.value}"
            )
        }
    }

    @Nested
    @DisplayName("validation exception")
    inner class ValidationException {

        @Test
        @DisplayName("요청 검증 실패는 400 응답으로 변환한다")
        fun handleValidationFailure() {
            val response = mockMvc.post("/api/v1/users") {
                contentType = MediaType.APPLICATION_JSON
                content = registerUserBody(email = "invalid-email")
            }.andExpect {
                status { isBadRequest() }
            }.andReturn().response

            assertTrue(response.contentAsString.contains("\"code\":\"INVALID_REQUEST\""))
            assertTrue(response.contentAsString.contains("\"success\":false"))
        }
    }

    private fun assertErrorResponse(
        actual: String,
        code: String,
        message: String
    ) {
        assertEquals(
            """{"success":false,"data":null,"error":{"code":"$code","message":"$message"}}""",
            actual
        )
    }

    private fun registerUserBody(email: String): String {
        return """
            {
              "email": "$email",
              "nickname": "rgunny",
              "authProvider": "GOOGLE"
            }
        """.trimIndent()
    }

    private fun registerKeywordBody(name: String): String {
        return """
            {
              "name": "$name"
            }
        """.trimIndent()
    }

    private fun updateKeywordBody(name: String): String {
        return """
            {
              "name": "$name",
              "enabled": true
            }
        """.trimIndent()
    }

}
