package me.rgunny.kachi.story.adapter.inbound.web

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.rgunny.kachi.story.adapter.inbound.index.IndexRebuildExecutor
import me.rgunny.kachi.story.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.story.application.port.outbound.lock.model.AlreadyHeldExecutionLockOutcome
import me.rgunny.kachi.story.application.port.outbound.lock.model.ExecutionLockHolder
import me.rgunny.kachi.story.application.port.outbound.lock.model.UnavailableExecutionLockOutcome
import me.rgunny.kachi.story.config.ApiVersionConfig
import me.rgunny.kachi.story.fake.FakeExecutionLockPort
import me.rgunny.kachi.story.fake.FakeRebuildCandidateIndexUseCase
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
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

@WebFluxTest(controllers = [InternalStoryIndexController::class])
@Import(ApiVersionConfig::class, InternalStoryIndexControllerTest.TestBeans::class)
@DisplayName("InternalStoryIndexController")
class InternalStoryIndexControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var lock: FakeExecutionLockPort

    @BeforeEach
    fun resetLock() {
        // 컨트롤러 슬라이스 컨텍스트는 테스트끼리 공유되므로 lock 결과를 되돌린다.
        lock.outcome = null
    }

    @Test
    @DisplayName("재구축을 시작하면 시작 여부만 응답한다")
    fun startRebuild() {
        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_STORY_INDEX_REBUILD)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        val json = JsonBody.parse(body)
        assertTrue(json.get("success").asBoolean())
        assertEquals(true, json.get("data").get("started").asBoolean())
    }

    @Test
    @DisplayName("다른 재구축이 실행 중이면 409로 응답한다")
    fun rejectWhenAlreadyRunning() {
        lock.outcome = AlreadyHeldExecutionLockOutcome(ExecutionLockHolder(acquiredAt = NOW, owner = "other"))

        val body = webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_STORY_INDEX_REBUILD)
            .exchange()
            .expectStatus().isEqualTo(ErrorCode.INDEX_REBUILD_ALREADY_RUNNING.status)
            .expectBody(String::class.java)
            .returnResult()
            .responseBody

        assertEquals(ErrorCode.INDEX_REBUILD_ALREADY_RUNNING.name, JsonBody.parse(body).get("error").get("code").asString())
    }

    @Test
    @DisplayName("lock을 확인할 수 없으면 503으로 응답한다")
    fun rejectWhenLockUnavailable() {
        lock.outcome = UnavailableExecutionLockOutcome(IllegalStateException("lock store unavailable"))

        webTestClient.post()
            .uri(ApiPaths.V1_INTERNAL_STORY_INDEX_REBUILD)
            .exchange()
            .expectStatus().isEqualTo(ErrorCode.INDEX_REBUILD_LOCK_UNAVAILABLE.status)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestBeans {

        @Bean
        fun executionLockPort(): FakeExecutionLockPort = FakeExecutionLockPort()

        @Bean
        fun rebuildCandidateIndexUseCase(): FakeRebuildCandidateIndexUseCase {
            // 컨트롤러 슬라이스는 완료를 기다리지 않으므로 본체는 즉시 끝나게 둔다.
            return FakeRebuildCandidateIndexUseCase().apply { gate.complete(Unit) }
        }

        @Bean
        fun indexRebuildExecutor(
            useCase: FakeRebuildCandidateIndexUseCase,
            lock: FakeExecutionLockPort
        ): IndexRebuildExecutor = IndexRebuildExecutor(useCase, lock)
    }
}
