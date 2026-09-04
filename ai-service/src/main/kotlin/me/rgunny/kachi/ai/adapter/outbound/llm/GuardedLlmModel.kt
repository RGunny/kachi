package me.rgunny.kachi.ai.adapter.outbound.llm

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.config.LlmCooldownProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailure
import me.rgunny.kachi.ai.domain.llm.LlmFailureCategory
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * 모델 하나의 호출 가능 여부를 관리하는 데코레이터.
 *
 * [LlmProviderPort] 구현을 같은 포트로 감싸는 데코레이터 패턴이다. 위임 대상은 자기가 차단될 수 있다는 사실을 모르고,
 * 라우터는 후보가 감싸였는지 모른다. 차단 장치를 더하거나 상태 저장소를 바꿔도 이 클래스 안에서 끝난다.
 *
 * 차단 장치는 둘이다. 연속 실패로 열리는 서킷 브레이커와, rate limit 응답이 지시한 cooldown이다.
 * 하나라도 걸리면 위임 대상을 호출하지 않고 [LlmFailureCode.LLM_PROVIDER_UNAVAILABLE]로 실패한다.
 *
 * 실패 분류는 위임 대상이 끝냈으므로 여기서 다시 하지 않고 [LlmFailure]를 그대로 소비한다.
 * 재시도도 하지 않는다. 재시도 구동은 다음 tick의 몫이다(ADR 021).
 */
class GuardedLlmModel(
    private val delegate: LlmProviderPort,
    override val model: LlmModel,
    private val circuitBreaker: CircuitBreaker,
    private val cooldown: LlmCooldownProperties,
    private val clock: Clock
) : LlmProviderCandidate {

    /** scheduler와 internal API가 동시에 들어올 수 있어 갱신을 원자적으로 처리한다. */
    private val cooldownUntil = AtomicReference<Instant?>(null)

    init {
        // 상태 전이를 남기지 않으면 특정 모델이 한동안 호출되지 않은 이유를 운영자가 알 수 없다.
        circuitBreaker.eventPublisher.onStateTransition { event ->
            val transition = "${event.stateTransition.fromState} -> ${event.stateTransition.toState}"

            if (event.stateTransition.toState == CircuitBreaker.State.OPEN) {
                log.warn("LLM model circuit breaker opened: model={}, transition={}", model.qualifiedCode, transition)
            } else {
                log.info("LLM model circuit breaker changed: model={}, transition={}", model.qualifiedCode, transition)
            }
        }
    }

    /**
     * 위임 대상의 실행 단위를 그대로 넘기면 그 객체의 호출이 차단을 우회하므로 감싼 것으로 바꿔 돌려준다.
     */
    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        return GuardedPreparedNewsSummary(plan = delegate.prepareNewsSummary().plan, model = this)
    }

    override suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult {
        return guarded { delegate.expandKeyword(keyword, maxExpansions) }
    }

    override suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        return guarded { delegate.summarizeNews(keyword, articles) }
    }

    /** 실제 차단은 호출 시점의 permission이 결정하므로 이 값이 틀려도 호출이 잘못 나가지 않는다. */
    override fun isLikelyAvailable(now: Instant): Boolean = exclusionReason(now) == null

    /**
     * 지금 이 모델이 어떤 상태인지의 스냅샷.
     *
     * cooldown 종료 시각은 쉬는 중일 때만 담는다. 지나간 시각은 호출을 막는 이유가 아니다.
     */
    fun status(now: Instant): LlmModelStatus {
        val metrics = circuitBreaker.metrics

        return LlmModelStatus(
            model = model,
            circuitBreakerState = circuitBreaker.state.name,
            cooldownUntil = coolingDownUntil(now),
            failureRate = metrics.failureRate,
            slowCallRate = metrics.slowCallRate,
            bufferedCalls = metrics.numberOfBufferedCalls,
            successfulCalls = metrics.numberOfSuccessfulCalls,
            failedCalls = metrics.numberOfFailedCalls,
            notPermittedCalls = metrics.numberOfNotPermittedCalls
        )
    }

    /**
     * 운영자가 원인 해소를 확인한 뒤 두 차단 장치를 함께 푼다.
     *
     * 이미 닫혀 있는 회로에 전이를 요청하면 라이브러리가 거부하므로 상태를 보고 건너뛴다.
     * cooldown은 회로와 별개라 그때도 지운다.
     */
    fun reset() {
        if (circuitBreaker.state != CircuitBreaker.State.CLOSED) {
            circuitBreaker.transitionToClosedState()
        }
        cooldownUntil.set(null)
    }

    /** 후보에서 빠질 이유. 빠질 이유가 없으면 null이다. */
    fun exclusionReason(now: Instant): String? {
        coolingDownUntil(now)?.let { return cooldownReason(it) }

        return when (circuitBreaker.state) {
            CircuitBreaker.State.OPEN -> "open"
            CircuitBreaker.State.FORCED_OPEN -> "forced-open"
            else -> null
        }
    }

    /**
     * 호출되지 못한 이유. 상태만으로 특정할 수 없으면 permission 거부로 본다.
     *
     * half-open에서 probe 허용 수를 넘긴 호출이 상태로 특정되지 않는 경우다.
     */
    override fun blockedReason(now: Instant): String = exclusionReason(now) ?: NOT_PERMITTED

    /**
     * 호출을 서킷 브레이커 집계와 cooldown 판정으로 감싼다.
     *
     * 취소는 성공도 실패도 아니라서 permission을 돌려주고 집계에서 뺀다. 라이브러리 wrapper는 취소를
     * 성공으로 세고 그 동작에 끼어들 지점이 없어, 상태 전이 API를 직접 호출한다.
     */
    private suspend fun <T : Any> guarded(call: suspend () -> T): T {
        val now = Instant.now(clock)

        // 1. 쉬는 중인 모델은 회로에 닿기 전에 막는다. 이 차단은 집계에 넣지 않는다.
        coolingDownUntil(now)?.let { throw unavailable(cooldownReason(it)) }

        // 2. permission 획득이 곧 OPEN에서 HALF_OPEN으로 넘어가는 계기다. 상태만 읽어서는 전이가 없다.
        if (!circuitBreaker.tryAcquirePermission()) {
            throw unavailable(blockedReason(Instant.now(clock)))
        }

        // 3. 호출하고 걸린 시간과 함께 결과를 집계한다.
        val start = circuitBreaker.currentTimestamp
        val timestampUnit = circuitBreaker.timestampUnit

        return try {
            val result = call()
            circuitBreaker.onResult(circuitBreaker.currentTimestamp - start, timestampUnit, result)
            result
        } catch (cancellation: CancellationException) {
            circuitBreaker.releasePermission()
            throw cancellation
        } catch (failure: LlmProviderException) {
            circuitBreaker.onError(circuitBreaker.currentTimestamp - start, timestampUnit, failure)
            holdIfRateLimited(failure.failure)
            throw failure
        } catch (error: Throwable) {
            circuitBreaker.onError(circuitBreaker.currentTimestamp - start, timestampUnit, error)
            throw error
        }
    }

    private fun coolingDownUntil(now: Instant): Instant? = cooldownUntil.get()?.takeIf { now < it }

    private fun cooldownReason(until: Instant): String = "cooldown(until=$until)"

    /**
     * rate limit 응답을 받으면 제공자가 지시한 시간만큼 쉰다.
     *
     * 헤더가 없어도 기본값만큼은 쉰다. 0으로 두면 같은 실행에서 같은 모델을 다시 골라 같은 응답을 받는다.
     * 이미 쉬는 중이면 더 늦은 시각만 반영한다. 짧은 지시로 대기를 앞당기면 앞선 지시를 어기는 것이 된다.
     */
    private fun holdIfRateLimited(failure: LlmFailure) {
        if (failure.category != LlmFailureCategory.RATE_LIMITED) {
            return
        }

        val duration = failure.retryAfterMillis
            ?.let { minOf(Duration.ofMillis(it), cooldown.max) }
            ?: cooldown.default
        val until = Instant.now(clock).plus(duration)
        val applied = cooldownUntil.updateAndGet { current ->
            if (current == null || until.isAfter(current)) until else current
        }

        log.warn(
            "LLM model is cooling down after rate limit: model={}, until={}, source={}",
            model.qualifiedCode,
            applied,
            if (failure.retryAfterMillis == null) "default" else "retry-after"
        )
    }

    private fun unavailable(reason: String): LlmProviderException {
        return LlmProviderException(
            LlmFailure(
                code = LlmFailureCode.LLM_PROVIDER_UNAVAILABLE,
                provider = model.provider,
                message = "${LlmFailureCode.LLM_PROVIDER_UNAVAILABLE.defaultMessage}: $reason"
            )
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(GuardedLlmModel::class.java)
        const val NOT_PERMITTED = "not-permitted"
    }
}
