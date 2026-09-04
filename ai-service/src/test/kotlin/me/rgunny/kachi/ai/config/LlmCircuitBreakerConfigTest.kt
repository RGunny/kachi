package me.rgunny.kachi.ai.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@DisplayName("LlmCircuitBreakerConfig")
class LlmCircuitBreakerConfigTest {
    private val config = LlmCircuitBreakerConfig()

    @Test
    @DisplayName("프로퍼티 값과 모델의 느린 호출 판정 시간이 서킷 브레이커 설정에 그대로 반영된다")
    fun bindPropertiesToCircuitBreakerConfig() {
        val circuitBreakerConfig = config.circuitBreakerConfig(properties(), slowCallDurationThreshold = Duration.ofSeconds(8))

        assertEquals(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED, circuitBreakerConfig.slidingWindowType)
        assertEquals(6, circuitBreakerConfig.slidingWindowSize)
        assertEquals(3, circuitBreakerConfig.minimumNumberOfCalls)
        assertEquals(50f, circuitBreakerConfig.failureRateThreshold)
        assertEquals(Duration.ofSeconds(8), circuitBreakerConfig.slowCallDurationThreshold)
        assertEquals(80f, circuitBreakerConfig.slowCallRateThreshold)
        assertEquals(
            Duration.ofSeconds(60),
            circuitBreakerConfig.waitIntervalFunctionInOpenState.apply(1).let(Duration::ofMillis)
        )
        assertEquals(2, circuitBreakerConfig.permittedNumberOfCallsInHalfOpenState)
    }

    @ParameterizedTest
    @EnumSource(value = LlmFailureCode::class, names = [
        "LLM_TIMEOUT",
        "LLM_RATE_LIMITED",
        "LLM_TRANSIENT_ERROR",
        "LLM_NETWORK_ERROR"
    ])
    @DisplayName("실제 호출에서 나온 재시도 가능한 실패만 회로를 여는 근거로 기록한다")
    fun recordRetryableFailuresFromActualCall(code: LlmFailureCode) {
        val predicate = circuitBreakerConfig().recordExceptionPredicate

        assertTrue(predicate.test(AiTestFixture.llmProviderException(code)))
    }

    // 위 목록과 같은 값을 쓴다. 기록 대상이 늘었는데 한쪽만 고치면 그 코드가 이 테스트로 넘어와 바로 실패한다.
    // LLM_PROVIDER_UNAVAILABLE도 여기 들어온다. 차단이 만든 실패라 실제로는 predicate까지 오지 않지만,
    // 그 호출 순서가 바뀌어도 차단 실패가 회로를 다시 여는 근거가 되지 않도록 판정을 고정해 둔다.
    @ParameterizedTest
    @EnumSource(value = LlmFailureCode::class, mode = EnumSource.Mode.EXCLUDE, names = [
        "LLM_TIMEOUT",
        "LLM_RATE_LIMITED",
        "LLM_TRANSIENT_ERROR",
        "LLM_NETWORK_ERROR"
    ])
    @DisplayName("실제 호출에서 나오지 않았거나 재시도로 풀리지 않는 실패는 기록하지 않는다")
    fun doNotRecordFailuresOutsideCircuitEvidence(code: LlmFailureCode) {
        val predicate = circuitBreakerConfig().recordExceptionPredicate

        assertFalse(predicate.test(AiTestFixture.llmProviderException(code)))
    }

    @Test
    @DisplayName("LLM 실패가 아닌 예외는 기록하지 않는다")
    fun doNotRecordNonLlmException() {
        val predicate = circuitBreakerConfig().recordExceptionPredicate

        assertFalse(predicate.test(IllegalStateException("boom")))
    }

    @Test
    @DisplayName("registry는 후보 모델마다 그 모델의 느린 호출 판정 시간을 가진 설정을 이름으로 등록한다")
    fun registryHoldsConfigPerCandidateModel() {
        val registry = config.llmCircuitBreakerRegistry(AiTestFixture.llmProperties())
        val groq = LlmModel.GROQ_QWEN3_27B.qualifiedCode
        val mistral = LlmModel.MISTRAL_SMALL_2603.qualifiedCode

        assertEquals(
            AiTestFixture.llmProperties().models.getValue(LlmModel.GROQ_QWEN3_27B).slowAfter,
            registry.getConfiguration(groq).get().slowCallDurationThreshold
        )
        assertNotSame(registry.circuitBreaker(groq, groq), registry.circuitBreaker(mistral, mistral))
        assertSame(registry.circuitBreaker(groq, groq), registry.circuitBreaker(groq, groq))
    }

    @Test
    @DisplayName("후보가 아닌 모델의 설정은 registry에 없다")
    fun registryOmitsUnreferencedModel() {
        val registry = config.llmCircuitBreakerRegistry(AiTestFixture.llmProperties())
        val ollama = LlmModel.OLLAMA_QWEN3_27B.qualifiedCode

        assertTrue(registry.getConfiguration(ollama).isEmpty)
        assertFailsWith<io.github.resilience4j.core.ConfigurationNotFoundException> {
            registry.circuitBreaker(ollama, ollama)
        }
    }

    @Test
    @DisplayName("느린 호출 비율 임계치 100은 느린 호출로 열리지 않는 설정이다")
    fun slowCallRateThresholdOfHundredNeverOpens() {
        val circuitBreakerConfig = config.circuitBreakerConfig(
            properties(slowCallRateThreshold = 100f),
            slowCallDurationThreshold = Duration.ofSeconds(8)
        )

        assertEquals(100f, circuitBreakerConfig.slowCallRateThreshold)
    }

    private fun circuitBreakerConfig(): CircuitBreakerConfig {
        return config.circuitBreakerConfig(properties(), slowCallDurationThreshold = Duration.ofSeconds(8))
    }

    private fun properties(
        slowCallRateThreshold: Float = 80f
    ): LlmCircuitBreakerProperties {
        return AiTestFixture.circuitBreakerProperties(slowCallRateThreshold = slowCallRateThreshold)
    }
}
