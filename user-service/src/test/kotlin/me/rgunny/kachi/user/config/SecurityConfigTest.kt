package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.adapter.`in`.web.AuthController
import me.rgunny.kachi.user.adapter.`in`.web.KeywordController
import me.rgunny.kachi.user.adapter.`in`.web.UserController
import me.rgunny.kachi.user.adapter.`in`.web.fake.WebMvcFakeOAuth2Config
import me.rgunny.kachi.user.adapter.`in`.web.fake.WebMvcFakeUseCaseConfig
import me.rgunny.kachi.user.adapter.`in`.web.security.JwtTokenProvider
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Duration

@WebMvcTest(controllers = [AuthController::class, UserController::class, KeywordController::class])
@AutoConfigureMockMvc
@ImportAutoConfiguration(
    SecurityAutoConfiguration::class,
    ServletWebSecurityAutoConfiguration::class,
    SecurityFilterAutoConfiguration::class
)
@Import(
    ApiVersionConfig::class,
    SecurityConfig::class,
    SecurityConfigTest.JwtTestConfig::class,
    WebMvcFakeOAuth2Config::class,
    WebMvcFakeUseCaseConfig::class
)
@DisplayName("SecurityConfig")
class SecurityConfigTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val jwtTokenProvider: JwtTokenProvider
) {

    @Nested
    @DisplayName("permit all")
    inner class PermitAll {

        @Test
        @DisplayName("사용자 등록 API는 인증 없이 접근할 수 있다")
        fun permitRegisterUserApi() {
            mockMvc.post("/api/v1/users") {
                contentType = MediaType.APPLICATION_JSON
                content = registerUserBody()
            }.andExpect {
                status { isCreated() }
            }
        }

        @Test
        @DisplayName("토큰 갱신 API는 인증 없이 접근할 수 있다")
        fun permitRefreshTokenApi() {
            mockMvc.post("/api/v1/auth/token/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = refreshTokenBody()
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        @DisplayName("로그아웃 API는 인증 없이 접근할 수 있다")
        fun permitLogoutApi() {
            mockMvc.post("/api/v1/auth/logout") {
                contentType = MediaType.APPLICATION_JSON
                content = refreshTokenBody()
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        @DisplayName("OAuth2 인증 시작 endpoint는 인증 없이 provider로 리다이렉트한다")
        fun permitOAuth2AuthorizationApi() {
            mockMvc.get("/oauth2/authorization/google") {
                accept = MediaType.TEXT_HTML
            }.andExpect {
                status { is3xxRedirection() }
                header { exists("Location") }
                cookie { exists("oauth2_authorization_request") }
            }
        }

    }

    @Nested
    @DisplayName("authenticated")
    inner class Authenticated {

        @Test
        @DisplayName("내 정보 조회 API는 인증을 요구한다")
        fun requireAuthenticationForGetMeApi() {
            mockMvc.get("/api/v1/me") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isForbidden() }
            }
        }

        @Test
        @DisplayName("내 정보 조회 API는 access token으로 접근할 수 있다")
        fun permitGetMeApiWithAccessToken() {
            val token = jwtTokenProvider.createAccessToken(UserId.newId(), UserRole.USER)

            mockMvc.get("/api/v1/me") {
                header("Authorization", "Bearer ${token.value}")
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        @DisplayName("내 탈퇴 API는 인증을 요구한다")
        fun requireAuthenticationForDeactivateMeApi() {
            mockMvc.delete("/api/v1/me") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isForbidden() }
            }
        }

        @Test
        @DisplayName("내 탈퇴 API는 access token으로 접근할 수 있다")
        fun permitDeactivateMeApiWithAccessToken() {
            val token = jwtTokenProvider.createAccessToken(UserId.newId(), UserRole.USER)

            mockMvc.delete("/api/v1/me") {
                header("Authorization", "Bearer ${token.value}")
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        @DisplayName("인증 사용자 관심 키워드 등록 API는 인증을 요구한다")
        fun requireAuthenticationForRegisterMyKeywordApi() {
            mockMvc.post("/api/v1/me/keywords") {
                contentType = MediaType.APPLICATION_JSON
                content = registerKeywordBody()
            }.andExpect {
                status { isForbidden() }
            }
        }

        @Test
        @DisplayName("인증 사용자 관심 키워드 등록 API는 access token으로 접근할 수 있다")
        fun permitRegisterMyKeywordApiWithAccessToken() {
            val token = jwtTokenProvider.createAccessToken(UserId.newId(), UserRole.USER)

            mockMvc.post("/api/v1/me/keywords") {
                header("Authorization", "Bearer ${token.value}")
                contentType = MediaType.APPLICATION_JSON
                content = registerKeywordBody()
            }.andExpect {
                status { isCreated() }
            }
        }

        @Test
        @DisplayName("인증 사용자 관심 키워드 목록 조회 API는 인증을 요구한다")
        fun requireAuthenticationForListMyKeywordsApi() {
            mockMvc.get("/api/v1/me/keywords") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isForbidden() }
            }
        }

        @Test
        @DisplayName("인증 사용자 관심 키워드 목록 조회 API는 access token으로 접근할 수 있다")
        fun permitListMyKeywordsApiWithAccessToken() {
            val token = jwtTokenProvider.createAccessToken(UserId.newId(), UserRole.USER)

            mockMvc.get("/api/v1/me/keywords") {
                header("Authorization", "Bearer ${token.value}")
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        @DisplayName("관심 키워드 수정 API는 인증을 요구한다")
        fun requireAuthenticationForUpdateKeywordApi() {
            mockMvc.patch("/api/v1/keywords/${KeywordId.newId().value}") {
                contentType = MediaType.APPLICATION_JSON
                content = updateKeywordBody()
            }.andExpect {
                status { isForbidden() }
            }
        }

        @Test
        @DisplayName("관심 키워드 수정 API는 access token으로 접근할 수 있다")
        fun permitUpdateKeywordApiWithAccessToken() {
            val token = jwtTokenProvider.createAccessToken(UserId.newId(), UserRole.USER)

            mockMvc.patch("/api/v1/keywords/${KeywordId.newId().value}") {
                header("Authorization", "Bearer ${token.value}")
                contentType = MediaType.APPLICATION_JSON
                content = updateKeywordBody()
            }.andExpect {
                status { isOk() }
            }
        }

        @Test
        @DisplayName("허용하지 않은 API는 인증을 요구한다")
        fun requireAuthenticationForOtherApis() {
            mockMvc.get("/api/v1/internal") {
                accept = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isForbidden() }
            }
        }
    }

    private fun registerUserBody(): String {
        return """
            {
              "email": "rgunny@kachi.com",
              "nickname": "rgunny",
              "authProvider": "GOOGLE"
            }
        """.trimIndent()
    }

    private fun registerKeywordBody(): String {
        return """
            {
              "name": "Trump"
            }
        """.trimIndent()
    }

    private fun updateKeywordBody(): String {
        return """
            {
              "name": "Trump",
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

    @TestConfiguration(proxyBeanMethods = false)
    class JwtTestConfig {

        @Bean
        fun jwtTokenProvider(): JwtTokenProvider {
            // SecurityConfig slice test는 properties binding 대신 필터에 필요한 provider만 직접 제공한다.
            return JwtTokenProvider(
                secret = "morCcncONBqndWq56eP75u8LgYDg+HLWlfhqugyfDA4=",
                accessTokenTtl = Duration.ofMinutes(15),
                refreshTokenTtl = Duration.ofDays(14),
                clock = Clock.systemUTC()
            )
        }

    }
}
