package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.adapter.`in`.web.KeywordController
import me.rgunny.kachi.user.adapter.`in`.web.UserController
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordResult
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordUseCase
import me.rgunny.kachi.user.application.port.`in`.RegisterUserCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterUserResult
import me.rgunny.kachi.user.application.port.`in`.RegisterUserUseCase
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordResult
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordUseCase
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant

@WebMvcTest(controllers = [UserController::class, KeywordController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiVersionConfig::class, ApiVersionConfigTest.TestUseCaseConfig::class)
@DisplayName("ApiVersionConfig")
class ApiVersionConfigTest @Autowired constructor(
    private val mockMvc: MockMvc
) {

    @Nested
    @DisplayName("path segment version")
    inner class PathSegmentVersion {

        @Test
        @DisplayName("v1 사용자 등록 API를 매핑한다")
        fun mapV1RegisterUserApi() {
            mockMvc.post("/api/v1/users") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "email": "rgunny@kachi.com",
                      "nickname": "rgunny",
                      "authProvider": "GOOGLE"
                    }
                """.trimIndent()
            }.andExpect {
                status { isCreated() }
            }
        }

        @Test
        @DisplayName("v1 관심 키워드 등록 API를 매핑한다")
        fun mapV1RegisterKeywordApi() {
            mockMvc.post("/api/v1/users/${TestUseCaseConfig.userId.value}/keywords") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "name": "Trump"
                    }
                """.trimIndent()
            }.andExpect {
                status { isCreated() }
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestUseCaseConfig {

        @Bean
        fun registerUserUseCase(): RegisterUserUseCase = object : RegisterUserUseCase {
            override fun register(command: RegisterUserCommand): RegisterUserResult {
                return RegisterUserResult(
                    id = userId,
                    email = command.email,
                    nickname = command.nickname,
                    status = UserStatus.ACTIVE,
                    role = UserRole.USER,
                    authProvider = command.authProvider,
                    registeredAt = registeredAt
                )
            }
        }

        @Bean
        fun registerKeywordUseCase(): RegisterKeywordUseCase = object : RegisterKeywordUseCase {
            override fun register(command: RegisterKeywordCommand): RegisterKeywordResult {
                return RegisterKeywordResult(
                    id = keywordId,
                    userId = command.userId,
                    name = command.name,
                    enabled = true,
                    registeredAt = registeredAt
                )
            }
        }

        @Bean
        fun updateKeywordUseCase(): UpdateKeywordUseCase = object : UpdateKeywordUseCase {
            override fun update(command: UpdateKeywordCommand): UpdateKeywordResult {
                return UpdateKeywordResult(
                    id = command.keywordId,
                    userId = userId,
                    name = command.name ?: "Trump",
                    enabled = command.enabled ?: true,
                    registeredAt = registeredAt,
                    disabledAt = null
                )
            }
        }

        companion object {
            val userId: UserId = UserId.newId()
            private val keywordId: KeywordId = KeywordId.newId()
            private val registeredAt: Instant = Instant.parse("2026-05-20T00:00:00Z")
        }
    }
}
