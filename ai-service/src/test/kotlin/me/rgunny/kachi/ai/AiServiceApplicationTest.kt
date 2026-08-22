package me.rgunny.kachi.ai

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import me.rgunny.kachi.ai.adapter.outbound.llm.GuardedLlmProvider
import me.rgunny.kachi.ai.adapter.outbound.llm.RoutingLlmProvider
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@ActiveProfiles("test")
@SpringBootTest
@Import(AiServiceTestContainersConfig::class)
class AiServiceApplicationTest {

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @Autowired
    private lateinit var llmProviderPort: LlmProviderPort

    @Test
    fun contextLoads() {
    }

    @Test
    @DisplayName("provider마다 circuit breaker 인스턴스가 등록된다")
    fun registerCircuitBreakerPerProvider() {
        val names = circuitBreakerRegistry.allCircuitBreakers.map { it.name }.toSet()

        assertEquals(setOf("openrouter", "groq", "together", "cerebras", "mistral"), names)
    }

    @Test
    @DisplayName("컨텍스트의 circuit breaker 설정은 운영 yaml 값과 같다")
    fun bindProductionCircuitBreakerConfig() {
        val config = circuitBreakerRegistry.circuitBreaker("groq").circuitBreakerConfig

        assertEquals(6, config.slidingWindowSize)
        assertEquals(3, config.minimumNumberOfCalls)
        assertEquals(50f, config.failureRateThreshold)
        assertEquals(Duration.ofSeconds(8), config.slowCallDurationThreshold)
        assertEquals(80f, config.slowCallRateThreshold)
        assertEquals(2, config.permittedNumberOfCallsInHalfOpenState)
    }

    @Test
    @DisplayName("LLM provider 포트는 회로로 감싼 provider를 순회하는 router다")
    fun llmProviderPortRoutesGuardedProviders() {
        val router = assertIs<RoutingLlmProvider>(llmProviderPort)

        assertTrue(router.providers.isNotEmpty())
        router.providers.forEach { assertIs<GuardedLlmProvider>(it) }
    }
}
