package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.adapter.`in`.web.KeywordController
import me.rgunny.kachi.user.adapter.`in`.web.UserController
import me.rgunny.kachi.user.adapter.`in`.web.fake.WebMvcFakeUseCaseConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(controllers = [UserController::class, KeywordController::class])
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

    }
}
