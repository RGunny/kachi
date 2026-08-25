package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.ai.application.exception.AiOutboxNotFoundException
import me.rgunny.kachi.ai.application.exception.AiOutboxNotRecoverableException
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.AiOutboxSummary
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.FindAiOutboxesQuery
import me.rgunny.kachi.ai.config.ApiVersionConfig
import me.rgunny.kachi.ai.domain.outbox.AiOutboxId
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus
import me.rgunny.kachi.ai.fake.RecordingFindAiOutboxesUseCase
import me.rgunny.kachi.ai.fake.RecordingRecoverAiOutboxUseCase
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
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@WebFluxTest(controllers = [InternalAiOutboxController::class])
@Import(ApiVersionConfig::class, InternalAiOutboxControllerTest.TestBeans::class)
@DisplayName("InternalAiOutboxController")
class InternalAiOutboxControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var findUseCase: RecordingFindAiOutboxesUseCase

    @Autowired
    private lateinit var recoverUseCase: RecordingRecoverAiOutboxUseCase

    @BeforeEach
    fun resetUseCases() {
        // 컨트롤러 슬라이스 컨텍스트는 테스트끼리 공유되므로 호출 기록을 되돌린다.
        findUseCase.invokeCount = 0
        findUseCase.lastQuery = null
        findUseCase.outboxes = emptyList()
        recoverUseCase.invokeCount = 0
        recoverUseCase.lastCommand = null
        recoverUseCase.failure = null
    }

    @Test
    @DisplayName("outbox 목록을 응답으로 옮기고 payload는 담지 않는다")
    fun findOutboxes() {
        findUseCase.outboxes = listOf(summary())

        val body = webTestClient.get()
            .uri { builder ->
                builder.path(ApiPaths.V1_INTERNAL_AI_OUTBOXES)
                    .queryParam("status", AiOutboxStatus.PENDING.name)
                    .queryParam("limit", 10)
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())

        val outbox = json.get("data").get(0)
        assertEquals(AiTestFixture.OUTBOX_EVENT_KEY, outbox.get("eventKey").asString())
        assertEquals(AiOutboxStatus.DEAD.name, outbox.get("status").asString())
        assertFalse(outbox.has("payload"), "목록 응답에 payload가 없어야 합니다")

        val query = requireNotNull(findUseCase.lastQuery)
        assertEquals(AiOutboxStatus.PENDING, query.status)
        assertEquals(10, query.limit)
    }

    @Test
    @DisplayName("조회 조건을 생략하면 DEAD를 기본 개수만큼 조회한다")
    fun findOutboxesWithDefaults() {
        webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_AI_OUTBOXES)
            .exchange()
            .expectStatus().isOk

        val query = requireNotNull(findUseCase.lastQuery)
        assertEquals(AiOutboxStatus.DEAD, query.status)
        assertEquals(FindAiOutboxesQuery.DEFAULT_LIMIT, query.limit)
    }

    @Test
    @DisplayName("DEAD 행을 복구하면 복구된 행과 복구 시각을 응답한다")
    fun recoverOutbox() {
        val outboxId = UUID.randomUUID()

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_OUTBOX_RECOVER, outboxId)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())
        assertEquals(AiOutboxStatus.PENDING.name, json.get("data").get("outbox").get("status").asString())
        assertEquals(AiTestFixture.NOW.toString(), json.get("data").get("recoveredAt").asString())
        assertEquals(outboxId, requireNotNull(recoverUseCase.lastCommand).outboxId.value)
    }

    @Test
    @DisplayName("복구 대상 행이 없으면 404로 응답한다")
    fun recoverMissingOutbox() {
        recoverUseCase.failure = AiOutboxNotFoundException(AiOutboxId.newId())

        assertFailureResponse(ErrorCode.AI_OUTBOX_NOT_FOUND)
    }

    @Test
    @DisplayName("DEAD 상태가 아니면 409로 응답한다")
    fun recoverNotDeadOutbox() {
        recoverUseCase.failure = AiOutboxNotRecoverableException(AiOutboxId.newId(), AiOutboxStatus.PENDING)

        assertFailureResponse(ErrorCode.AI_OUTBOX_NOT_RECOVERABLE)
    }

    @Test
    @DisplayName("id가 UUID가 아니면 400으로 응답한다")
    fun rejectInvalidOutboxId() {
        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_OUTBOX_RECOVER, "not-a-uuid")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(false, json.get("success").asBoolean())
        assertEquals(ErrorCode.INVALID_INTERNAL_REQUEST.name, json.get("error").get("code").asString())
        assertEquals(0, recoverUseCase.invokeCount)
    }

    private fun assertFailureResponse(errorCode: ErrorCode) {
        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_AI_OUTBOX_RECOVER, UUID.randomUUID())
            .exchange()
            .expectStatus().isEqualTo(errorCode.status)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(false, json.get("success").asBoolean())
        assertEquals(errorCode.name, json.get("error").get("code").asString())
    }

    private fun summary(): AiOutboxSummary {
        return AiOutboxSummary.from(
            AiTestFixture.restoredOutbox(
                status = AiOutboxStatus.DEAD,
                retryCount = 5,
                lastError = "broker down"
            )
        )
    }

    @TestConfiguration
    class TestBeans {

        @Bean
        fun recordingFindAiOutboxesUseCase() = RecordingFindAiOutboxesUseCase()

        @Bean
        fun recordingRecoverAiOutboxUseCase() = RecordingRecoverAiOutboxUseCase()
    }
}
