package me.rgunny.kachi.ai.adapter.outbound.llm

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import me.rgunny.kachi.ai.config.LlmFailoverProperties
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.fake.NamedLlmProviderPort
import me.rgunny.kachi.ai.support.MutableClock
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("GuardedLlmProviderAdmin")
class GuardedLlmProviderAdminTest {
    private val clock = MutableClock()
    private val circuitBreakers = PROVIDER_NAMES.associateWith { circuitBreaker(it) }
    private val providers = PROVIDER_NAMES.map { guarded(it) }
    private val admin = GuardedLlmProviderAdmin(providers = providers, clock = clock)

    @Test
    @DisplayName("provider별 상태를 목록 순서 그대로 돌려준다")
    fun listStatusesPerProvider() {
        circuitBreakers.getValue("groq").transitionToOpenState()

        val statuses = admin.statuses()

        assertEquals(PROVIDER_NAMES, statuses.map { it.provider.value })
        assertEquals(
            CircuitBreaker.State.OPEN.name,
            statuses.single { it.provider.value == "groq" }.circuitBreakerState
        )
    }

    @Test
    @DisplayName("이름이 같은 provider의 차단을 되돌린다")
    fun resetMatchedProvider() {
        circuitBreakers.getValue("groq").transitionToOpenState()

        assertTrue(admin.reset(LlmProviderName.of("groq")))

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreakers.getValue("groq").state)
    }

    @Test
    @DisplayName("없는 이름은 아무것도 되돌리지 않고 실패로 알린다")
    fun rejectUnknownProvider() {
        circuitBreakers.getValue("groq").transitionToOpenState()

        assertFalse(admin.reset(LlmProviderName.of("unknown")))

        assertEquals(CircuitBreaker.State.OPEN, circuitBreakers.getValue("groq").state)
    }

    private fun guarded(name: String): GuardedLlmProvider {
        return GuardedLlmProvider(
            delegate = NamedLlmProviderPort(name),
            provider = LlmProviderName.of(name),
            circuitBreaker = circuitBreakers.getValue(name),
            failover = LlmFailoverProperties(
                defaultCooldown = Duration.ofSeconds(60),
                maxCooldown = Duration.ofMinutes(10)
            ),
            clock = clock
        )
    }

    private fun circuitBreaker(name: String): CircuitBreaker {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker(name)
    }

    private companion object {
        val PROVIDER_NAMES = listOf("openrouter", "groq")
    }
}
