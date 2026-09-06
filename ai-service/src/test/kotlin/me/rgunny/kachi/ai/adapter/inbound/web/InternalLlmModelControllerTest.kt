package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmHold
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import me.rgunny.kachi.ai.config.ApiVersionConfig
import me.rgunny.kachi.ai.domain.llm.LlmBilling
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.fake.FakeLlmProviderAdminPort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.JsonBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient
import tools.jackson.databind.JsonNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebFluxTest(controllers = [InternalLlmModelController::class])
@Import(ApiVersionConfig::class, InternalLlmModelControllerTest.TestBeans::class)
@DisplayName("InternalLlmModelController")
class InternalLlmModelControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var adminPort: FakeLlmProviderAdminPort

    @BeforeEach
    fun resetAdminPort() {
        // 컨트롤러 슬라이스 컨텍스트는 테스트끼리 공유되므로 호출 기록을 되돌린다.
        adminPort.statuses = emptyList()
        adminPort.resetModels.clear()
        adminPort.probedModels.clear()
        adminPort.probeResult = AiTestFixture.llmProbeResult()
        adminPort.probeFailure = null
    }

    @Test
    @DisplayName("모델별 차단 상태를 모델 상수명·제공자 code·과금 방식과 함께 응답한다")
    fun findModelStatuses() {
        adminPort.statuses = listOf(
            status(MODEL, state = "OPEN").copy(
                billing = LlmBilling.METERED,
                hold = LlmHold(LlmFailureCode.LLM_MODEL_NOT_FOUND, AiTestFixture.NOW.plusSeconds(3600))
            )
        )

        val json = exchange(webTestClient.get().uri(ApiPaths.V1_INTERNAL_LLM_MODELS), expectedStatus = 200)

        assertTrue(json.get("success").asBoolean())

        val model = json.get("data").get(0)
        assertEquals(MODEL.name, model.get("model").asString())
        assertEquals(MODEL.provider.code, model.get("provider").asString())
        assertEquals("METERED", model.get("billing").asString())
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

        val json = exchange(webTestClient.post().uri(ApiPaths.V1_INTERNAL_LLM_MODEL_RESET, MODEL.name), expectedStatus = 200)

        assertTrue(json.get("success").asBoolean())
        assertEquals("CLOSED", json.get("data").get("circuitBreakerState").asString())
        assertEquals(listOf(MODEL), adminPort.resetModels)
    }

    @Test
    @DisplayName("후보에 없는 모델 상수의 되돌리기는 404로 응답한다")
    fun rejectResetOutsideCandidates() {
        adminPort.statuses = listOf(status(MODEL, state = "CLOSED"))

        val json = exchange(
            webTestClient.post().uri(ApiPaths.V1_INTERNAL_LLM_MODEL_RESET, OTHER_MODEL.name),
            expectedStatus = ErrorCode.LLM_MODEL_NOT_CANDIDATE.status.value()
        )

        assertEquals(false, json.get("success").asBoolean())
        assertEquals(ErrorCode.LLM_MODEL_NOT_CANDIDATE.name, json.get("error").get("code").asString())
        assertEquals(listOf(OTHER_MODEL), adminPort.resetModels)
    }

    @Test
    @DisplayName("모델 하나에 실제 요청을 보낸 결과를 요청 모델·응답 모델·지연·토큰·과금 방식과 함께 응답한다")
    fun probeModel() {
        adminPort.statuses = listOf(status(MODEL, state = "CLOSED"))
        adminPort.probeResult = AiTestFixture.llmProbeResult(billing = LlmBilling.METERED)

        val json = exchange(webTestClient.post().uri(ApiPaths.V1_INTERNAL_LLM_MODEL_PROBE, MODEL.name), expectedStatus = 200)

        assertTrue(json.get("success").asBoolean())
        val data = json.get("data")
        assertEquals(MODEL.name, data.get("model").asString())
        assertEquals(MODEL.provider.code, data.get("provider").asString())
        assertEquals("METERED", data.get("billing").asString())
        assertEquals(AiTestFixture.REQUESTED_MODEL, data.get("requestedModel").asString())
        assertEquals(AiTestFixture.MODEL, data.get("servedModel").asString())
        assertEquals(AiTestFixture.KEYWORD_EXPANSION_PROMPT_VERSION.value, data.get("promptVersion").asString())
        assertEquals(1234, data.get("latencyMillis").asLong())
        assertEquals(AiTestFixture.TOKEN_USAGE.inputTokens, data.get("inputTokens").asInt())
        assertEquals(AiTestFixture.TOKEN_USAGE.outputTokens, data.get("outputTokens").asInt())
        val expandedKeywords = data.get("expandedKeywords")
        assertEquals(2, expandedKeywords.size())
        assertEquals("AI 반도체", expandedKeywords.get(0).asString())
        assertEquals("GPU", expandedKeywords.get(1).asString())
        assertEquals(listOf(MODEL), adminPort.probedModels)
    }

    @Test
    @DisplayName("후보에 없는 모델 상수의 probe는 404로 응답한다")
    fun rejectProbeOutsideCandidates() {
        adminPort.statuses = listOf(status(MODEL, state = "CLOSED"))

        val json = exchange(
            webTestClient.post().uri(ApiPaths.V1_INTERNAL_LLM_MODEL_PROBE, OTHER_MODEL.name),
            expectedStatus = ErrorCode.LLM_MODEL_NOT_CANDIDATE.status.value()
        )

        assertEquals(ErrorCode.LLM_MODEL_NOT_CANDIDATE.name, json.get("error").get("code").asString())
    }

    @Test
    @DisplayName("probe 호출이 실패하면 실패 코드와 사유를 실어 502로 응답한다")
    fun returnBadGatewayWhenProbeFails() {
        adminPort.statuses = listOf(status(MODEL, state = "OPEN"))
        adminPort.probeFailure = AiTestFixture.llmProviderException(LlmFailureCode.LLM_NOT_PERMITTED)

        val json = exchange(
            webTestClient.post().uri(ApiPaths.V1_INTERNAL_LLM_MODEL_PROBE, MODEL.name),
            expectedStatus = ErrorCode.LLM_CALL_FAILED.status.value()
        )

        assertEquals(false, json.get("success").asBoolean())
        assertEquals(ErrorCode.LLM_CALL_FAILED.name, json.get("error").get("code").asString())
        assertTrue(json.get("error").get("message").asString().startsWith(LlmFailureCode.LLM_NOT_PERMITTED.code))
    }

    @ParameterizedTest
    @ValueSource(strings = [ApiPaths.V1_INTERNAL_LLM_MODEL_RESET, ApiPaths.V1_INTERNAL_LLM_MODEL_PROBE])
    @DisplayName("모델 상수가 아닌 이름은 포트에 닿기 전에 400으로 응답한다")
    fun rejectUnknownModelName(path: String) {
        adminPort.statuses = listOf(status(MODEL, state = "CLOSED"))

        val json = exchange(
            webTestClient.post().uri(path, "unknown"),
            expectedStatus = ErrorCode.INVALID_INTERNAL_REQUEST.status.value()
        )

        assertEquals(false, json.get("success").asBoolean())
        assertEquals(ErrorCode.INVALID_INTERNAL_REQUEST.name, json.get("error").get("code").asString())
        assertTrue(adminPort.resetModels.isEmpty())
        assertTrue(adminPort.probedModels.isEmpty())
    }

    private fun exchange(
        request: WebTestClient.RequestHeadersSpec<*>,
        expectedStatus: Int
    ): JsonNode {
        val body = request.exchange()
            .expectStatus().isEqualTo(expectedStatus)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        return JsonBody.parse(body)
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
        val OTHER_MODEL: LlmModel = LlmModel.OLLAMA_QWEN3_27B
    }
}
