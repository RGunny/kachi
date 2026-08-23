package me.rgunny.kachi.ai.adapter.inbound.web

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.rgunny.kachi.ai.adapter.inbound.news.AiNewsSummaryExecutor
import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.inbound.news.model.SummaryWindowRequest
import me.rgunny.kachi.ai.config.ApiVersionConfig
import me.rgunny.kachi.ai.fake.RecordingSummarizeNewsUseCase
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

@WebFluxTest(controllers = [InternalAiNewsSummaryController::class])
@Import(ApiVersionConfig::class, InternalAiNewsSummaryControllerTest.TestBeans::class)
@DisplayName("InternalAiNewsSummaryController")
class InternalAiNewsSummaryControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var useCase: RecordingSummarizeNewsUseCase

    @Autowired
    private lateinit var executor: AiNewsSummaryExecutor

    @BeforeEach
    fun resetUseCase() {
        // 컨트롤러 슬라이스 컨텍스트는 테스트끼리 공유되므로 호출 기록을 되돌린다.
        useCase.invokeCount = 0
        useCase.lastCommand = null
        useCase.hold = null
    }

    @Test
    @DisplayName("요청 body 없이 호출하면 기본값으로 요약을 실행한다")
    fun summarizeWithDefaults() {
        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_NEWS_SUMMARIES)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())
        assertTrue(json.get("data").has("id"))
        assertEquals(1, useCase.invokeCount)
        assertEquals(0, useCase.lastCommand?.keywords?.size)
    }

    @Test
    @DisplayName("요청 body의 키워드와 구간을 command로 옮긴다")
    fun summarizeWithRequestedWindow() {
        val from = AiTestFixture.NOW.minusSeconds(3600)
        val to = AiTestFixture.NOW

        webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_NEWS_SUMMARIES)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                mapOf(
                    "keywords" to listOf("NVIDIA", "TESLA"),
                    "from" to from.toString(),
                    "to" to to.toString(),
                    "maxArticlesPerKeyword" to 5
                )
            )
            .exchange()
            .expectStatus().isOk

        val command = requireNotNull(useCase.lastCommand)
        val window = command.window as SummaryWindowRequest.Explicit

        assertEquals(listOf("NVIDIA", "TESLA"), command.keywords.map { it.value })
        assertEquals(5, command.maxArticlesPerKeyword)
        assertEquals(from, window.from)
        assertEquals(to, window.to)
    }

    @Test
    @DisplayName("생략한 필드는 요청 DTO의 기본값으로 채운다")
    fun applyRequestDefaultsForOmittedFields() {
        webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_NEWS_SUMMARIES)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("keywords" to listOf("NVIDIA")))
            .exchange()
            .expectStatus().isOk

        val command = requireNotNull(useCase.lastCommand)

        assertEquals(listOf("NVIDIA"), command.keywords.map { it.value })
        assertEquals(
            SummarizeNewsCommand.DEFAULT_MAX_ARTICLES_PER_KEYWORD,
            command.maxArticlesPerKeyword
        )
    }

    @Test
    @DisplayName("키워드가 올바르지 않으면 400으로 응답한다")
    fun rejectInvalidKeyword() {
        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_NEWS_SUMMARIES)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("keywords" to listOf(" ")))
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(false, json.get("success").asBoolean())
        assertEquals(ErrorCode.INVALID_NEWS_SUMMARY_REQUEST.name, json.get("error").get("code").asString())
        assertEquals(0, useCase.invokeCount)
    }

    @Test
    @DisplayName("버전 없는 경로는 매핑되지 않는다")
    fun rejectPathWithoutVersion() {
        webTestClient.post()
            .uri(ApiPaths.INTERNAL_AI_NEWS_SUMMARIES)
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    @DisplayName("이미 실행 중이면 409로 응답한다")
    fun rejectWhenAlreadyRunning() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        useCase.hold = gate
        val running = async {
            executor.execute(
                SummarizeNewsCommand(
                    keywords = emptyList(),
                    window = SummaryWindowRequest.Explicit(from = null, to = null)
                )
            )
        }
        while (useCase.invokeCount == 0) {
            yield()
        }

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_NEWS_SUMMARIES)
            .exchange()
            .expectStatus().isEqualTo(ErrorCode.NEWS_SUMMARY_ALREADY_RUNNING.status)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(false, json.get("success").asBoolean())
        assertEquals(ErrorCode.NEWS_SUMMARY_ALREADY_RUNNING.name, json.get("error").get("code").asString())

        gate.complete(Unit)
        running.await()
        Unit
    }

    @TestConfiguration
    class TestBeans {

        @Bean
        fun recordingSummarizeNewsUseCase() = RecordingSummarizeNewsUseCase()

        @Bean
        fun newsSummaryExecutor(useCase: RecordingSummarizeNewsUseCase) =
            AiNewsSummaryExecutor(useCase, AiTestFixture.CLOCK)
    }
}
