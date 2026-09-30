package me.rgunny.kachi.ai.adapter.outbound.llm

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.domain.llm.LlmBilling
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.fake.NamedLlmProviderPort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.MutableClock
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("GuardedLlmModelAdmin")
class GuardedLlmModelAdminTest {
    private val clock = MutableClock()
    private val providerHolds = ProviderHoldRegistry()
    private val delegates = MODELS.associateWith { NamedLlmProviderPort(it.qualifiedCode, it.provider) }
    private val circuitBreakers = MODELS.associateWith { circuitBreaker(it) }
    private val models = MODELS.map { guarded(it) }
    private val admin = GuardedLlmModelAdmin(models = models, clock = clock)

    @Test
    @DisplayName("모델별 상태를 목록 순서 그대로 과금 방식과 함께 돌려준다")
    fun listStatusesPerModel() {
        circuitBreakers.getValue(OPEN_MODEL).transitionToOpenState()

        val statuses = admin.statuses()

        assertEquals(MODELS, statuses.map { it.model })
        assertEquals(MODELS.map { BILLING.getValue(it) }, statuses.map { it.billing })
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

    @Test
    @DisplayName("probe는 지정한 모델 하나에만 실제 요청을 보내고 응답 메타데이터와 걸린 시간을 돌려준다")
    fun probeOnlyThatModel() = runBlocking {
        val result = assertNotNull(admin.probe(OPEN_MODEL))

        assertEquals(OPEN_MODEL, result.model)
        assertEquals(BILLING.getValue(OPEN_MODEL), result.billing)
        assertEquals(OPEN_MODEL.qualifiedCode, result.metadata.requestedModel)
        assertEquals(AiTestFixture.KEYWORD_EXPANSION_PROMPT_VERSION, result.metadata.promptVersion)
        assertFalse(result.latency.isNegative)
        assertTrue(result.expandedKeywords.isNotEmpty())
        assertEquals(1, delegates.getValue(OPEN_MODEL).expandCallCount)
        assertEquals(0, delegates.getValue(OTHER_MODEL).callCount)
    }

    @Test
    @DisplayName("probe는 가드를 그대로 지나므로 차단 중인 모델은 호출 없이 실패한다")
    fun failProbeWhenModelIsBlocked() = runBlocking {
        circuitBreakers.getValue(OPEN_MODEL).transitionToOpenState()

        val blocked = assertFailsWith<LlmProviderException> { admin.probe(OPEN_MODEL) }

        assertEquals(LlmFailureCode.LLM_NOT_PERMITTED, blocked.failure.code)
        assertEquals(0, delegates.getValue(OPEN_MODEL).callCount)
    }

    @Test
    @DisplayName("probe에서 받은 실패는 평소 호출과 같이 상태에 남는다")
    fun recordProbeFailureInGuard() = runBlocking {
        delegates.getValue(OPEN_MODEL).failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_MODEL_NOT_FOUND)

        val failure = assertFailsWith<LlmProviderException> { admin.probe(OPEN_MODEL) }

        assertEquals(LlmFailureCode.LLM_MODEL_NOT_FOUND, failure.failure.code)
        assertEquals(
            LlmFailureCode.LLM_MODEL_NOT_FOUND,
            admin.statuses().single { it.model == OPEN_MODEL }.hold?.code
        )
    }

    @Test
    @DisplayName("후보에 없는 모델의 probe는 아무것도 부르지 않고 null이다")
    fun returnNullForUnknownProbeTarget() = runBlocking {
        assertNull(admin.probe(LlmModel.OLLAMA_QWEN3_27B))

        assertTrue(delegates.values.all { it.callCount == 0 })
    }

    private fun guarded(model: LlmModel): GuardedLlmModel {
        return GuardedLlmModel(
            delegate = delegates.getValue(model),
            model = model,
            billing = BILLING.getValue(model),
            circuitBreaker = circuitBreakers.getValue(model),
            cooldown = LlmCooldownSettings(default = Duration.ofSeconds(60), max = Duration.ofMinutes(10)),
            hold = AiTestFixture.holdSettings(),
            providerHolds = providerHolds,
            clock = clock
        )
    }

    private fun circuitBreaker(model: LlmModel): CircuitBreaker {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults()).circuitBreaker(model.qualifiedCode)
    }

    private companion object {
        val MODELS = listOf(LlmModel.GROQ_QWEN3_27B, LlmModel.MISTRAL_SMALL_2603)
        val OPEN_MODEL = LlmModel.MISTRAL_SMALL_2603
        val OTHER_MODEL = LlmModel.GROQ_QWEN3_27B
        val BILLING = mapOf(
            LlmModel.GROQ_QWEN3_27B to LlmBilling.FREE_TIER,
            LlmModel.MISTRAL_SMALL_2603 to LlmBilling.METERED
        )
    }
}
