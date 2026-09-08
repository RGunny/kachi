package me.rgunny.kachi.collector.adapter.inbound.web

import me.rgunny.kachi.collector.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.collector.application.exception.CollectorOutboxNotFoundException
import me.rgunny.kachi.collector.application.exception.CollectorOutboxNotRecoverableException
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.CollectorOutboxSummary
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.FindCollectorOutboxesQuery
import me.rgunny.kachi.collector.config.ApiVersionConfig
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxId
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus
import me.rgunny.kachi.collector.fake.RecordingFindCollectorOutboxesUseCase
import me.rgunny.kachi.collector.fake.RecordingRecoverCollectorOutboxUseCase
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import me.rgunny.kachi.collector.support.JsonBody
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

@WebFluxTest(controllers = [InternalCollectorOutboxController::class])
@Import(ApiVersionConfig::class, InternalCollectorOutboxControllerTest.TestBeans::class)
@DisplayName("InternalCollectorOutboxController")
class InternalCollectorOutboxControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var findUseCase: RecordingFindCollectorOutboxesUseCase

    @Autowired
    private lateinit var recoverUseCase: RecordingRecoverCollectorOutboxUseCase

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
                builder.path(ApiPaths.V1_INTERNAL_COLLECTOR_OUTBOXES)
                    .queryParam("status", CollectorOutboxStatus.PENDING.name)
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
        assertEquals(CollectorTestFixture.OUTBOX_EVENT_KEY, outbox.get("eventKey").asString())
        assertEquals(CollectorOutboxStatus.DEAD.name, outbox.get("status").asString())
        assertFalse(outbox.has("payload"), "목록 응답에 payload가 없어야 합니다")

        val query = requireNotNull(findUseCase.lastQuery)
        assertEquals(CollectorOutboxStatus.PENDING, query.status)
        assertEquals(10, query.limit)
    }

    @Test
    @DisplayName("조회 조건을 생략하면 DEAD를 기본 개수만큼 조회한다")
    fun findOutboxesWithDefaults() {
        webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_COLLECTOR_OUTBOXES)
            .exchange()
            .expectStatus().isOk

        val query = requireNotNull(findUseCase.lastQuery)
        assertEquals(CollectorOutboxStatus.DEAD, query.status)
        assertEquals(FindCollectorOutboxesQuery.DEFAULT_LIMIT, query.limit)
    }

    @Test
    @DisplayName("DEAD 행을 복구하면 복구된 행과 복구 시각을 응답한다")
    fun recoverOutbox() {
        val outboxId = UUID.randomUUID()

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_COLLECTOR_OUTBOX_RECOVER, outboxId)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())
        assertEquals(CollectorOutboxStatus.PENDING.name, json.get("data").get("outbox").get("status").asString())
        assertEquals(CollectorTestFixture.NOW.toString(), json.get("data").get("recoveredAt").asString())
        assertEquals(outboxId, requireNotNull(recoverUseCase.lastCommand).outboxId.value)
    }

    @Test
    @DisplayName("복구 대상 행이 없으면 404로 응답한다")
    fun recoverMissingOutbox() {
        recoverUseCase.failure = CollectorOutboxNotFoundException(CollectorOutboxId.newId())

        assertFailureResponse(ErrorCode.COLLECTOR_OUTBOX_NOT_FOUND)
    }

    @Test
    @DisplayName("DEAD 상태가 아니면 409로 응답한다")
    fun recoverNotDeadOutbox() {
        recoverUseCase.failure = CollectorOutboxNotRecoverableException(CollectorOutboxId.newId(), CollectorOutboxStatus.PENDING)

        assertFailureResponse(ErrorCode.COLLECTOR_OUTBOX_NOT_RECOVERABLE)
    }

    @Test
    @DisplayName("id가 UUID가 아니면 400으로 응답한다")
    fun rejectInvalidOutboxId() {
        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_COLLECTOR_OUTBOX_RECOVER, "not-a-uuid")
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
            .uri(ApiPaths.V1_INTERNAL_COLLECTOR_OUTBOX_RECOVER, UUID.randomUUID())
            .exchange()
            .expectStatus().isEqualTo(errorCode.status)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(false, json.get("success").asBoolean())
        assertEquals(errorCode.name, json.get("error").get("code").asString())
    }

    private fun summary(): CollectorOutboxSummary {
        return CollectorOutboxSummary.from(
            CollectorTestFixture.restoredOutbox(
                status = CollectorOutboxStatus.DEAD,
                retryCount = 5,
                lastError = "broker down"
            )
        )
    }

    @TestConfiguration
    class TestBeans {

        @Bean
        fun recordingFindCollectorOutboxesUseCase() = RecordingFindCollectorOutboxesUseCase()

        @Bean
        fun recordingRecoverCollectorOutboxUseCase() = RecordingRecoverCollectorOutboxUseCase()
    }
}
