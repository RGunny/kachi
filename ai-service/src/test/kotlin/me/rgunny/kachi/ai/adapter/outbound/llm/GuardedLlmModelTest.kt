package me.rgunny.kachi.ai.adapter.outbound.llm

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.config.LlmCircuitBreakerConfig
import me.rgunny.kachi.ai.config.LlmCircuitBreakerProperties
import me.rgunny.kachi.ai.config.LlmCooldownProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.fake.NamedLlmProviderPort
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.MutableClock
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@DisplayName("GuardedLlmModel")
class GuardedLlmModelTest {
    private val delegate = NamedLlmProviderPort(MODEL.qualifiedCode)
    private val clock = MutableClock()

    @Test
    @DisplayName("재시도 가능한 실패가 임계치에 도달하면 다음 호출을 차단한다")
    fun openAfterRetryableFailures() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        repeat(2) { delegate.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT) }

        repeat(2) { assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) } }
        val blocked = assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, blocked.failure.code)
        assertEquals(MODEL.provider, blocked.failure.provider)
        assertEquals(2, delegate.summarizeCallCount)
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
    }

    @ParameterizedTest
    @EnumSource(value = LlmFailureCode::class, names = [
        "LLM_RATE_LIMITED",
        "LLM_TRANSIENT_ERROR",
        "LLM_NETWORK_ERROR"
    ])
    @DisplayName("rate limit·일시 오류·네트워크 실패도 회로를 여는 근거로 기록한다")
    fun recordEveryRetryableFailure(code: LlmFailureCode) = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        repeat(2) { delegate.failures += AiTestFixture.llmProviderException(code) }

        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }
        // rate limit은 cooldown까지 거는 실패다. 쉬는 동안에는 회로에 닿는 호출이 없다.
        clock.advance(DEFAULT_COOLDOWN)
        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
    }

    @Test
    @DisplayName("응답 계약 위반이 연속돼도 회로가 열리지 않는다")
    fun keepClosedOnInvalidResponse() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        repeat(5) { delegate.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_INVALID_RESPONSE) }

        repeat(5) { assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) } }
        provider.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertEquals(6, delegate.summarizeCallCount)
    }

    @ParameterizedTest
    @EnumSource(value = LlmFailureCode::class, names = ["LLM_CLIENT_ERROR", "LLM_AUTHORIZATION_ERROR"])
    @DisplayName("요청 검증 실패와 인증 실패는 회로를 열지 않는다")
    fun keepClosedOnValidationAndAuthorizationFailure(code: LlmFailureCode) = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        repeat(5) { delegate.failures += AiTestFixture.llmProviderException(code) }

        repeat(5) { assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) } }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    @DisplayName("LLM 실패가 아닌 예외는 기록하지 않고 그대로 전파한다")
    fun propagateNonLlmException() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        repeat(5) { delegate.failures += IllegalStateException("boom") }

        repeat(5) {
            assertFailsWith<IllegalStateException> { provider.summarizeNews(KEYWORD, ARTICLES) }
        }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    @DisplayName("성공하면 위임 대상의 결과를 그대로 돌려준다")
    fun returnDelegateResult() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)

        val result = provider.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(MODEL.qualifiedCode, result.metadata.model)
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    @DisplayName("실패율이 임계치 미만이면 열리지 않는다")
    fun keepClosedBelowFailureRateThreshold() = runBlocking {
        val circuitBreaker = circuitBreaker(slidingWindowSize = 4, minimumNumberOfCalls = 4)
        val provider = guarded(circuitBreaker = circuitBreaker)
        delegate.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)

        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }
        repeat(3) { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    @DisplayName("차단된 호출은 회로 집계에 들어가지 않는다")
    fun blockedCallsAreNotCounted() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        repeat(2) { delegate.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT) }
        repeat(2) { assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) } }
        val failedCalls = circuitBreaker.metrics.numberOfFailedCalls

        repeat(3) { assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) } }

        assertEquals(3, circuitBreaker.metrics.numberOfNotPermittedCalls)
        assertEquals(failedCalls, circuitBreaker.metrics.numberOfFailedCalls)
    }

    @Test
    @DisplayName("half-open에서는 허용한 수만큼만 호출을 통과시킨다")
    fun permitOnlyConfiguredProbes() = runBlocking {
        val circuitBreaker = circuitBreaker(permittedNumberOfCallsInHalfOpenState = 2)
        val provider = guarded(circuitBreaker = circuitBreaker)
        openCircuit(provider)
        circuitBreaker.transitionToHalfOpenState()
        repeat(2) { delegate.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT) }
        val callsBeforeProbe = delegate.summarizeCallCount

        repeat(2) { assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) } }
        val blocked = assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(2, delegate.summarizeCallCount - callsBeforeProbe)
        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, blocked.failure.code)
    }

    @Test
    @DisplayName("half-open 허용 수를 넘어 동시에 들어온 호출은 사유 없이 차단된다")
    fun blockConcurrentProbeBeyondPermittedCalls() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        openCircuit(provider)
        circuitBreaker.transitionToHalfOpenState()
        val gate = CompletableDeferred<Unit>()
        delegate.gate = gate

        val callsBefore = delegate.summarizeCallCount

        val probe = async { provider.summarizeNews(KEYWORD, ARTICLES) }
        while (delegate.summarizeCallCount == callsBefore) {
            yield()
        }
        val blocked = assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }
        gate.complete(Unit)
        probe.await()

        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, blocked.failure.code)
        assertEquals(1, delegate.summarizeCallCount - callsBefore)
    }

    @Test
    @DisplayName("half-open 호출이 성공하면 회로가 닫힌다")
    fun closeAfterSuccessfulProbe() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        openCircuit(provider)
        circuitBreaker.transitionToHalfOpenState()

        provider.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    @DisplayName("half-open 호출이 재시도 가능한 실패면 회로가 다시 열린다")
    fun reopenAfterFailedProbe() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        openCircuit(provider)
        circuitBreaker.transitionToHalfOpenState()
        delegate.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)

        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
    }

    @Test
    @DisplayName("half-open 호출이 키워드 귀속 실패면 provider는 살아 있다고 보고 닫는다")
    fun closeWhenProbeFailsWithKeywordBoundFailure() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        openCircuit(provider)
        circuitBreaker.transitionToHalfOpenState()
        delegate.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_INVALID_RESPONSE)

        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    @DisplayName("rate limit이 지시한 시간 동안 호출하지 않는다")
    fun holdCallsDuringRetryAfter() = runBlocking {
        val provider = guarded(circuitBreaker = wideCircuitBreaker())
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = 30_000)

        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }
        assertBlocked(provider, expectedCalls = 1)

        clock.advance(Duration.ofSeconds(29))
        assertBlocked(provider, expectedCalls = 1)

        clock.advance(Duration.ofSeconds(1))
        provider.summarizeNews(KEYWORD, ARTICLES)
        assertEquals(2, delegate.summarizeCallCount)
    }

    @Test
    @DisplayName("Retry-After가 없는 rate limit은 기본 cooldown을 적용한다")
    fun holdCallsWithDefaultCooldown() = runBlocking {
        val provider = guarded(circuitBreaker = wideCircuitBreaker())
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = null)

        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        clock.advance(DEFAULT_COOLDOWN.minusSeconds(1))
        assertBlocked(provider, expectedCalls = 1)

        clock.advance(Duration.ofSeconds(1))
        provider.summarizeNews(KEYWORD, ARTICLES)
        assertEquals(2, delegate.summarizeCallCount)
    }

    @Test
    @DisplayName("Retry-After가 상한을 넘으면 상한까지만 기다린다")
    fun capCooldownAtMaximum() = runBlocking {
        val provider = guarded(circuitBreaker = wideCircuitBreaker())
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = MAX_COOLDOWN.plusHours(1).toMillis())

        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        clock.advance(MAX_COOLDOWN.minusSeconds(1))
        assertBlocked(provider, expectedCalls = 1)

        clock.advance(Duration.ofSeconds(1))
        provider.summarizeNews(KEYWORD, ARTICLES)
        assertEquals(2, delegate.summarizeCallCount)
    }

    @Test
    @DisplayName("cooldown 차단은 회로 집계에 들어가지 않는다")
    fun cooldownBlockIsNotRecorded() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = 30_000)
        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }
        val bufferedCalls = circuitBreaker.metrics.numberOfBufferedCalls

        repeat(3) { assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) } }

        assertEquals(bufferedCalls, circuitBreaker.metrics.numberOfBufferedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfNotPermittedCalls)
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }

    @Test
    @DisplayName("cooldown이 끝나면 다시 정상 호출된다")
    fun resumeAfterCooldown() = runBlocking {
        val provider = guarded(circuitBreaker = wideCircuitBreaker())
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = 30_000)
        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        clock.advance(Duration.ofSeconds(30))
        provider.summarizeNews(KEYWORD, ARTICLES)
        provider.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(3, delegate.summarizeCallCount)
    }

    @Test
    @DisplayName("rate limit이 아닌 실패는 cooldown을 걸지 않는다")
    fun doNotHoldOnOtherFailures() = runBlocking {
        val provider = guarded(circuitBreaker = wideCircuitBreaker())
        delegate.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)

        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }
        provider.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(2, delegate.summarizeCallCount)
    }

    @Test
    @DisplayName("동시에 들어온 rate limit은 더 늦은 시각으로만 cooldown을 갱신한다")
    fun extendCooldownOnlyForward() = runBlocking {
        val provider = guarded(circuitBreaker = wideCircuitBreaker())
        val gate = CompletableDeferred<Unit>()
        delegate.gate = gate
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = 60_000)
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = 10_000)

        val first = async { runCatching { provider.summarizeNews(KEYWORD, ARTICLES) } }
        val second = async { runCatching { provider.summarizeNews(KEYWORD, ARTICLES) } }
        while (delegate.summarizeCallCount < 2) {
            yield()
        }
        gate.complete(Unit)
        first.await()
        second.await()

        clock.advance(Duration.ofSeconds(59))
        assertEquals(
            "cooldown(until=${AiTestFixture.NOW.plusSeconds(60)})",
            provider.exclusionReason(clock.instant())
        )
    }

    @Test
    @DisplayName("회로가 닫혀 있고 쉬는 중이 아니면 호출 가능하다고 본다")
    fun availableWhenClosed() {
        assertTrue(guarded().isLikelyAvailable(clock.instant()))
    }

    @Test
    @DisplayName("회로가 열려 있으면 호출 가능하지 않다고 본다")
    fun unavailableWhenOpen() = runBlocking {
        val provider = guarded()
        openCircuit(provider)

        assertFalse(provider.isLikelyAvailable(clock.instant()))
    }

    @Test
    @DisplayName("쉬는 중이면 호출 가능하지 않다고 보고 끝나면 다시 가능하다고 본다")
    fun unavailableWhileCoolingDown() = runBlocking {
        val provider = guarded(circuitBreaker = wideCircuitBreaker())
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = 30_000)
        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertFalse(provider.isLikelyAvailable(clock.instant()))

        clock.advance(Duration.ofSeconds(30))
        assertTrue(provider.isLikelyAvailable(clock.instant()))
    }

    @Test
    @DisplayName("half-open은 호출 가능하다고 본다")
    fun availableWhenHalfOpen() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        openCircuit(provider)
        circuitBreaker.transitionToHalfOpenState()

        assertTrue(provider.isLikelyAvailable(clock.instant()))
    }

    @Test
    @DisplayName("차단 실패는 자기 provider 이름을 가진 UNAVAILABLE이다")
    fun unavailableFailureShape() = runBlocking {
        val provider = guarded()
        openCircuit(provider)

        val blocked = assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, blocked.failure.code)
        assertEquals(MODEL.provider, blocked.failure.provider)
        assertNull(blocked.failure.statusCode)
        assertNull(blocked.failure.retryAfterMillis)
    }

    @Test
    @DisplayName("키워드 확장도 같은 차단을 받는다")
    fun expandKeywordIsGuarded() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        repeat(2) { delegate.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT) }

        repeat(2) { assertFailsWith<LlmProviderException> { provider.expandKeyword(KEYWORD, 3) } }
        val blocked = assertFailsWith<LlmProviderException> { provider.expandKeyword(KEYWORD, 3) }

        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, blocked.failure.code)
        assertEquals(2, delegate.expandCallCount)
    }

    @Test
    @DisplayName("실패 예외는 분류를 잃지 않고 그대로 전파된다")
    fun propagateSameFailureInstance() = runBlocking {
        val provider = guarded()
        val thrown = AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)
        delegate.failures += thrown

        val caught = assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertSame(thrown, caught)
    }

    @Test
    @DisplayName("coroutine 취소는 회로 집계에 들어가지 않는다")
    fun cancellationIsNotCounted() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        delegate.failures += CancellationException("cancelled")

        assertFailsWith<CancellationException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(0, circuitBreaker.metrics.numberOfSuccessfulCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfFailedCalls)
        assertEquals(0, circuitBreaker.metrics.numberOfBufferedCalls)

        provider.summarizeNews(KEYWORD, ARTICLES)
        assertEquals(2, delegate.summarizeCallCount)
    }

    @Test
    @DisplayName("요약 준비의 plan은 위임 대상의 plan이다")
    fun prepareReturnsDelegatePlan() {
        val provider = guarded()

        assertEquals(delegate.prepareNewsSummary().plan, provider.prepareNewsSummary().plan)
    }

    @Test
    @DisplayName("회로가 열려 있어도 요약 준비는 실패하지 않는다")
    fun prepareDoesNotThrowWhenOpen() = runBlocking {
        val provider = guarded()
        openCircuit(provider)

        assertEquals(MODEL.provider, provider.prepareNewsSummary().plan.provider)
    }

    @Test
    @DisplayName("요약 준비가 돌려준 실행 단위도 같은 차단을 받는다")
    fun preparedSummaryIsGuarded() = runBlocking {
        val provider = guarded()
        val prepared = provider.prepareNewsSummary()
        openCircuit(provider)
        val callsBefore = delegate.summarizeCallCount

        val blocked = assertFailsWith<LlmProviderException> { prepared.summarize(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, blocked.failure.code)
        assertEquals(callsBefore, delegate.summarizeCallCount)
    }

    @Test
    @DisplayName("차단이 없으면 요약 준비가 돌려준 실행 단위가 위임 대상의 결과를 준다")
    fun preparedSummaryDelegatesWhenNotBlocked() = runBlocking {
        val provider = guarded()

        val result = provider.prepareNewsSummary().summarize(KEYWORD, ARTICLES)

        assertEquals(MODEL.qualifiedCode, result.metadata.model)
        assertEquals(1, delegate.summarizeCallCount)
    }

    @Test
    @DisplayName("강제로 열어 둔 회로도 호출을 차단하고 사유를 남긴다")
    fun blockWhenCircuitIsForcedOpen() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        circuitBreaker.transitionToForcedOpenState()

        val blocked = assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, blocked.failure.code)
        assertEquals(0, delegate.summarizeCallCount)
        assertEquals("forced-open", provider.exclusionReason(clock.instant()))
        assertFalse(provider.isLikelyAvailable(clock.instant()))
    }

    @Test
    @DisplayName("회로 상태 전이에 listener가 등록돼 있다")
    fun registerStateTransitionListener() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        val transitions = mutableListOf<Pair<CircuitBreaker.State, CircuitBreaker.State>>()
        circuitBreaker.eventPublisher.onStateTransition {
            transitions += it.stateTransition.fromState to it.stateTransition.toState
        }

        openCircuit(provider)

        assertEquals(listOf(CircuitBreaker.State.CLOSED to CircuitBreaker.State.OPEN), transitions)
    }

    @Test
    @DisplayName("제외 사유는 상태별 문자열을 준다")
    fun exclusionReasonPerState() = runBlocking {
        val provider = guarded(circuitBreaker = wideCircuitBreaker())

        assertNull(provider.exclusionReason(clock.instant()))

        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = 30_000)
        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }
        assertEquals(
            "cooldown(until=${AiTestFixture.NOW.plusSeconds(30)})",
            provider.exclusionReason(clock.instant())
        )

        clock.advance(Duration.ofSeconds(30))
        val openDelegate = NamedLlmProviderPort(MODEL.qualifiedCode)
        val opened = guarded(delegate = openDelegate)
        openCircuit(opened, openDelegate)
        assertEquals("open", opened.exclusionReason(clock.instant()))
    }

    @Test
    @DisplayName("느린 호출 비율이 임계치를 넘으면 회로를 연다")
    fun openOnSlowCallRate() = runBlocking {
        val circuitBreaker = circuitBreaker(
            slowCallDurationThreshold = Duration.ofMillis(10),
            slowCallRateThreshold = 50f
        )
        val provider = guarded(circuitBreaker = circuitBreaker)
        delegate.callDelay = Duration.ofMillis(30)

        repeat(2) { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
        assertEquals(2, circuitBreaker.metrics.numberOfSlowCalls)
    }

    @Test
    @DisplayName("느린 호출 비율 임계치가 100이면 모든 호출이 느릴 때만 연다")
    fun keepClosedUntilEveryCallIsSlow() = runBlocking {
        val circuitBreaker = circuitBreaker(
            slowCallDurationThreshold = Duration.ofMillis(10),
            slowCallRateThreshold = 100f
        )
        val provider = guarded(circuitBreaker = circuitBreaker)

        delegate.callDelay = Duration.ofMillis(30)
        provider.summarizeNews(KEYWORD, ARTICLES)
        delegate.callDelay = Duration.ZERO
        provider.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertEquals(1, circuitBreaker.metrics.numberOfSlowCalls)
    }

    @Test
    @DisplayName("느린 호출도 결과는 그대로 돌려준다")
    fun slowCallStillReturnsResult() = runBlocking {
        val circuitBreaker = circuitBreaker(
            slowCallDurationThreshold = Duration.ofMillis(10),
            slowCallRateThreshold = 50f
        )
        val provider = guarded(circuitBreaker = circuitBreaker)
        delegate.callDelay = Duration.ofMillis(30)

        val result = provider.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(MODEL.qualifiedCode, result.metadata.model)
    }

    @Test
    @DisplayName("상태 스냅샷은 회로 상태와 쉬는 시각, 집계를 함께 담는다")
    fun statusSnapshot() = runBlocking {
        val provider = guarded(circuitBreaker = wideCircuitBreaker())
        provider.summarizeNews(KEYWORD, ARTICLES)
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = 30_000)
        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        val status = provider.status(clock.instant())

        assertEquals(MODEL, status.model)
        assertEquals(CircuitBreaker.State.CLOSED.name, status.circuitBreakerState)
        assertEquals(AiTestFixture.NOW.plusSeconds(30), status.cooldownUntil)
        assertEquals(1, status.failedCalls)
        assertEquals(1, status.successfulCalls)
        assertEquals(2, status.bufferedCalls)
    }

    @Test
    @DisplayName("쉬는 시간이 지나면 상태 스냅샷에서 쉬는 시각이 빠진다")
    fun statusDropsExpiredCooldown() = runBlocking {
        val provider = guarded(circuitBreaker = wideCircuitBreaker())
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = 30_000)
        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        clock.advance(Duration.ofSeconds(30))

        assertNull(provider.status(clock.instant()).cooldownUntil)
    }

    @Test
    @DisplayName("되돌리면 열린 회로가 닫히고 쉬는 시각도 지워져 호출이 다시 나간다")
    fun resetClearsBothGuards() = runBlocking {
        val circuitBreaker = circuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        // 마지막 실패를 rate limit으로 두면 회로가 열리는 시점에 cooldown도 함께 걸린다.
        delegate.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT)
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = 30_000)
        repeat(2) { assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) } }
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)
        val callsBefore = delegate.summarizeCallCount

        provider.reset()
        provider.summarizeNews(KEYWORD, ARTICLES)

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertNull(provider.status(clock.instant()).cooldownUntil)
        assertEquals(callsBefore + 1, delegate.summarizeCallCount)
    }

    @Test
    @DisplayName("이미 닫혀 있는 회로를 되돌려도 실패하지 않고 쉬는 시각만 지운다")
    fun resetClosedCircuit() = runBlocking {
        val circuitBreaker = wideCircuitBreaker()
        val provider = guarded(circuitBreaker = circuitBreaker)
        delegate.failures += AiTestFixture.rateLimitedException(retryAfterMillis = 30_000)
        assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        provider.reset()

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertNull(provider.exclusionReason(clock.instant()))
    }

    private suspend fun openCircuit(
        provider: GuardedLlmModel,
        delegate: NamedLlmProviderPort = this.delegate
    ) {
        repeat(2) { delegate.failures += AiTestFixture.llmProviderException(LlmFailureCode.LLM_TIMEOUT) }
        repeat(2) { assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) } }
    }

    /** 실패율로는 열리지 않는 회로. cooldown만 검증하는 테스트가 회로 상태에 흔들리지 않게 한다. */
    private fun wideCircuitBreaker(): CircuitBreaker =
        circuitBreaker(slidingWindowSize = 8, minimumNumberOfCalls = 8)

    private suspend fun assertBlocked(provider: GuardedLlmModel, expectedCalls: Int) {
        val blocked = assertFailsWith<LlmProviderException> { provider.summarizeNews(KEYWORD, ARTICLES) }

        assertEquals(LlmFailureCode.LLM_PROVIDER_UNAVAILABLE, blocked.failure.code)
        assertEquals(expectedCalls, delegate.summarizeCallCount)
    }

    private fun guarded(
        delegate: NamedLlmProviderPort = this.delegate,
        circuitBreaker: CircuitBreaker = circuitBreaker()
    ): GuardedLlmModel {
        return GuardedLlmModel(
            delegate = delegate,
            model = MODEL,
            circuitBreaker = circuitBreaker,
            cooldown = LlmCooldownProperties(default = DEFAULT_COOLDOWN, max = MAX_COOLDOWN),
            clock = clock
        )
    }

    /**
     * 실제 운영 설정 변환기를 그대로 쓰되 창을 좁혀 두 번의 실패로 열리게 한다.
     * open 대기는 길게 잡아 시간이 아니라 명시적 전이로만 half-open이 되게 한다.
     */
    private fun circuitBreaker(
        slidingWindowSize: Int = 2,
        minimumNumberOfCalls: Int = 2,
        slowCallDurationThreshold: Duration = Duration.ofSeconds(8),
        slowCallRateThreshold: Float = 100f,
        permittedNumberOfCallsInHalfOpenState: Int = 1
    ): CircuitBreaker {
        val config = LlmCircuitBreakerConfig().circuitBreakerConfig(
            LlmCircuitBreakerProperties(
                slidingWindowSize = slidingWindowSize,
                minimumNumberOfCalls = minimumNumberOfCalls,
                failureRateThreshold = 50f,
                slowCallRateThreshold = slowCallRateThreshold,
                waitDurationInOpenState = Duration.ofHours(1),
                permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState
            ),
            slowCallDurationThreshold = slowCallDurationThreshold
        )

        return CircuitBreakerRegistry.of(config).circuitBreaker(MODEL.qualifiedCode)
    }

    private companion object {
        val MODEL: LlmModel = AiTestFixture.LLM_MODEL
        val KEYWORD: AiKeyword = AiTestFixture.keyword()
        val ARTICLES = listOf(AiTestFixture.newsArticle())
        val DEFAULT_COOLDOWN: Duration = Duration.ofSeconds(60)
        val MAX_COOLDOWN: Duration = Duration.ofMinutes(10)
    }
}
