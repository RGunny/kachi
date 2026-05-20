package me.rgunny.kachi.user.adapter.`in`.web.exception

import me.rgunny.kachi.user.adapter.`in`.web.AuthController
import me.rgunny.kachi.user.adapter.`in`.web.KeywordController
import me.rgunny.kachi.user.adapter.`in`.web.UserController
import me.rgunny.kachi.user.adapter.`in`.web.fake.FakeRegisterKeywordUseCase
import me.rgunny.kachi.user.adapter.`in`.web.fake.FakeRegisterUserUseCase
import me.rgunny.kachi.user.adapter.`in`.web.fake.FakeRefreshTokenUseCase
import me.rgunny.kachi.user.adapter.`in`.web.fake.FakeUpdateKeywordUseCase
import me.rgunny.kachi.user.adapter.`in`.web.fake.WebMvcFakeUseCaseConfig
import me.rgunny.kachi.user.adapter.`in`.web.security.AuthenticatedUser
import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.exception.DuplicateKeywordException
import me.rgunny.kachi.user.application.exception.InvalidTokenException
import me.rgunny.kachi.user.application.exception.KeywordAccessDeniedException
import me.rgunny.kachi.user.application.exception.KeywordNotFoundException
import me.rgunny.kachi.user.config.ApiVersionConfig
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebMvcTest(controllers = [AuthController::class, UserController::class, KeywordController::class])
@AutoConfigureMockMvc(addFilters = false)
@ImportAutoConfiguration(
    SecurityAutoConfiguration::class,
    ServletWebSecurityAutoConfiguration::class,
    SecurityFilterAutoConfiguration::class
)
@Import(ApiVersionConfig::class, WebMvcFakeUseCaseConfig::class)
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val refreshTokenUseCase: FakeRefreshTokenUseCase,
    private val registerUserUseCase: FakeRegisterUserUseCase,
    private val registerKeywordUseCase: FakeRegisterKeywordUseCase,
    private val updateKeywordUseCase: FakeUpdateKeywordUseCase
) {

    @BeforeEach
    fun setUp() {
        registerUserUseCase.exception = null
        refreshTokenUseCase.exception = null
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

            SecurityContextHolder.getContext().authentication = authenticatedUserAuthentication(userId)

            val response = try {
                mockMvc.post("/api/v1/me/keywords") {
                    contentType = MediaType.APPLICATION_JSON
                    content = registerKeywordBody(name = "Trump")
                }.andExpect {
                    status { isConflict() }
                }.andReturn().response
            } finally {
                SecurityContextHolder.clearContext()
            }

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

            SecurityContextHolder.getContext().authentication = authenticatedUserAuthentication()

            val response = try {
                mockMvc.patch("/api/v1/keywords/${keywordId.value}") {
                    contentType = MediaType.APPLICATION_JSON
                    content = updateKeywordBody(name = "Trump")
                }.andExpect {
                    status { isNotFound() }
                }.andReturn().response
            } finally {
                SecurityContextHolder.clearContext()
            }

            assertErrorResponse(
                actual = response.contentAsString,
                code = "KEYWORD_NOT_FOUND",
                message = "키워드를 찾을 수 없습니다: ${keywordId.value}"
            )
        }

        @Test
        @DisplayName("키워드 접근 거부 예외는 403 응답으로 변환한다")
        fun handleKeywordAccessDenied() {
            val keywordId = KeywordId.newId()
            val userId = UserId.newId()
            updateKeywordUseCase.exception = KeywordAccessDeniedException(keywordId, userId)

            SecurityContextHolder.getContext().authentication = authenticatedUserAuthentication()

            val response = try {
                mockMvc.patch("/api/v1/keywords/${keywordId.value}") {
                    contentType = MediaType.APPLICATION_JSON
                    content = updateKeywordBody(name = "Trump")
                }.andExpect {
                    status { isForbidden() }
                }.andReturn().response
            } finally {
                SecurityContextHolder.clearContext()
            }

            assertErrorResponse(
                actual = response.contentAsString,
                code = "KEYWORD_ACCESS_DENIED",
                message = "키워드에 접근할 수 없습니다: keywordId=${keywordId.value}, userId=${userId.value}"
            )
        }

        @Test
        @DisplayName("유효하지 않은 토큰 예외는 401 응답으로 변환한다")
        fun handleInvalidToken() {
            refreshTokenUseCase.exception = InvalidTokenException()

            val response = mockMvc.post("/api/v1/auth/token/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = refreshTokenBody()
            }.andExpect {
                status { isUnauthorized() }
            }.andReturn().response

            assertErrorResponse(
                actual = response.contentAsString,
                code = "INVALID_TOKEN",
                message = "유효하지 않은 토큰입니다"
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

    private fun refreshTokenBody(): String {
        return """
            {
              "refreshToken": "refresh-token"
            }
        """.trimIndent()
    }

    private fun authenticatedUserAuthentication(userId: UserId = UserId.newId()): UsernamePasswordAuthenticationToken {
        val role = UserRole.USER

        return UsernamePasswordAuthenticationToken(
            AuthenticatedUser(
                userId = userId,
                role = role
            ),
            null,
            listOf(SimpleGrantedAuthority("ROLE_${role.name}"))
        )
    }

}
