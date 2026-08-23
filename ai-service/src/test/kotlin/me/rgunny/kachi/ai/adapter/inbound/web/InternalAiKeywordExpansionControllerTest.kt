package me.rgunny.kachi.ai.adapter.inbound.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.rgunny.kachi.ai.adapter.inbound.keyword.AiKeywordExpansionExecutor
import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.ai.application.port.inbound.keyword.model.ExpandKeywordsCommand
import me.rgunny.kachi.ai.config.ApiVersionConfig
import me.rgunny.kachi.ai.fake.RecordingExpandKeywordsUseCase
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
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebFluxTest(controllers = [InternalAiKeywordExpansionController::class])
@Import(ApiVersionConfig::class, InternalAiKeywordExpansionControllerTest.TestBeans::class)
@DisplayName("InternalAiKeywordExpansionController")
class InternalAiKeywordExpansionControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var useCase: RecordingExpandKeywordsUseCase

    @Autowired
    private lateinit var executor: AiKeywordExpansionExecutor

    @BeforeEach
    fun resetUseCase() {
        // 컨트롤러 슬라이스 컨텍스트는 테스트끼리 공유되므로 호출 기록을 되돌린다.
        useCase.invokeCount = 0
        useCase.lastCommand = null
        useCase.hold = null
    }

    @Test
    @DisplayName("요청 body 없이 호출하면 기본값으로 확장을 실행한다")
    fun expandWithDefaults() {
        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_KEYWORD_EXPANSIONS)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())
        assertEquals(1, useCase.invokeCount)
        assertEquals(0, useCase.lastCommand?.keywords?.size)
    }

    @Test
    @DisplayName("요청 body의 키워드와 확장 개수를 command로 옮긴다")
    fun expandWithRequestedKeywords() {
        webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_KEYWORD_EXPANSIONS)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("keywords" to listOf("NVIDIA"), "maxExpansionsPerKeyword" to 4))
            .exchange()
            .expectStatus().isOk

        val command = requireNotNull(useCase.lastCommand)

        assertEquals(listOf("NVIDIA"), command.keywords.map { it.value })
        assertEquals(4, command.maxExpansionsPerKeyword)
    }

    @Test
    @DisplayName("생략한 필드는 요청 DTO의 기본값으로 채운다")
    fun applyRequestDefaultsForOmittedFields() {
        webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_KEYWORD_EXPANSIONS)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("keywords" to listOf("NVIDIA")))
            .exchange()
            .expectStatus().isOk

        val command = requireNotNull(useCase.lastCommand)

        assertEquals(listOf("NVIDIA"), command.keywords.map { it.value })
        assertEquals(
            ExpandKeywordsCommand.DEFAULT_MAX_EXPANSIONS_PER_KEYWORD,
            command.maxExpansionsPerKeyword
        )
    }

    @Test
    @DisplayName("키워드가 올바르지 않으면 400으로 응답한다")
    fun rejectInvalidKeyword() {
        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_KEYWORD_EXPANSIONS)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("keywords" to listOf(" ")))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(false, json.get("success").asBoolean())
        assertEquals(ErrorCode.INVALID_KEYWORD_EXPANSION_REQUEST.name, json.get("error").get("code").asString())
        assertEquals(0, useCase.invokeCount)
    }

    @Test
    @DisplayName("이미 실행 중이면 409로 응답한다")
    fun rejectWhenAlreadyRunning() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        useCase.hold = gate
        val running = async { executor.execute(ExpandKeywordsCommand(keywords = emptyList())) }
        while (useCase.invokeCount == 0) {
            yield()
        }

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_KEYWORD_EXPANSIONS)
            .exchange()
            .expectStatus().isEqualTo(ErrorCode.KEYWORD_EXPANSION_ALREADY_RUNNING.status)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(false, json.get("success").asBoolean())
        assertEquals(ErrorCode.KEYWORD_EXPANSION_ALREADY_RUNNING.name, json.get("error").get("code").asString())

        gate.complete(Unit)
        running.await()
        Unit
    }

    @TestConfiguration
    class TestBeans {

        @Bean
        fun recordingExpandKeywordsUseCase() = RecordingExpandKeywordsUseCase()

        @Bean
        fun keywordExpansionExecutor(useCase: RecordingExpandKeywordsUseCase) =
            AiKeywordExpansionExecutor(useCase, AiTestFixture.CLOCK)
    }
}
