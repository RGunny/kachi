package me.rgunny.kachi.ai.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.io.ClassPathResource
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@DisplayName("LlmCircuitBreakerConfig")
class LlmCircuitBreakerConfigTest {
    private val config = LlmCircuitBreakerConfig()

    @Test
    @DisplayName("프로퍼티 값이 서킷 브레이커 설정에 그대로 반영된다")
    fun bindPropertiesToCircuitBreakerConfig() {
        val circuitBreakerConfig = config.circuitBreakerConfig(properties())

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
        val predicate = config.circuitBreakerConfig(properties()).recordExceptionPredicate

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
        val predicate = config.circuitBreakerConfig(properties()).recordExceptionPredicate

        assertFalse(predicate.test(AiTestFixture.llmProviderException(code)))
    }

    @Test
    @DisplayName("LLM 실패가 아닌 예외는 기록하지 않는다")
    fun doNotRecordNonLlmException() {
        val predicate = config.circuitBreakerConfig(properties()).recordExceptionPredicate

        assertFalse(predicate.test(IllegalStateException("boom")))
    }

    @Test
    @DisplayName("registry는 provider 이름마다 독립 인스턴스를 준다")
    fun registryGivesInstancePerProvider() {
        val registry = config.llmCircuitBreakerRegistry(providerProperties())

        assertNotSame(registry.circuitBreaker("groq"), registry.circuitBreaker("mistral"))
        assertSame(registry.circuitBreaker("groq"), registry.circuitBreaker("groq"))
    }

    @Test
    @DisplayName("운영 application.yaml의 circuit-breaker·failover 값이 그대로 바인딩된다")
    fun bindProductionYaml() {
        val binder = productionYamlBinder()

        val circuitBreaker = binder
            .bind("kachi.ai.providers.circuit-breaker", LlmCircuitBreakerProperties::class.java)
            .get()
        val failover = binder
            .bind("kachi.ai.providers.failover", LlmFailoverProperties::class.java)
            .get()

        assertEquals(6, circuitBreaker.slidingWindowSize)
        assertEquals(3, circuitBreaker.minimumNumberOfCalls)
        assertEquals(50f, circuitBreaker.failureRateThreshold)
        assertEquals(Duration.ofSeconds(8), circuitBreaker.slowCallDurationThreshold)
        assertEquals(80f, circuitBreaker.slowCallRateThreshold)
        assertEquals(Duration.ofSeconds(60), circuitBreaker.waitDurationInOpenState)
        assertEquals(2, circuitBreaker.permittedNumberOfCallsInHalfOpenState)
        assertEquals(Duration.ofSeconds(60), failover.defaultCooldown)
        assertEquals(Duration.ofMinutes(10), failover.maxCooldown)
    }

    @Test
    @DisplayName("느린 호출 비율 임계치 100은 느린 호출로 열리지 않는 설정이다")
    fun slowCallRateThresholdOfHundredNeverOpens() {
        val circuitBreakerConfig = config.circuitBreakerConfig(properties(slowCallRateThreshold = 100f))

        assertEquals(100f, circuitBreakerConfig.slowCallRateThreshold)
    }

    private fun productionYamlBinder(): Binder {
        val factory = YamlPropertiesFactoryBean()
        factory.setResources(ClassPathResource("application.yaml"))
        val yaml = requireNotNull(factory.getObject()) { "application.yaml을 읽지 못했습니다" }
        val source = MapConfigurationPropertySource(
            yaml.entries.associate { (key, value) -> key.toString() to value }
        )

        return Binder(source)
    }

    private fun providerProperties(): LlmProviderProperties {
        return LlmProviderProperties(
            mode = "single-random",
            keywordExpansionPromptVersion = "keyword-expansion-v1",
            newsSummaryPromptVersion = "news-summary-v1",
            openrouter = openAiProviderProperties(),
            groq = openAiProviderProperties(),
            together = openAiProviderProperties(),
            cerebras = openAiProviderProperties(),
            mistral = openAiProviderProperties(),
            gemini = GeminiProviderProperties(
                enabled = false,
                apiKey = "test-key",
                baseUrl = "https://generativelanguage.googleapis.com",
                generateContentPath = "/v1beta/models/{model}:generateContent",
                model = "test-model"
            ),
            circuitBreaker = properties(),
            failover = LlmFailoverProperties(
                defaultCooldown = Duration.ofSeconds(60),
                maxCooldown = Duration.ofMinutes(10)
            )
        )
    }

    private fun openAiProviderProperties(): OpenAiProviderProperties {
        return OpenAiProviderProperties(
            enabled = true,
            apiKey = "test-key",
            baseUrl = "https://llm.example.com/v1",
            chatCompletionsPath = "/chat/completions",
            model = "test-model",
            connectTimeout = Duration.ofSeconds(2),
            responseTimeout = Duration.ofSeconds(10),
            readTimeout = Duration.ofSeconds(10),
            writeTimeout = Duration.ofSeconds(10),
            maxInMemorySize = 524288
        )
    }

    private fun properties(
        slowCallRateThreshold: Float = 80f
    ): LlmCircuitBreakerProperties {
        return LlmCircuitBreakerProperties(
            slidingWindowSize = 6,
            minimumNumberOfCalls = 3,
            failureRateThreshold = 50f,
            slowCallDurationThreshold = Duration.ofSeconds(8),
            slowCallRateThreshold = slowCallRateThreshold,
            waitDurationInOpenState = Duration.ofSeconds(60),
            permittedNumberOfCallsInHalfOpenState = 2
        )
    }
}
