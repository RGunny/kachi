package me.rgunny.kachi.story.config

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import java.time.Duration
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.outbound.judge.ThresholdOnlyStoryLinkJudge
import me.rgunny.kachi.story.adapter.outbound.tei.GuardedTeiClient
import me.rgunny.kachi.story.adapter.outbound.tei.TeiHealthIndicator
import me.rgunny.kachi.story.adapter.outbound.tei.TeiHttpClient
import me.rgunny.kachi.story.adapter.outbound.tei.TeiStartupProbe
import me.rgunny.kachi.story.adapter.outbound.tei.embedding.TeiEmbeddingAdapter
import me.rgunny.kachi.story.adapter.outbound.tei.judge.TeiRerankJudge
import me.rgunny.kachi.story.application.exception.InferenceException
import me.rgunny.kachi.story.application.port.outbound.embedding.EmbeddingPort
import me.rgunny.kachi.story.application.port.outbound.judge.StoryLinkJudge
import me.rgunny.kachi.story.domain.StoryJudge
import me.rgunny.kachi.story.domain.inference.InferenceTarget
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 추론 서버 호출 층을 조립하는 설정.
 *
 * - 전략 패턴: 판정기는 [StoryLinkJudge]를 구현하는 adapter 중 설정이 고른 하나다.
 *   조립 유스케이스는 어느 판정기인지 모른 채 다형성으로 부른다.
 *   판정기가 늘면 상수와 adapter가 하나씩 늘고 [storyLinkJudge]의 `when`이 빠진 분기를 컴파일에서 잡는다.
 * - 데코레이터 패턴: HTTP 클라이언트를 같은 인터페이스로 감싸는 [GuardedTeiClient]를 씌운다.
 *   차단 정책이 바뀌어도 adapter는 그대로다.
 */
@Configuration
@EnableConfigurationProperties(InferenceProperties::class)
class InferenceConfig {

    /**
     * 서버마다 자기 회로를 가진 가드를 씌운다.
     *
     * 판정기 서버는 원격 판정기를 골랐을 때만 있다.
     */
    @Bean
    fun teiClients(properties: InferenceProperties): List<GuardedTeiClient> {
        val embedding = guarded(
            target = InferenceTarget.EMBEDDING,
            server = properties.embedding.server,
            expectedDimension = properties.embedding.model.dimension,
            circuitBreaker = properties.circuitBreaker
        )
        val judge = properties.judge.server?.let { server ->
            guarded(
                target = InferenceTarget.JUDGE,
                server = server,
                expectedDimension = null,
                circuitBreaker = properties.circuitBreaker
            )
        }

        return listOfNotNull(embedding, judge)
    }

    @Bean
    fun embeddingPort(
        properties: InferenceProperties,
        teiClients: List<GuardedTeiClient>
    ): EmbeddingPort {
        return TeiEmbeddingAdapter(
            client = teiClients.of(InferenceTarget.EMBEDDING),
            model = properties.embedding.model,
            batchSize = properties.embedding.batchSize
        )
    }

    @Bean
    fun storyLinkJudge(
        properties: InferenceProperties,
        teiClients: List<GuardedTeiClient>
    ): StoryLinkJudge {
        // Strategy Pattern
        // StoryLinkJudge가 전략의 공통 계약이고 판정기마다 그것을 구현한 adapter가 구체 전략이다.
        // 어느 전략을 쓸지는 judge.kind 하나로 여기서 고른다. 새 StoryJudge 상수에 분기가 빠지면 이 when이 컴파일에서 잡는다.
        return when (properties.judge.kind) {
            StoryJudge.BGE_RERANKER_V2_M3 -> TeiRerankJudge(client = teiClients.of(InferenceTarget.JUDGE))
            StoryJudge.THRESHOLD_ONLY -> ThresholdOnlyStoryLinkJudge()
        }
    }

    /** 기동 시 서버 둘의 정체를 확인한다. */
    @Bean
    fun inferenceStartupProbe(
        properties: InferenceProperties,
        teiClients: List<GuardedTeiClient>
    ): ApplicationRunner {
        return ApplicationRunner {
            runBlocking {
                val embeddingInfo = TeiStartupProbe(
                    client = teiClients.of(InferenceTarget.EMBEDDING),
                    target = InferenceTarget.EMBEDDING,
                    expectedModelId = properties.embedding.model.modelId
                ).verify()
                val maxClientBatchSize = checkNotNull(embeddingInfo.max_client_batch_size) {
                    "TEI EMBEDDING 서버가 max_client_batch_size를 보고하지 않았습니다"
                }
                check(properties.embedding.batchSize <= maxClientBatchSize) {
                    "임베딩 batch-size ${properties.embedding.batchSize}가 서버 한도 $maxClientBatchSize 를 넘습니다"
                }
                log.info(
                    "TEI embedding server verified: model={}, sha={}, maxInputLength={}, maxClientBatchSize={}",
                    embeddingInfo.model_id,
                    embeddingInfo.model_sha,
                    embeddingInfo.max_input_length,
                    maxClientBatchSize
                )

                teiClients.firstOrNull { it.target == InferenceTarget.JUDGE }?.let { judge ->
                    val judgeInfo = TeiStartupProbe(
                        client = judge,
                        target = InferenceTarget.JUDGE,
                        expectedModelId = TeiRerankJudge.MODEL_ID
                    ).verify()
                    log.info(
                        "TEI judge server verified: model={}, sha={}, dtype={}, maxInputLength={}",
                        judgeInfo.model_id,
                        judgeInfo.model_sha,
                        judgeInfo.model_dtype,
                        judgeInfo.max_input_length
                    )
                }
            }
        }
    }

    @Bean("tei-embedding")
    fun teiEmbeddingHealthIndicator(properties: InferenceProperties): TeiHealthIndicator {
        return healthIndicator(properties.embedding.server)
    }

    @Bean("tei-reranker")
    @ConditionalOnProperty(prefix = InferenceProperties.PREFIX, name = ["judge.kind"], havingValue = "BGE_RERANKER_V2_M3")
    fun teiRerankerHealthIndicator(properties: InferenceProperties): TeiHealthIndicator {
        return healthIndicator(checkNotNull(properties.judge.server) { "원격 판정기 서버 설정이 없습니다" })
    }

    /** 실제 호출에서 나온 일시 실패만 회로를 여는 근거로 삼는다. */
    fun circuitBreakerConfig(
        properties: InferenceCircuitBreakerProperties,
        slowCallDurationThreshold: Duration
    ): CircuitBreakerConfig {
        return CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(properties.slidingWindowSize)
            .minimumNumberOfCalls(properties.minimumNumberOfCalls)
            .failureRateThreshold(properties.failureRateThreshold)
            .slowCallDurationThreshold(slowCallDurationThreshold)
            .slowCallRateThreshold(properties.slowCallRateThreshold)
            .waitDurationInOpenState(properties.waitDurationInOpenState)
            .permittedNumberOfCallsInHalfOpenState(properties.permittedNumberOfCallsInHalfOpenState)
            .recordException { it is InferenceException && it.failure.recordsInCircuit }
            .build()
    }

    private fun guarded(
        target: InferenceTarget,
        server: TeiServerProperties,
        expectedDimension: Int?,
        circuitBreaker: InferenceCircuitBreakerProperties
    ): GuardedTeiClient {
        // Decorator Pattern
        // TeiHttpClient를 같은 인터페이스 TeiClient로 감싼다. GuardedTeiClient는 호출을 받으면 서킷을 검사하고 통과하면 delegate에 그대로 넘긴다.
        // adapter 둘은 감싸인 사실을 모르므로 차단 장치를 더하거나 빼도 adapter는 바뀌지 않는다.
        return GuardedTeiClient(
            delegate = TeiHttpClient(
                webClient = TeiWebClients.forServer(server),
                target = target,
                expectedDimension = expectedDimension
            ),
            target = target,
            circuitBreaker = CircuitBreaker.of(circuitName(target), circuitBreakerConfig(circuitBreaker, server.slowAfter))
        )
    }

    private fun healthIndicator(server: TeiServerProperties): TeiHealthIndicator {
        return TeiHealthIndicator(webClient = TeiWebClients.forServer(server), baseUrl = server.baseUrl)
    }

    private fun List<GuardedTeiClient>.of(target: InferenceTarget): GuardedTeiClient {
        return first { it.target == target }
    }

    private fun circuitName(target: InferenceTarget): String = "tei-${target.name.lowercase()}"

    private companion object {
        val log = LoggerFactory.getLogger(InferenceConfig::class.java)
    }
}
