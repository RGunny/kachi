package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.inbound.watermark.model.SummaryWatermarkLag
import me.rgunny.kachi.ai.config.ApiVersionConfig
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.fake.RecordingFindSummaryWatermarksUseCase
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
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebFluxTest(controllers = [InternalSummaryWatermarkController::class])
@Import(ApiVersionConfig::class, InternalSummaryWatermarkControllerTest.TestBeans::class)
@DisplayName("InternalSummaryWatermarkController")
class InternalSummaryWatermarkControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var useCase: RecordingFindSummaryWatermarksUseCase

    @BeforeEach
    fun resetUseCase() {
        // 컨트롤러 슬라이스 컨텍스트는 테스트끼리 공유되므로 호출 기록을 되돌린다.
        useCase.invokeCount = 0
        useCase.watermarks = emptyList()
    }

    @Test
    @DisplayName("진행 지점 목록을 벌어진 폭과 함께 응답한다")
    fun findWatermarks() {
        val position = AiTestFixture.NOW.minus(LAG)
        useCase.watermarks = listOf(
            SummaryWatermarkLag(
                targetType = AiRunTargetType.NEWS_SUMMARY,
                position = position,
                lagSeconds = LAG.seconds,
                updatedAt = AiTestFixture.NOW
            )
        )

        val body = webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_AI_SUMMARY_WATERMARKS)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())

        val watermark = json.get("data").get(0)
        assertEquals(AiRunTargetType.NEWS_SUMMARY.name, watermark.get("targetType").asString())
        assertEquals(position.toString(), watermark.get("position").asString())
        assertEquals(LAG.seconds, watermark.get("lagSeconds").asLong())
        assertEquals(1, useCase.invokeCount)
    }

    @TestConfiguration
    class TestBeans {

        @Bean
        fun recordingFindSummaryWatermarksUseCase() = RecordingFindSummaryWatermarksUseCase()
    }

    private companion object {
        val LAG: Duration = Duration.ofMinutes(30)
    }
}
