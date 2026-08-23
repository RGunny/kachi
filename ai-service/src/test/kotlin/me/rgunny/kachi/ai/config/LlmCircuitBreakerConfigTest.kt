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
    @DisplayName("프로퍼티 값이 circuit breaker 설정에 그대로 반영된다")
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
    @DisplayName("재시도 가능한 LLM 실패만 회로를 여는 근거로 기록한다")
    fun recordRetryableLlmFailures(code: LlmFailureCode) {
        val predicate = config.circuitBreakerConfig(properties()).recordExceptionPredicate

        assertTrue(predicate.test(AiTestFixture.llmProviderException(code)))
    }

    @ParameterizedTest
    @EnumSource(value = LlmFailureCode::class, names = [
        "LLM_INVALID_RESPONSE",
        "LLM_CLIENT_ERROR",
        "LLM_AUTHORIZATION_ERROR",
        "LLM_UNKNOWN_ERROR"
    ])
    @DisplayName("키워드 귀속 실패와 인증 실패는 기록하지 않는다")
    fun doNotRecordKeywordBoundOrAuthorizationFailures(code: LlmFailureCode) {
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
