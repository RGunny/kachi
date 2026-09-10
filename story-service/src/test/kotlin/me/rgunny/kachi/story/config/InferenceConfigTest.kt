package me.rgunny.kachi.story.config

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.rgunny.kachi.story.adapter.outbound.judge.ThresholdOnlyStoryLinkJudge
import me.rgunny.kachi.story.adapter.outbound.tei.GuardedTeiClient
import me.rgunny.kachi.story.adapter.outbound.tei.TeiHttpClient
import me.rgunny.kachi.story.adapter.outbound.tei.judge.TeiRerankJudge
import me.rgunny.kachi.story.application.exception.InferenceException
import me.rgunny.kachi.story.application.port.outbound.judge.StoryLinkJudge
import me.rgunny.kachi.story.domain.inference.InferenceFailureCode
import me.rgunny.kachi.story.support.TeiInfoJson
import me.rgunny.kachi.story.support.TestStubResponse
import me.rgunny.kachi.story.support.TestStubServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/** 조립과 기동 probe를 컨텍스트로 본다. */
@DisplayName("InferenceConfig")
class InferenceConfigTest {

    @BeforeEach
    fun resetStubs() {
        embeddingStub.reset()
        rerankerStub.reset()
        embeddingStub.respond(TeiHttpClient.INFO_PATH, ok(TeiInfoJson.EMBEDDING))
        rerankerStub.respond(TeiHttpClient.INFO_PATH, ok(TeiInfoJson.RERANKER))
    }

    @Test
    @DisplayName("원격 판정기 설정이면 서버 둘의 가드·판정기·health indicator가 조립되고 probe가 통과한다")
    fun assembleRemoteJudge() {
        runner().run { context ->
            val clients = context.getBean("teiClients", List::class.java).filterIsInstance<GuardedTeiClient>()
            assertEquals(2, clients.size)
            assertTrue(context.getBean(StoryLinkJudge::class.java) is TeiRerankJudge)
            assertTrue(context.containsBean("tei-embedding"))
            assertTrue(context.containsBean("tei-reranker"))

            probe(context.getBean("inferenceStartupProbe", ApplicationRunner::class.java))

            assertEquals(1, embeddingStub.requestCount(TeiHttpClient.INFO_PATH))
            assertEquals(1, rerankerStub.requestCount(TeiHttpClient.INFO_PATH))
        }
    }

    @Test
    @DisplayName("코사인만 쓰는 판정기 설정이면 판정기 서버 없이 조립된다")
    fun assembleThresholdOnly() {
        runner("${InferenceProperties.PREFIX}.judge.kind=THRESHOLD_ONLY").run { context ->
            val clients = context.getBean("teiClients", List::class.java)
            assertEquals(1, clients.size)
            assertTrue(context.getBean(StoryLinkJudge::class.java) is ThresholdOnlyStoryLinkJudge)
            assertTrue(context.containsBean("tei-embedding"))
            assertFalse(context.containsBean("tei-reranker"))

            probe(context.getBean("inferenceStartupProbe", ApplicationRunner::class.java))

            assertEquals(0, rerankerStub.requestCount(TeiHttpClient.INFO_PATH))
        }
    }

    @Test
    @DisplayName("임베딩 서버의 model_id가 다르면 probe가 기동을 실패시킨다")
    fun failProbeOnDifferentModel() {
        embeddingStub.respond(TeiHttpClient.INFO_PATH, ok(TeiInfoJson.EMBEDDING.replace("BAAI/bge-m3", "BAAI/bge-large")))

        runner().run { context ->
            val error = assertFailsWith<IllegalStateException> {
                probe(context.getBean("inferenceStartupProbe", ApplicationRunner::class.java))
            }

            assertTrue(error.message!!.contains("expected=BAAI/bge-m3, actual=BAAI/bge-large"))
        }
    }

    @Test
    @DisplayName("설정 batch-size가 서버의 max_client_batch_size를 넘으면 probe가 기동을 실패시킨다")
    fun failProbeOnBatchSizeOverLimit() {
        runner("${InferenceProperties.PREFIX}.embedding.batch-size=64").run { context ->
            val error = assertFailsWith<IllegalStateException> {
                probe(context.getBean("inferenceStartupProbe", ApplicationRunner::class.java))
            }

            assertTrue(error.message!!.contains("batch-size 64"))
        }
    }

    @Test
    @DisplayName("판정기 서버가 판정기 모델이 아니면 probe가 기동을 실패시킨다")
    fun failProbeOnJudgeKind() {
        rerankerStub.respond(TeiHttpClient.INFO_PATH, ok(TeiInfoJson.EMBEDDING.replace("BAAI/bge-m3", TeiRerankJudge.MODEL_ID)))

        runner().run { context ->
            assertFailsWith<IllegalStateException> {
                probe(context.getBean("inferenceStartupProbe", ApplicationRunner::class.java))
            }
        }
    }

    @Test
    @DisplayName("서버에 닿지 않으면 probe가 호출 실패로 기동을 실패시킨다")
    fun failProbeWhenUnreachable() {
        val closed = java.net.ServerSocket(0).use { it.localPort }

        runner("${InferenceProperties.PREFIX}.embedding.base-url=http://localhost:$closed").run { context ->
            val error = assertFailsWith<InferenceException> {
                probe(context.getBean("inferenceStartupProbe", ApplicationRunner::class.java))
            }

            assertEquals(InferenceFailureCode.INFERENCE_NETWORK_ERROR, error.failure.code)
        }
    }

    private fun probe(runner: ApplicationRunner) {
        runner.run(DefaultApplicationArguments())
    }

    private fun runner(vararg overrides: String): ApplicationContextRunner {
        return ApplicationContextRunner()
            .withUserConfiguration(InferenceConfig::class.java)
            .withPropertyValues(
                "${InferenceProperties.PREFIX}.embedding.model=BGE_M3",
                "${InferenceProperties.PREFIX}.embedding.base-url=${embeddingStub.baseUrl}",
                "${InferenceProperties.PREFIX}.embedding.connect-timeout=1s",
                "${InferenceProperties.PREFIX}.embedding.timeout=5s",
                "${InferenceProperties.PREFIX}.embedding.slow-after=2s",
                "${InferenceProperties.PREFIX}.embedding.batch-size=32",
                "${InferenceProperties.PREFIX}.judge.kind=BGE_RERANKER_V2_M3",
                "${InferenceProperties.PREFIX}.judge.base-url=${rerankerStub.baseUrl}",
                "${InferenceProperties.PREFIX}.judge.connect-timeout=1s",
                "${InferenceProperties.PREFIX}.judge.timeout=5s",
                "${InferenceProperties.PREFIX}.judge.slow-after=2s",
                "${InferenceProperties.PREFIX}.circuit-breaker.sliding-window-size=20",
                "${InferenceProperties.PREFIX}.circuit-breaker.minimum-number-of-calls=10",
                "${InferenceProperties.PREFIX}.circuit-breaker.failure-rate-threshold=50",
                "${InferenceProperties.PREFIX}.circuit-breaker.slow-call-rate-threshold=80",
                "${InferenceProperties.PREFIX}.circuit-breaker.wait-duration-in-open-state=30s",
                "${InferenceProperties.PREFIX}.circuit-breaker.permitted-number-of-calls-in-half-open-state=3",
                *overrides
            )
    }

    private fun ok(body: String): TestStubResponse = TestStubResponse(statusCode = 200, body = body)

    companion object {
        private val embeddingStub = TestStubServer()
        private val rerankerStub = TestStubServer()

        @JvmStatic
        @AfterAll
        fun closeStubs() {
            embeddingStub.close()
            rerankerStub.close()
        }
    }
}
