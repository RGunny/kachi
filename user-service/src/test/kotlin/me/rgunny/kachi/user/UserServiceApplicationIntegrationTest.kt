package me.rgunny.kachi.user

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertContains

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(UserServiceApplicationTestContainersConfig::class)
@DisplayName("UserServiceApplication 통합 테스트")
class UserServiceApplicationIntegrationTest @Autowired constructor(
    private val mockMvc: MockMvc
) {

    @Test
    @DisplayName("MySQL과 Redis 테스트 컨테이너로 애플리케이션 컨텍스트를 로드한다")
    fun loadApplicationContext() {
        // SpringBootTest context load 자체가 검증 대상이다.
    }

    @Test
    @DisplayName("헬스체크 endpoint는 인증 없이 서비스 상태를 반환한다")
    fun getHealthWithoutAuthentication() {
        val result = mockMvc.get("/actuator/health") {
            accept = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
        }.andReturn()

        assertContains(result.response.contentAsString, "\"status\":\"UP\"")
    }
}
