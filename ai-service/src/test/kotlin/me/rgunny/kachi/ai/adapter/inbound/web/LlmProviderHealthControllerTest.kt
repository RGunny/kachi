package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.config.ApiVersionConfig
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.fake.FakeLlmProviderPort
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

@WebFluxTest(controllers = [LlmProviderHealthController::class])
@Import(ApiVersionConfig::class, LlmProviderHealthControllerTest.TestBeans::class)
@DisplayName("LlmProviderHealthController")
class LlmProviderHealthControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var llmProvider: FakeLlmProviderPort

    @BeforeEach
    fun resetProvider() {
        llmProvider.expandCallCount = 0
        llmProvider.failureByKeyword = emptyMap()
    }

    @Test
    @DisplayName("provider 호출에 성공하면 provider와 model 정보를 반환한다")
    fun returnProviderMetadata() {
        val body = webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_LLM_PROVIDER_HEALTH)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        val data = json.get("data")

        assertEquals(true, json.get("success").asBoolean())
        assertEquals(AiTestFixture.PROVIDER.code, data.get("provider").asString())
        assertEquals(AiTestFixture.MODEL, data.get("model").asString())
        assertEquals(1, llmProvider.expandCallCount)
    }

    @Test
    @DisplayName("keyword 파라미터를 그대로 provider 호출에 사용한다")
    fun useRequestedKeyword() {
        webTestClient.get()
            .uri { it.path(ApiPaths.V1_INTERNAL_LLM_PROVIDER_HEALTH).queryParam("keyword", "TESLA").build() }
            .exchange()
            .expectStatus().isOk

        assertEquals(1, llmProvider.expandCallCount)
    }

    @Test
    @DisplayName("provider 호출이 실패하면 502로 응답한다")
    fun returnBadGatewayWhenProviderFails() {
        llmProvider.failureByKeyword = mapOf(
            AiKeyword.of("NVIDIA") to AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)
        )

        val body = webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_LLM_PROVIDER_HEALTH)
            .exchange()
            .expectStatus().isEqualTo(ErrorCode.LLM_PROVIDER_HEALTH_CHECK_FAILED.status)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)

        assertEquals(false, json.get("success").asBoolean())
        assertEquals(
            ErrorCode.LLM_PROVIDER_HEALTH_CHECK_FAILED.name,
            json.get("error").get("code").asString()
        )
    }

    @Test
    @DisplayName("키워드가 올바르지 않아도 502로 응답한다")
    fun returnBadGatewayWhenKeywordIsInvalid() {
        webTestClient.get()
            .uri { it.path(ApiPaths.V1_INTERNAL_LLM_PROVIDER_HEALTH).queryParam("keyword", " ").build() }
            .exchange()
            .expectStatus().isEqualTo(ErrorCode.LLM_PROVIDER_HEALTH_CHECK_FAILED.status)

        assertEquals(0, llmProvider.expandCallCount)
    }

    @TestConfiguration
    class TestBeans {

        @Bean
        fun llmProviderPort(): LlmProviderPort = FakeLlmProviderPort()
    }
}
