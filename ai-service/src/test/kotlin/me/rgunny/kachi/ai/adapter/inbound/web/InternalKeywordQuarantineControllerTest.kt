package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.ai.application.exception.KeywordQuarantineNotFoundException
import me.rgunny.kachi.ai.application.exception.KeywordQuarantineNotReleasableException
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.KeywordQuarantineSummary
import me.rgunny.kachi.ai.config.ApiVersionConfig
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.fake.RecordingFindKeywordQuarantinesUseCase
import me.rgunny.kachi.ai.fake.RecordingReleaseKeywordQuarantineUseCase
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@WebFluxTest(controllers = [InternalKeywordQuarantineController::class])
@Import(ApiVersionConfig::class, InternalKeywordQuarantineControllerTest.TestBeans::class)
@DisplayName("InternalKeywordQuarantineController")
class InternalKeywordQuarantineControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var findUseCase: RecordingFindKeywordQuarantinesUseCase

    @Autowired
    private lateinit var releaseUseCase: RecordingReleaseKeywordQuarantineUseCase

    @BeforeEach
    fun resetUseCases() {
        // 컨트롤러 슬라이스 컨텍스트는 테스트끼리 공유되므로 호출 기록을 되돌린다.
        findUseCase.invokeCount = 0
        findUseCase.lastQuery = null
        findUseCase.quarantines = emptyList()
        releaseUseCase.invokeCount = 0
        releaseUseCase.lastCommand = null
        releaseUseCase.failure = null
    }

    @Test
    @DisplayName("격리 기록 목록을 응답으로 옮기고 조회 조건을 그대로 전달한다")
    fun findQuarantines() {
        findUseCase.quarantines = listOf(summary())

        val body = webTestClient.get()
            .uri { builder ->
                builder.path(ApiPaths.V1_INTERNAL_AI_KEYWORD_QUARANTINES)
                    .queryParam("targetType", AiRunTargetType.NEWS_SUMMARY.name)
                    .queryParam("status", KeywordQuarantineStatus.QUARANTINED.name)
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())

        val quarantine = json.get("data").get(0)
        assertEquals("NVIDIA", quarantine.get("keyword").asString())
        assertEquals(AiRunTargetType.NEWS_SUMMARY.name, quarantine.get("targetType").asString())
        assertEquals(KeywordQuarantineStatus.QUARANTINED.name, quarantine.get("status").asString())
        assertEquals(3, quarantine.get("consecutiveFailures").asInt())

        val query = requireNotNull(findUseCase.lastQuery)
        assertEquals(AiRunTargetType.NEWS_SUMMARY, query.targetType)
        assertEquals(KeywordQuarantineStatus.QUARANTINED, query.status)
    }

    @Test
    @DisplayName("조회 조건을 생략하면 전체 조회로 전달한다")
    fun findQuarantinesWithoutFilters() {
        webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_AI_KEYWORD_QUARANTINES)
            .exchange()
            .expectStatus().isOk

        val query = requireNotNull(findUseCase.lastQuery)
        assertNull(query.targetType)
        assertNull(query.status)
    }

    @Test
    @DisplayName("상태 값이 올바르지 않으면 400으로 응답한다")
    fun rejectInvalidStatus() {
        val body = webTestClient.get()
            .uri { builder ->
                builder.path(ApiPaths.V1_INTERNAL_AI_KEYWORD_QUARANTINES)
                    .queryParam("status", "UNKNOWN_STATUS")
                    .build()
            }
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(false, json.get("success").asBoolean())
        assertEquals(ErrorCode.INVALID_INTERNAL_REQUEST.name, json.get("error").get("code").asString())
        assertEquals(0, findUseCase.invokeCount)
    }

    @Test
    @DisplayName("격리를 해제하면 해제된 기록을 응답한다")
    fun releaseQuarantine() {
        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_KEYWORD_QUARANTINE_RELEASE, KEYWORD)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())
        assertEquals(KeywordQuarantineStatus.RELEASED.name, json.get("data").get("status").asString())

        val command = requireNotNull(releaseUseCase.lastCommand)
        assertEquals(KEYWORD, command.keyword.value)
        // 대상 종류를 생략한 요청은 격리를 만드는 유일한 실행인 뉴스 요약으로 간다.
        assertEquals(AiRunTargetType.NEWS_SUMMARY, command.targetType)
    }

    @Test
    @DisplayName("해제 대상 기록이 없으면 404로 응답한다")
    fun releaseMissingQuarantine() {
        releaseUseCase.failure = KeywordQuarantineNotFoundException(
            targetType = AiRunTargetType.NEWS_SUMMARY,
            keyword = AiTestFixture.keyword()
        )

        assertFailureResponse(ErrorCode.KEYWORD_QUARANTINE_NOT_FOUND)
    }

    @Test
    @DisplayName("격리 상태가 아니면 409로 응답한다")
    fun releaseNotQuarantinedKeyword() {
        releaseUseCase.failure = KeywordQuarantineNotReleasableException(
            keyword = AiTestFixture.keyword(),
            status = KeywordQuarantineStatus.RELEASED
        )

        assertFailureResponse(ErrorCode.KEYWORD_QUARANTINE_NOT_RELEASABLE)
    }

    private fun assertFailureResponse(errorCode: ErrorCode) {
        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_KEYWORD_QUARANTINE_RELEASE, KEYWORD)
            .exchange()
            .expectStatus().isEqualTo(errorCode.status)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(false, json.get("success").asBoolean())
        assertEquals(errorCode.name, json.get("error").get("code").asString())
    }

    private fun summary(): KeywordQuarantineSummary {
        return KeywordQuarantineSummary.from(
            AiTestFixture.quarantine(
                consecutiveFailures = AiTestFixture.DEFAULT_QUARANTINE_FAILURE_THRESHOLD
            )
        )
    }

    @TestConfiguration
    class TestBeans {

        @Bean
        fun recordingFindKeywordQuarantinesUseCase() = RecordingFindKeywordQuarantinesUseCase()

        @Bean
        fun recordingReleaseKeywordQuarantineUseCase() = RecordingReleaseKeywordQuarantineUseCase()
    }

    private companion object {
        const val KEYWORD = "NVIDIA"
    }
}
