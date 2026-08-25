package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmProviderStatus
import me.rgunny.kachi.ai.config.ApiVersionConfig
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.fake.FakeLlmProviderAdminPort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.JsonBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebFluxTest(controllers = [InternalLlmProviderController::class])
@Import(ApiVersionConfig::class, InternalLlmProviderControllerTest.TestBeans::class)
@DisplayName("InternalLlmProviderController")
class InternalLlmProviderControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var adminPort: FakeLlmProviderAdminPort

    @BeforeEach
    fun resetAdminPort() {
        // 컨트롤러 슬라이스 컨텍스트는 테스트끼리 공유되므로 호출 기록을 되돌린다.
        adminPort.statuses = emptyList()
        adminPort.resetNames.clear()
    }

    @Test
    @DisplayName("provider별 차단 상태를 응답한다")
    fun findProviderStatuses() {
        adminPort.statuses = listOf(status(PROVIDER, state = "OPEN"))

        val body = webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_LLM_PROVIDERS)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())

        val provider = json.get("data").get(0)
        assertEquals(PROVIDER, provider.get("provider").asString())
        assertEquals("OPEN", provider.get("circuitBreakerState").asString())
        assertEquals(AiTestFixture.NOW.toString(), provider.get("cooldownUntil").asString())
        assertEquals(2, provider.get("failedCalls").asInt())
    }

    @Test
    @DisplayName("차단을 되돌리면 되돌린 뒤 상태를 응답한다")
    fun resetProvider() {
        adminPort.statuses = listOf(status(PROVIDER, state = "CLOSED"))

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_LLM_PROVIDER_RESET, PROVIDER)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())
        assertEquals("CLOSED", json.get("data").get("circuitBreakerState").asString())
        assertEquals(listOf(LlmProviderName.of(PROVIDER)), adminPort.resetNames)
    }

    @Test
    @DisplayName("없는 provider 이름은 404로 응답한다")
    fun rejectUnknownProvider() {
        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_LLM_PROVIDER_RESET, "unknown")
            .exchange()
            .expectStatus().isEqualTo(ErrorCode.LLM_PROVIDER_NOT_FOUND.status)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(false, json.get("success").asBoolean())
        assertEquals(ErrorCode.LLM_PROVIDER_NOT_FOUND.name, json.get("error").get("code").asString())
    }

    private fun status(provider: String, state: String): LlmProviderStatus {
        return LlmProviderStatus(
            provider = LlmProviderName.of(provider),
            circuitBreakerState = state,
            cooldownUntil = AiTestFixture.NOW,
            failureRate = 50f,
            slowCallRate = -1f,
            bufferedCalls = 4,
            successfulCalls = 2,
            failedCalls = 2,
            notPermittedCalls = 3
        )
    }

    @TestConfiguration
    class TestBeans {

        @Bean
        fun fakeLlmProviderAdminPort() = FakeLlmProviderAdminPort()
    }

    private companion object {
        const val PROVIDER = "groq"
    }
}
