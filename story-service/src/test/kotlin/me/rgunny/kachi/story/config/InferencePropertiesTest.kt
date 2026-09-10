package me.rgunny.kachi.story.config

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import me.rgunny.kachi.story.domain.EmbeddingModel
import me.rgunny.kachi.story.domain.StoryJudge
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource

/** 항목 하나 안의 검증과 운영 yaml 바인딩을 보는 테스트. */
@DisplayName("InferenceProperties")
class InferencePropertiesTest {

    @Test
    @DisplayName("서버 base-url은 비어 있을 수 없고 connect-timeout은 양수여야 한다")
    fun rejectBlankBaseUrlAndNonPositiveConnectTimeout() {
        assertFailsWith<IllegalArgumentException> { server(baseUrl = " ") }
        assertFailsWith<IllegalArgumentException> { server(connectTimeout = Duration.ZERO) }
    }

    @Test
    @DisplayName("서버의 slow-after는 양수이고 timeout보다 짧아야 한다")
    fun requireSlowAfterShorterThanTimeout() {
        assertFailsWith<IllegalArgumentException> { server(slowAfter = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { server(timeout = Duration.ofSeconds(10), slowAfter = Duration.ofSeconds(10)) }
        assertFailsWith<IllegalArgumentException> { server(timeout = Duration.ofSeconds(10), slowAfter = Duration.ofSeconds(11)) }
    }

    @Test
    @DisplayName("임베딩 batch-size는 1 이상이어야 한다")
    fun rejectNonPositiveBatchSize() {
        assertFailsWith<IllegalArgumentException> { embedding(batchSize = 0) }
        assertEquals(32, embedding().batchSize)
    }

    @Test
    @DisplayName("원격 판정기는 서버 값 넷이 전부 있어야 한다")
    fun requireServerValuesForRemoteJudge() {
        val error = assertFailsWith<IllegalArgumentException> {
            InferenceProperties.JudgeProperties(kind = StoryJudge.BGE_RERANKER_V2_M3)
        }
        assertEquals("판정기 BGE_RERANKER_V2_M3의 base-url이 없습니다", error.message)

        assertFailsWith<IllegalArgumentException> {
            InferenceProperties.JudgeProperties(kind = StoryJudge.BGE_RERANKER_V2_M3, baseUrl = "http://x", connectTimeout = Duration.ofSeconds(1))
        }
        val judge = InferenceProperties.JudgeProperties(
            kind = StoryJudge.BGE_RERANKER_V2_M3,
            baseUrl = "http://x",
            connectTimeout = Duration.ofSeconds(1),
            timeout = Duration.ofSeconds(5),
            slowAfter = Duration.ofSeconds(2)
        )
        assertEquals("http://x", assertNotNull(judge.server).baseUrl)
    }

    @Test
    @DisplayName("코사인만 쓰는 판정기는 서버 값이 없어도 된다")
    fun allowThresholdOnlyWithoutServer() {
        val judge = InferenceProperties.JudgeProperties(kind = StoryJudge.THRESHOLD_ONLY)

        assertNull(judge.server)
    }

    /** 운영 yaml의 값이 enum 키와 단축형 시간 표기 그대로 바인딩되는지 본다. */
    @Test
    @DisplayName("운영 application.yaml의 kachi.story.inference 값이 그대로 바인딩된다")
    fun bindProductionYaml() {
        val properties = productionYamlBinder().bind(InferenceProperties.PREFIX, InferenceProperties::class.java).get()

        assertEquals(EmbeddingModel.BGE_M3, properties.embedding.model)
        assertEquals("http://localhost:8090", properties.embedding.baseUrl)
        assertEquals(Duration.ofSeconds(2), properties.embedding.connectTimeout)
        assertEquals(Duration.ofSeconds(10), properties.embedding.timeout)
        assertEquals(Duration.ofSeconds(5), properties.embedding.slowAfter)
        assertEquals(32, properties.embedding.batchSize)

        assertEquals(StoryJudge.BGE_RERANKER_V2_M3, properties.judge.kind)
        assertEquals("http://localhost:8091", assertNotNull(properties.judge.server).baseUrl)
        assertEquals(Duration.ofSeconds(10), properties.judge.timeout)

        val circuitBreaker = properties.circuitBreaker
        assertEquals(20, circuitBreaker.slidingWindowSize)
        assertEquals(10, circuitBreaker.minimumNumberOfCalls)
        assertEquals(50f, circuitBreaker.failureRateThreshold)
        assertEquals(80f, circuitBreaker.slowCallRateThreshold)
        assertEquals(Duration.ofSeconds(30), circuitBreaker.waitDurationInOpenState)
        assertEquals(3, circuitBreaker.permittedNumberOfCallsInHalfOpenState)
    }

    private fun productionYamlBinder(): Binder {
        val factory = YamlPropertiesFactoryBean()
        factory.setResources(ClassPathResource("application.yaml"))
        val yaml = requireNotNull(factory.getObject()) { "application.yaml을 읽지 못했습니다" }
        val source = MapConfigurationPropertySource(
            // 운영 yaml의 ${ENV:default} 자리표시는 Binder가 풀지 않으므로 기본값으로 바꾼다.
            yaml.entries.associate { (key, value) -> key.toString() to value.toString().replace(Regex("\\$\\{[^:}]*:([^}]*)}"), "$1") }
        )

        return Binder(source)
    }

    private fun server(
        baseUrl: String = "http://localhost:8090",
        connectTimeout: Duration = Duration.ofSeconds(2),
        timeout: Duration = Duration.ofSeconds(10),
        slowAfter: Duration = Duration.ofSeconds(5)
    ): TeiServerProperties {
        return TeiServerProperties(baseUrl = baseUrl, connectTimeout = connectTimeout, timeout = timeout, slowAfter = slowAfter)
    }

    private fun embedding(batchSize: Int = 32): InferenceProperties.EmbeddingProperties {
        return InferenceProperties.EmbeddingProperties(
            model = EmbeddingModel.BGE_M3,
            baseUrl = "http://localhost:8090",
            connectTimeout = Duration.ofSeconds(2),
            timeout = Duration.ofSeconds(10),
            slowAfter = Duration.ofSeconds(5),
            batchSize = batchSize
        )
    }
}
