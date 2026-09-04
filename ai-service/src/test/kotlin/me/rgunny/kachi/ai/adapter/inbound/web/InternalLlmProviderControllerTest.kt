package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmHold
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import me.rgunny.kachi.ai.config.ApiVersionConfig
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
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
        adminPort.resetModels.clear()
    }

    @Test
    @DisplayName("모델별 차단 상태를 모델 상수명과 제공자 code로 응답한다")
    fun findModelStatuses() {
        adminPort.statuses = listOf(
            status(MODEL, state = "OPEN").copy(hold = LlmHold(LlmFailureCode.LLM_MODEL_NOT_FOUND, AiTestFixture.NOW.plusSeconds(3600)))
        )

        val body = webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_LLM_PROVIDERS)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())

        val model = json.get("data").get(0)
        assertEquals(MODEL.name, model.get("model").asString())
        assertEquals(MODEL.provider.code, model.get("provider").asString())
        assertEquals("OPEN", model.get("circuitBreakerState").asString())
        assertEquals(AiTestFixture.NOW.toString(), model.get("cooldownUntil").asString())
        assertEquals("LLM_MODEL_NOT_FOUND", model.get("holdReason").asString())
        assertEquals(AiTestFixture.NOW.plusSeconds(3600).toString(), model.get("holdUntil").asString())
        assertEquals(2, model.get("failedCalls").asInt())
    }

    @Test
    @DisplayName("차단을 되돌리면 되돌린 뒤 상태를 응답한다")
    fun resetModel() {
        adminPort.statuses = listOf(status(MODEL, state = "CLOSED"))

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_LLM_PROVIDER_RESET, MODEL.name)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())
        assertEquals("CLOSED", json.get("data").get("circuitBreakerState").asString())
        assertEquals(listOf(MODEL), adminPort.resetModels)
    }

    @Test
    @DisplayName("모델 상수가 아닌 이름은 404로 응답한다")
    fun rejectUnknownModelName() {
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
        assertTrue(adminPort.resetModels.isEmpty())
    }

    @Test
    @DisplayName("후보에 없는 모델 상수는 404로 응답한다")
    fun rejectModelOutsideCandidates() {
        adminPort.statuses = listOf(status(MODEL, state = "CLOSED"))

        webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_LLM_PROVIDER_RESET, LlmModel.OLLAMA_QWEN3_27B.name)
            .exchange()
            .expectStatus().isEqualTo(ErrorCode.LLM_PROVIDER_NOT_FOUND.status)

        assertEquals(listOf(LlmModel.OLLAMA_QWEN3_27B), adminPort.resetModels)
    }

    private fun status(model: LlmModel, state: String): LlmModelStatus {
        return AiTestFixture.llmModelStatus(model = model, circuitBreakerState = state, cooldownUntil = AiTestFixture.NOW)
    }

    @TestConfiguration
    class TestBeans {

        @Bean
        fun fakeLlmProviderAdminPort() = FakeLlmProviderAdminPort()
    }

    private companion object {
        val MODEL: LlmModel = AiTestFixture.LLM_MODEL
    }
}
