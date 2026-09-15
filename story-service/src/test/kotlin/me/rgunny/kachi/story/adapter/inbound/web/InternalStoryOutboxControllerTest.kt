package me.rgunny.kachi.story.adapter.inbound.web

import java.util.UUID
import kotlin.test.assertEquals
import me.rgunny.kachi.story.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.story.application.exception.StoryOutboxNotFoundException
import me.rgunny.kachi.story.application.exception.StoryOutboxNotRecoverableException
import me.rgunny.kachi.story.application.port.inbound.outbox.model.FindStoryOutboxesQuery
import me.rgunny.kachi.story.application.port.inbound.outbox.model.FindStoryOutboxesResult
import me.rgunny.kachi.story.application.port.inbound.outbox.model.RecoverStoryOutboxResult
import me.rgunny.kachi.story.application.port.inbound.outbox.model.StoryOutboxSummary
import me.rgunny.kachi.story.config.ApiVersionConfig
import me.rgunny.kachi.story.domain.outbox.StoryOutboxId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus
import me.rgunny.kachi.story.fake.RecordingFindStoryOutboxesUseCase
import me.rgunny.kachi.story.fake.RecordingRecoverStoryOutboxUseCase
import me.rgunny.kachi.story.fixture.StoryTestFixture
import me.rgunny.kachi.story.support.JsonBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(controllers = [InternalStoryOutboxController::class])
@Import(ApiVersionConfig::class, InternalStoryOutboxControllerTest.TestBeans::class)
@DisplayName("InternalStoryOutboxController")
class InternalStoryOutboxControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var findUseCase: RecordingFindStoryOutboxesUseCase

    @Autowired
    private lateinit var recoverUseCase: RecordingRecoverStoryOutboxUseCase

    @BeforeEach
    fun resetUseCases() {
        // 컨트롤러 슬라이스 컨텍스트는 테스트끼리 공유되므로 호출 기록을 되돌린다.
        findUseCase.result = FindStoryOutboxesResult(emptyList())
        findUseCase.lastQuery = null
        findUseCase.invokeCount = 0
        recoverUseCase.result = null
        recoverUseCase.failure = null
        recoverUseCase.lastCommand = null
    }

    @Test
    @DisplayName("목록 조회는 파라미터를 조건으로 옮기고 payload 없는 스냅샷을 응답한다")
    fun findOutboxes() {
        val summary = deadSummary()
        findUseCase.result = FindStoryOutboxesResult(listOf(summary))

        val body = webTestClient.get()
            .uri { builder ->
                builder.path(ApiPaths.V1_INTERNAL_STORY_OUTBOXES)
                    .queryParam("status", StoryOutboxStatus.DEAD.name)
                    .queryParam("limit", 10)
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val row = JsonBody.parse(body).get("data").get(0)
        assertEquals(summary.id.value.toString(), row.get("id").asString())
        assertEquals(StoryOutboxStatus.DEAD.name, row.get("status").asString())
        assertEquals("broker down", row.get("lastError").asString())
        assertEquals(FindStoryOutboxesQuery(status = StoryOutboxStatus.DEAD, limit = 10), findUseCase.lastQuery)
    }

    @Test
    @DisplayName("조회 파라미터를 생략하면 기본 조건을 쓴다")
    fun findOutboxesWithDefaults() {
        webTestClient.get()
            .uri(ApiPaths.V1_INTERNAL_STORY_OUTBOXES)
            .exchange()
            .expectStatus().isOk

        assertEquals(FindStoryOutboxesQuery(), findUseCase.lastQuery)
    }

    @Test
    @DisplayName("status가 enum 값이 아니면 400으로 응답한다")
    fun rejectInvalidStatus() {
        val body = webTestClient.get()
            .uri { builder ->
                builder.path(ApiPaths.V1_INTERNAL_STORY_OUTBOXES)
                    .queryParam("status", "UNKNOWN")
                    .build()
            }
            .exchange()
            .expectStatus().isBadRequest
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        assertEquals(ErrorCode.INVALID_INTERNAL_REQUEST.name, JsonBody.parse(body).get("error").get("code").asString())
        assertEquals(0, findUseCase.invokeCount)
    }

    @Test
    @DisplayName("복구는 outboxId를 명령으로 옮기고 되돌린 행을 응답한다")
    fun recoverOutbox() {
        val summary = deadSummary().copy(status = StoryOutboxStatus.PENDING, retryCount = 0, lastError = null)
        recoverUseCase.result = RecoverStoryOutboxResult(outbox = summary, recoveredAt = StoryTestFixture.NOW)

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_STORY_OUTBOX_RECOVER, summary.id.value)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertEquals(summary.id.value.toString(), json.get("data").get("outbox").get("id").asString())
        assertEquals(StoryOutboxStatus.PENDING.name, json.get("data").get("outbox").get("status").asString())
        assertEquals(summary.id, requireNotNull(recoverUseCase.lastCommand).outboxId)
    }

    @Test
    @DisplayName("없는 행의 복구는 404로 응답한다")
    fun recoverMissingOutbox() {
        val outboxId = StoryOutboxId.newId()
        recoverUseCase.failure = StoryOutboxNotFoundException(outboxId)

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_STORY_OUTBOX_RECOVER, outboxId.value)
            .exchange()
            .expectStatus().isNotFound
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        assertEquals(ErrorCode.OUTBOX_NOT_FOUND.name, JsonBody.parse(body).get("error").get("code").asString())
    }

    @Test
    @DisplayName("DEAD가 아닌 행의 복구는 409로 응답한다")
    fun recoverNotDeadOutbox() {
        val outboxId = StoryOutboxId.newId()
        recoverUseCase.failure = StoryOutboxNotRecoverableException(outboxId, StoryOutboxStatus.PUBLISHED)

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_STORY_OUTBOX_RECOVER, outboxId.value)
            .exchange()
            .expectStatus().isEqualTo(ErrorCode.OUTBOX_NOT_RECOVERABLE.status)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        assertEquals(ErrorCode.OUTBOX_NOT_RECOVERABLE.name, JsonBody.parse(body).get("error").get("code").asString())
    }

    private fun deadSummary(): StoryOutboxSummary {
        return StoryOutboxSummary.from(
            StoryTestFixture.restoredOutbox(
                status = StoryOutboxStatus.DEAD,
                retryCount = 5,
                lastError = "broker down"
            )
        )
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {

        @Bean
        fun findStoryOutboxesUseCase(): RecordingFindStoryOutboxesUseCase = RecordingFindStoryOutboxesUseCase()

        @Bean
        fun recoverStoryOutboxUseCase(): RecordingRecoverStoryOutboxUseCase = RecordingRecoverStoryOutboxUseCase()
    }
}
