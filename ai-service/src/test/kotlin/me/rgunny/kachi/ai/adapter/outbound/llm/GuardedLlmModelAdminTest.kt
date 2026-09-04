package me.rgunny.kachi.ai.adapter.outbound.llm

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import me.rgunny.kachi.ai.config.LlmCooldownProperties
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.fake.NamedLlmProviderPort
import me.rgunny.kachi.ai.support.MutableClock
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("GuardedLlmModelAdmin")
class GuardedLlmModelAdminTest {
    private val clock = MutableClock()
    private val circuitBreakers = MODELS.associateWith { circuitBreaker(it) }
    private val models = MODELS.map { guarded(it) }
    private val admin = GuardedLlmModelAdmin(models = models, clock = clock)

    @Test
    @DisplayName("모델별 상태를 목록 순서 그대로 돌려준다")
    fun listStatusesPerModel() {
        circuitBreakers.getValue(OPEN_MODEL).transitionToOpenState()

        val statuses = admin.statuses()

        assertEquals(MODELS, statuses.map { it.model })
        assertEquals(
            CircuitBreaker.State.OPEN.name,
            statuses.single { it.model == OPEN_MODEL }.circuitBreakerState
        )
    }

    @Test
    @DisplayName("지정한 모델의 차단을 되돌린다")
    fun resetMatchedModel() {
        circuitBreakers.getValue(OPEN_MODEL).transitionToOpenState()

        assertTrue(admin.reset(OPEN_MODEL))

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreakers.getValue(OPEN_MODEL).state)
    }

    @Test
    @DisplayName("후보에 없는 모델은 아무것도 되돌리지 않고 실패로 알린다")
    fun rejectUnknownModel() {
        circuitBreakers.getValue(OPEN_MODEL).transitionToOpenState()

        assertFalse(admin.reset(LlmModel.OLLAMA_QWEN3_27B))

        assertEquals(CircuitBreaker.State.OPEN, circuitBreakers.getValue(OPEN_MODEL).state)
    }

    private fun guarded(model: LlmModel): GuardedLlmModel {
        return GuardedLlmModel(
            delegate = NamedLlmProviderPort(model.qualifiedCode, model.provider),
            model = model,
            circuitBreaker = circuitBreakers.getValue(model),
            cooldown = LlmCooldownProperties(default = Duration.ofSeconds(60), max = Duration.ofMinutes(10)),
            clock = clock
        )
    }

    private fun circuitBreaker(model: LlmModel): CircuitBreaker {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker(model.qualifiedCode)
    }

    private companion object {
        val MODELS = listOf(LlmModel.GROQ_QWEN3_27B, LlmModel.MISTRAL_SMALL_2603)
        val OPEN_MODEL = LlmModel.MISTRAL_SMALL_2603
    }
}
