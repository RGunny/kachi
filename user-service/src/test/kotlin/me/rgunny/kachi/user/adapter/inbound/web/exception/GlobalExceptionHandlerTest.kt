package me.rgunny.kachi.user.adapter.inbound.web.exception

import me.rgunny.kachi.user.adapter.inbound.web.AuthController
import me.rgunny.kachi.user.adapter.inbound.web.ChannelBindingController
import me.rgunny.kachi.user.adapter.inbound.web.InternalChannelBindingController
import me.rgunny.kachi.user.adapter.inbound.web.SubscriptionController
import me.rgunny.kachi.user.adapter.inbound.web.UserController
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeCompleteTelegramLinkUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeRegisterSubscriptionUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeRegisterWebhookBindingUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeResolveChannelBindingUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeRegisterUserUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeRefreshTokenUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeRevokeChannelBindingUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.FakeUpdateSubscriptionUseCase
import me.rgunny.kachi.user.adapter.inbound.web.fake.WebMvcFakeUseCaseConfig
import me.rgunny.kachi.user.adapter.inbound.web.security.AuthenticatedUser
import me.rgunny.kachi.user.application.exception.ChannelBindingNotActiveException
import me.rgunny.kachi.user.application.exception.ChannelBindingNotFoundException
import me.rgunny.kachi.user.application.exception.ChannelBindingRefNotFoundException
import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.exception.DuplicateSubscriptionException
import me.rgunny.kachi.user.application.exception.InvalidChannelAddressException
import me.rgunny.kachi.user.application.exception.InvalidTokenException
import me.rgunny.kachi.user.application.exception.LinkTokenInvalidException
import me.rgunny.kachi.user.application.exception.SubscriptionAccessDeniedException
import me.rgunny.kachi.user.application.exception.SubscriptionNotFoundException
import me.rgunny.kachi.user.config.ApiVersionConfig
import me.rgunny.kachi.user.domain.ChannelBindingId
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.SubscriptionChannel
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebMvcTest(
    controllers = [
        AuthController::class,
        UserController::class,
        SubscriptionController::class,
        ChannelBindingController::class,
        InternalChannelBindingController::class
    ]
)
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
    private val registerSubscriptionUseCase: FakeRegisterSubscriptionUseCase,
    private val updateSubscriptionUseCase: FakeUpdateSubscriptionUseCase,
    private val registerWebhookBindingUseCase: FakeRegisterWebhookBindingUseCase,
    private val revokeChannelBindingUseCase: FakeRevokeChannelBindingUseCase,
    private val completeTelegramLinkUseCase: FakeCompleteTelegramLinkUseCase,
    private val resolveChannelBindingUseCase: FakeResolveChannelBindingUseCase
) {

    @BeforeEach
    fun setUp() {
        registerUserUseCase.exception = null
        refreshTokenUseCase.exception = null
        registerSubscriptionUseCase.exception = null
        updateSubscriptionUseCase.exception = null
        registerWebhookBindingUseCase.exception = null
        revokeChannelBindingUseCase.exception = null
        completeTelegramLinkUseCase.exception = null
        resolveChannelBindingUseCase.exception = null
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
        @DisplayName("중복 구독 예외는 409 응답으로 변환한다")
        fun handleDuplicateSubscription() {
            val userId = UserId.newId()
            registerSubscriptionUseCase.exception = DuplicateSubscriptionException(userId, KeywordName.of("Trump"))

            SecurityContextHolder.getContext().authentication = authenticatedUserAuthentication(userId)

            val response = try {
                mockMvc.post("/api/v1/me/keywords") {
                    contentType = MediaType.APPLICATION_JSON
                    content = registerSubscriptionBody(name = "Trump")
                }.andExpect {
                    status { isConflict() }
                }.andReturn().response
            } finally {
                SecurityContextHolder.clearContext()
            }

            assertErrorResponse(
                actual = response.contentAsString,
                code = "DUPLICATE_SUBSCRIPTION",
                message = "이미 구독 중인 키워드입니다: Trump"
            )
        }

        @Test
        @DisplayName("구독 없음 예외는 404 응답으로 변환한다")
        fun handleSubscriptionNotFound() {
            val subscriptionId = SubscriptionId.newId()
            updateSubscriptionUseCase.exception = SubscriptionNotFoundException(subscriptionId)

            SecurityContextHolder.getContext().authentication = authenticatedUserAuthentication()

            val response = try {
                mockMvc.patch("/api/v1/me/keywords/${subscriptionId.value}") {
                    contentType = MediaType.APPLICATION_JSON
                    content = updateSubscriptionBody()
                }.andExpect {
                    status { isNotFound() }
                }.andReturn().response
            } finally {
                SecurityContextHolder.clearContext()
            }

            assertErrorResponse(
                actual = response.contentAsString,
                code = "SUBSCRIPTION_NOT_FOUND",
                message = "구독을 찾을 수 없습니다: ${subscriptionId.value}"
            )
        }

        @Test
        @DisplayName("구독 접근 거부 예외는 403 응답으로 변환한다")
        fun handleSubscriptionAccessDenied() {
            val subscriptionId = SubscriptionId.newId()
            val userId = UserId.newId()
            updateSubscriptionUseCase.exception = SubscriptionAccessDeniedException(subscriptionId, userId)

            SecurityContextHolder.getContext().authentication = authenticatedUserAuthentication()

            val response = try {
                mockMvc.patch("/api/v1/me/keywords/${subscriptionId.value}") {
                    contentType = MediaType.APPLICATION_JSON
                    content = updateSubscriptionBody()
                }.andExpect {
                    status { isForbidden() }
                }.andReturn().response
            } finally {
                SecurityContextHolder.clearContext()
            }

            assertErrorResponse(
                actual = response.contentAsString,
                code = "SUBSCRIPTION_ACCESS_DENIED",
                message = "구독에 접근할 수 없습니다: subscriptionId=${subscriptionId.value}, userId=${userId.value}"
            )
        }

        @Test
        @DisplayName("채널 바인딩 비활성 예외는 409 응답으로 변환한다")
        fun handleChannelBindingNotActive() {
            val userId = UserId.newId()
            registerSubscriptionUseCase.exception = ChannelBindingNotActiveException(userId, SubscriptionChannel.SLACK)

            SecurityContextHolder.getContext().authentication = authenticatedUserAuthentication(userId)

            val response = try {
                mockMvc.post("/api/v1/me/keywords") {
                    contentType = MediaType.APPLICATION_JSON
                    content = registerSubscriptionBody(name = "Trump")
                }.andExpect {
                    status { isConflict() }
                }.andReturn().response
            } finally {
                SecurityContextHolder.clearContext()
            }

            assertErrorResponse(
                actual = response.contentAsString,
                code = "CHANNEL_BINDING_NOT_ACTIVE",
                message = "연결된 채널이 아닙니다: channel=SLACK"
            )
        }

        @Test
        @DisplayName("채널 바인딩 없음 예외는 404 응답으로 변환한다")
        fun handleChannelBindingNotFound() {
            val userId = UserId.newId()
            revokeChannelBindingUseCase.exception = ChannelBindingNotFoundException(userId, SubscriptionChannel.DISCORD)

            SecurityContextHolder.getContext().authentication = authenticatedUserAuthentication(userId)

            val response = try {
                mockMvc.delete("/api/v1/me/channel-bindings/DISCORD").andExpect {
                    status { isNotFound() }
                }.andReturn().response
            } finally {
                SecurityContextHolder.clearContext()
            }

            assertErrorResponse(
                actual = response.contentAsString,
                code = "CHANNEL_BINDING_NOT_FOUND",
                message = "채널 바인딩을 찾을 수 없습니다: channel=DISCORD"
            )
        }

        @Test
        @DisplayName("채널 바인딩 참조 없음 예외는 404 응답으로 변환한다")
        fun handleChannelBindingRefNotFound() {
            val ref = ChannelBindingId.newId()
            resolveChannelBindingUseCase.exception = ChannelBindingRefNotFoundException(ref)

            val response = mockMvc.get("/api/v1/internal/channel-bindings/${ref.value}").andExpect {
                status { isNotFound() }
            }.andReturn().response

            assertErrorResponse(
                actual = response.contentAsString,
                code = "CHANNEL_BINDING_NOT_FOUND",
                message = "채널 바인딩을 찾을 수 없습니다: ref=${ref.value}"
            )
        }

        @Test
        @DisplayName("채널 주소 형식 예외는 400 응답으로 변환한다")
        fun handleInvalidChannelAddress() {
            val userId = UserId.newId()
            registerWebhookBindingUseCase.exception =
                InvalidChannelAddressException(SubscriptionChannel.SLACK, "Slack 주소가 아닙니다")

            SecurityContextHolder.getContext().authentication = authenticatedUserAuthentication(userId)

            val response = try {
                mockMvc.put("/api/v1/me/channel-bindings/SLACK") {
                    contentType = MediaType.APPLICATION_JSON
                    content = webhookBindingBody("https://example.com/x")
                }.andExpect {
                    status { isBadRequest() }
                }.andReturn().response
            } finally {
                SecurityContextHolder.clearContext()
            }

            assertErrorResponse(
                actual = response.contentAsString,
                code = "INVALID_CHANNEL_ADDRESS",
                message = "Slack 주소가 아닙니다"
            )
        }

        @Test
        @DisplayName("연결 토큰 예외는 400 응답으로 변환한다")
        fun handleLinkTokenInvalid() {
            completeTelegramLinkUseCase.exception = LinkTokenInvalidException()

            val response = mockMvc.post("/api/v1/internal/channel-bindings/telegram/link") {
                contentType = MediaType.APPLICATION_JSON
                content = telegramLinkBody()
            }.andExpect {
                status { isBadRequest() }
            }.andReturn().response

            assertErrorResponse(
                actual = response.contentAsString,
                code = "LINK_TOKEN_INVALID",
                message = "연결 토큰이 유효하지 않습니다"
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

    private fun registerSubscriptionBody(name: String): String {
        return """
            {
              "name": "$name",
              "channels": ["SLACK"]
            }
        """.trimIndent()
    }

    private fun updateSubscriptionBody(): String {
        return """
            {
              "enabled": true
            }
        """.trimIndent()
    }

    private fun webhookBindingBody(webhookUrl: String): String {
        return """
            {
              "webhookUrl": "$webhookUrl"
            }
        """.trimIndent()
    }

    private fun telegramLinkBody(): String {
        return """
            {
              "token": "token",
              "chatId": "123456789"
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
