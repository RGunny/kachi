package me.rgunny.kachi.story.adapter.outbound.tei

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.exception.InferenceException
import me.rgunny.kachi.story.config.InferenceCircuitBreakerProperties
import me.rgunny.kachi.story.config.InferenceConfig
import me.rgunny.kachi.story.domain.inference.InferenceFailure
import me.rgunny.kachi.story.domain.inference.InferenceFailureCode
import me.rgunny.kachi.story.domain.inference.InferenceTarget
import me.rgunny.kachi.story.fake.FakeTeiClient
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("GuardedTeiClient")
class GuardedTeiClientTest {
    private val delegate = FakeTeiClient()

    @Test
    @DisplayName("일시 실패가 임계치에 닿으면 다음 호출을 차단한다")
    fun openAfterTransientFailures() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val client = guarded(circuitBreaker)
        repeat(2) { delegate.failures += failure(InferenceFailureCode.INFERENCE_TIMEOUT) }

        repeat(2) { assertFailsWith<InferenceException> { client.embed(TEXTS) } }
        val blocked = assertFailsWith<InferenceException> { client.embed(TEXTS) }

        assertEquals(InferenceFailureCode.INFERENCE_NOT_PERMITTED, blocked.failure.code)
        assertEquals(InferenceTarget.EMBEDDING, blocked.failure.target)
        assertEquals(2, delegate.embedCallCount)
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
        assertEquals("open", client.blockedReason())
    }

    @ParameterizedTest
    @EnumSource(
        value = InferenceFailureCode::class,
        names = ["INFERENCE_FAILED", "INFERENCE_OVERLOADED", "INFERENCE_UNHEALTHY", "INFERENCE_SERVER_ERROR", "INFERENCE_NETWORK_ERROR", "INFERENCE_UNKNOWN_ERROR"]
    )
    @DisplayName("서버 탓 일시 실패는 전부 회로를 여는 근거로 기록한다")
    fun recordEveryTransientFailure(code: InferenceFailureCode) = runBlocking {
        val circuitBreaker = circuitBreaker()
        val client = guarded(circuitBreaker)
        repeat(2) { delegate.failures += failure(code) }

        repeat(2) { assertFailsWith<InferenceException> { client.rerank("q", TEXTS) } }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
    }

    @ParameterizedTest
    @EnumSource(
        value = InferenceFailureCode::class,
        names = ["INFERENCE_INPUT_EMPTY", "INFERENCE_PAYLOAD_TOO_LARGE", "INFERENCE_INPUT_INVALID", "INFERENCE_INVALID_RESPONSE"]
    )
    @DisplayName("입력 탓과 응답 계약 위반은 연속돼도 회로를 열지 않는다")
    fun keepClosedOnNonTransientFailure(code: InferenceFailureCode) = runBlocking {
        val circuitBreaker = circuitBreaker()
        val client = guarded(circuitBreaker)
        repeat(5) { delegate.failures += failure(code) }

        repeat(5) { assertFailsWith<InferenceException> { client.embed(TEXTS) } }
        client.embed(TEXTS)

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
        assertEquals(6, delegate.embedCallCount)
    }

    @Test
    @DisplayName("차단된 호출은 회로 집계에 들어가지 않는다")
    fun blockedCallsAreNotCounted() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val client = guarded(circuitBreaker)
        openCircuit(client)
        val failedCalls = circuitBreaker.metrics.numberOfFailedCalls

        repeat(3) { assertFailsWith<InferenceException> { client.embed(TEXTS) } }

        assertEquals(3, circuitBreaker.metrics.numberOfNotPermittedCalls)
        assertEquals(failedCalls, circuitBreaker.metrics.numberOfFailedCalls)
    }

    @Test
    @DisplayName("coroutine 취소는 permission을 돌려주고 집계에 들어가지 않는다")
    fun cancellationIsNotCounted() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val client = guarded(circuitBreaker)
        delegate.failures += CancellationException("cancelled")

        assertFailsWith<CancellationException> { client.embed(TEXTS) }

        assertEquals(0, circuitBreaker.metrics.numberOfBufferedCalls)
        client.embed(TEXTS)
        assertEquals(2, delegate.embedCallCount)
    }

    @Test
    @DisplayName("추론 실패가 아닌 예외는 기록하지 않고 그대로 전파한다")
    fun propagateOtherException() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val client = guarded(circuitBreaker)
        repeat(5) { delegate.failures += IllegalStateException("boom") }

        repeat(5) { assertFailsWith<IllegalStateException> { client.embed(TEXTS) } }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    @DisplayName("실패 예외는 분류를 잃지 않고 그대로 전파된다")
    fun propagateSameFailureInstance() = runBlocking {
        val client = guarded(circuitBreaker())
        val thrown = failure(InferenceFailureCode.INFERENCE_TIMEOUT)
        delegate.failures += thrown

        val caught = assertFailsWith<InferenceException> { client.rerank("q", TEXTS) }

        assertSame(thrown, caught)
    }

    @Test
    @DisplayName("느린 호출 비율이 임계치를 넘으면 회로를 연다")
    fun openOnSlowCallRate() = runBlocking {
        val circuitBreaker = circuitBreaker(slowCallDurationThreshold = Duration.ofMillis(10), slowCallRateThreshold = 50f)
        val client = guarded(circuitBreaker)
        delegate.callDelay = Duration.ofMillis(30)

        repeat(2) { client.embed(TEXTS) }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
        assertEquals(2, circuitBreaker.metrics.numberOfSlowCalls)
    }

    @Test
    @DisplayName("half-open 호출이 성공하면 닫히고 일시 실패면 다시 열린다")
    fun halfOpenTransitions() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val client = guarded(circuitBreaker)
        openCircuit(client)

        circuitBreaker.transitionToHalfOpenState()
        client.embed(TEXTS)
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)

        openCircuit(client)
        circuitBreaker.transitionToHalfOpenState()
        delegate.failures += failure(InferenceFailureCode.INFERENCE_TIMEOUT)
        assertFailsWith<InferenceException> { client.embed(TEXTS) }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
    }

    @Test
    @DisplayName("회로가 열려 있어도 info는 위임 대상에 닿는다")
    fun infoBypassesCircuit() = runBlocking {
        val client = guarded(circuitBreaker())
        openCircuit(client)

        assertEquals("BAAI/bge-m3", client.info().model_id)
        assertEquals(1, delegate.infoCallCount)
    }

    @Test
    @DisplayName("되돌리면 열린 회로가 닫혀 호출이 다시 나간다")
    fun resetClosesCircuit() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val client = guarded(circuitBreaker)
        openCircuit(client)
        val callsBefore = delegate.embedCallCount

        client.reset()
        client.embed(TEXTS)

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertEquals(callsBefore + 1, delegate.embedCallCount)
        assertNull(client.blockedReason())
    }

    @Test
    @DisplayName("이미 닫혀 있는 회로를 되돌려도 실패하지 않는다")
    fun resetClosedCircuit() {
        val circuitBreaker = circuitBreaker()

        guarded(circuitBreaker).reset()

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    @DisplayName("강제로 열어 둔 회로도 호출을 차단하고 사유를 남긴다")
    fun blockWhenForcedOpen() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val client = guarded(circuitBreaker)
        circuitBreaker.transitionToForcedOpenState()

        val blocked = assertFailsWith<InferenceException> { client.embed(TEXTS) }

        assertEquals(InferenceFailureCode.INFERENCE_NOT_PERMITTED, blocked.failure.code)
        assertEquals("forced-open", client.blockedReason())
        assertEquals(0, delegate.embedCallCount)
    }

    @Test
    @DisplayName("상태 스냅샷은 대상·회로 상태·집계를 담는다")
    fun statusSnapshot() = runBlocking {
        val client = guarded(circuitBreaker(slidingWindowSize = 8, minimumNumberOfCalls = 8))
        client.embed(TEXTS)
        delegate.failures += failure(InferenceFailureCode.INFERENCE_TIMEOUT)
        assertFailsWith<InferenceException> { client.embed(TEXTS) }

        val status = client.status()

        assertEquals(InferenceTarget.EMBEDDING, status.target)
        assertEquals(CircuitBreaker.State.CLOSED.name, status.circuitBreakerState)
        assertEquals(1, status.successfulCalls)
        assertEquals(1, status.failedCalls)
        assertEquals(2, status.bufferedCalls)
        assertEquals(0, status.notPermittedCalls)
    }

    private suspend fun openCircuit(client: GuardedTeiClient) {
        repeat(2) { delegate.failures += failure(InferenceFailureCode.INFERENCE_TIMEOUT) }
        repeat(2) { assertFailsWith<InferenceException> { client.embed(TEXTS) } }
    }

    private fun guarded(circuitBreaker: CircuitBreaker): GuardedTeiClient {
        return GuardedTeiClient(delegate = delegate, target = InferenceTarget.EMBEDDING, circuitBreaker = circuitBreaker)
    }

    private fun failure(code: InferenceFailureCode): InferenceException {
        return InferenceException(InferenceFailure(code = code, target = InferenceTarget.EMBEDDING))
    }

    /** `InferenceConfig`로 서킷 브레이커를 만들되 창을 좁혀 두 번의 실패로 열리게 한다. */
    private fun circuitBreaker(
        slidingWindowSize: Int = 2,
        minimumNumberOfCalls: Int = 2,
        slowCallDurationThreshold: Duration = Duration.ofSeconds(8),
        slowCallRateThreshold: Float = 100f
    ): CircuitBreaker {
        val config = InferenceConfig().circuitBreakerConfig(
            InferenceCircuitBreakerProperties(
                slidingWindowSize = slidingWindowSize,
                minimumNumberOfCalls = minimumNumberOfCalls,
                failureRateThreshold = 50f,
                slowCallRateThreshold = slowCallRateThreshold,
                waitDurationInOpenState = Duration.ofHours(1),
                permittedNumberOfCallsInHalfOpenState = 1
            ),
            slowCallDurationThreshold = slowCallDurationThreshold
        )

        return CircuitBreaker.of("tei-test", config)
    }

    private companion object {
        val TEXTS = listOf("a", "b")
    }
}
