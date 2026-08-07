package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.adapter.inbound.web.AuthController
import me.rgunny.kachi.user.adapter.inbound.web.KeywordController
import me.rgunny.kachi.user.adapter.inbound.web.UserController
import me.rgunny.kachi.user.adapter.inbound.web.fake.WebMvcFakeUseCaseConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(
    controllers = [AuthController::class, UserController::class, KeywordController::class],
    excludeAutoConfiguration = [
        OAuth2ClientAutoConfiguration::class,
        OAuth2ClientWebSecurityAutoConfiguration::class
    ]
)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiVersionConfig::class, WebMvcFakeUseCaseConfig::class)
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
        @DisplayName("v1 토큰 갱신 API를 매핑한다")
        fun mapV1RefreshTokenApi() {
            mockMvc.post("/api/v1/auth/token/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "refreshToken": "refresh-token"
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        @DisplayName("v1 로그아웃 API를 매핑한다")
        fun mapV1LogoutApi() {
            mockMvc.post("/api/v1/auth/logout") {
                contentType = MediaType.APPLICATION_JSON
                content = """
                    {
                      "refreshToken": "refresh-token"
                    }
                """.trimIndent()
            }.andExpect {
                status { isOk() }
            }
        }

    }
}
