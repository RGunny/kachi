package me.rgunny.kachi.ai.adapter.outbound.llm

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmHold
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmModelStatus
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.config.LlmCooldownProperties
import me.rgunny.kachi.ai.config.LlmHoldProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmBilling
import me.rgunny.kachi.ai.domain.llm.LlmFailure
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
 * 차단 장치는 넷이다. 일시 실패의 비율로 열리는 서킷 브레이커, rate limit 응답이 지시한 cooldown,
 * 모델이 없다는 응답(404)에 거는 모델 hold, 계정 문제(401·402·403)에 거는 제공자 hold다.
 * 제공자 hold는 같은 제공자의 가드들이 [providerHolds]로 공유한다. 한 모델에서 받은 계정 문제는 다른 모델에서도 같기 때문이다.
 * 하나라도 걸리면 위임 대상을 호출하지 않고 [LlmFailureCode.LLM_NOT_PERMITTED]로 실패한다.
 *
 * hold는 서킷과 다른 상태다. 서킷은 표본으로 열리고 대기 뒤 탐색하지만 hold는 한 건으로 확정이다.
 * 대기 시각이 지나면 호출이 한 번 통과하고, 같은 실패면 다시 걸린다. 그래서 별도의 탐색 상태가 없다.
 *
 * 실패 분류는 위임 대상이 끝냈으므로 여기서 다시 하지 않고 [LlmFailure]를 그대로 소비한다.
 * 재시도도 하지 않는다. 재시도 구동은 다음 tick의 몫이다(ADR 021).
 *
 * [billing]은 차단과 무관하지만 상태 스냅샷에 실린다. 운영자가 차단을 해제하거나 실제 호출을 확인할 때 비용을 알아야 한다.
 */
class GuardedLlmModel(
    private val delegate: LlmProviderPort,
    override val model: LlmModel,
    val billing: LlmBilling,
    private val circuitBreaker: CircuitBreaker,
    private val cooldown: LlmCooldownProperties,
    private val hold: LlmHoldProperties,
    private val providerHolds: ProviderHoldRegistry,
    private val clock: Clock
) : LlmProviderCandidate {

    /** scheduler와 internal API가 동시에 들어올 수 있어 갱신을 원자적으로 처리한다. */
    private val cooldownUntil = AtomicReference<Instant?>(null)
    private val modelHold = AtomicReference<LlmHold?>(null)

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
     * cooldown 종료 시각과 hold는 걸려 있을 때만 담는다. 지나간 시각은 호출을 막는 이유가 아니다.
     */
    fun status(now: Instant): LlmModelStatus {
        val metrics = circuitBreaker.metrics

        return LlmModelStatus(
            model = model,
            billing = billing,
            circuitBreakerState = circuitBreaker.state.name,
            cooldownUntil = coolingDownUntil(now),
            hold = activeHold(now),
            failureRate = metrics.failureRate,
            slowCallRate = metrics.slowCallRate,
            bufferedCalls = metrics.numberOfBufferedCalls,
            successfulCalls = metrics.numberOfSuccessfulCalls,
            failedCalls = metrics.numberOfFailedCalls,
            notPermittedCalls = metrics.numberOfNotPermittedCalls
        )
    }

    /**
     * 운영자가 원인 해소를 확인한 뒤 차단 장치를 전부 푼다.
     * 제공자 hold도 함께 푼다. reset은 운영자가 원인 해소를 확인했다는 뜻이기 때문이다.
     *
     * 이미 닫혀 있는 회로에 전이를 요청하면 라이브러리가 거부하므로 상태를 보고 건너뛴다.
     * cooldown과 hold는 회로와 별개라 그때도 지운다.
     */
    fun reset() {
        if (circuitBreaker.state != CircuitBreaker.State.CLOSED) {
            circuitBreaker.transitionToClosedState()
        }
        cooldownUntil.set(null)
        modelHold.set(null)
        providerHolds.release(model.provider)
    }

    /** 후보에서 빠질 이유. 빠질 이유가 없으면 null이다. */
    fun exclusionReason(now: Instant): String? {
        providerHolds.holdOf(model.provider, now)?.let { return providerHoldReason(it) }
        activeModelHold(now)?.let { return modelHoldReason(it) }
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
     * 호출을 hold·cooldown 판정과 서킷 브레이커 집계로 감싼다.
     *
     * 취소는 성공도 실패도 아니라서 permission을 돌려주고 집계에서 뺀다. 라이브러리 wrapper는 취소를
     * 성공으로 세고 그 동작에 끼어들 지점이 없어, 상태 전이 API를 직접 호출한다.
     */
    private suspend fun <T : Any> guarded(call: suspend () -> T): T {
        val now = Instant.now(clock)

        // 1. hold와 cooldown은 회로에 닿기 전에 막는다. 이 차단은 집계에 넣지 않는다.
        providerHolds.holdOf(model.provider, now)?.let { throw notPermitted(providerHoldReason(it)) }
        activeModelHold(now)?.let { throw notPermitted(modelHoldReason(it)) }
        coolingDownUntil(now)?.let { throw notPermitted(cooldownReason(it)) }

        // 2. permission 획득이 곧 OPEN에서 HALF_OPEN으로 넘어가는 계기다. 상태만 읽어서는 전이가 없다.
        if (!circuitBreaker.tryAcquirePermission()) {
            throw notPermitted(blockedReason(Instant.now(clock)))
        }

        // 3. 호출하고 걸린 시간과 함께 결과를 집계한다. 무엇을 실패로 셀지는 회로 설정의 predicate가 가른다.
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
            holdIfBroken(failure.failure)
            throw failure
        } catch (error: Throwable) {
            circuitBreaker.onError(circuitBreaker.currentTimestamp - start, timestampUnit, error)
            throw error
        }
    }

    private fun coolingDownUntil(now: Instant): Instant? = cooldownUntil.get()?.takeIf { now < it }

    private fun activeModelHold(now: Instant): LlmHold? = modelHold.get()?.takeIf { it.isActive(now) }

    private fun activeHold(now: Instant): LlmHold? = providerHolds.holdOf(model.provider, now) ?: activeModelHold(now)

    private fun cooldownReason(until: Instant): String = "cooldown(until=$until)"

    private fun modelHoldReason(hold: LlmHold): String = "model-hold(code=${hold.code.code}, until=${hold.until})"

    private fun providerHoldReason(hold: LlmHold): String = "provider-hold(code=${hold.code.code}, until=${hold.until})"

    /**
     * rate limit 응답을 받으면 제공자가 지시한 시간만큼 쉰다.
     *
     * 헤더가 없어도 기본값만큼은 쉰다. 0으로 두면 같은 실행에서 같은 모델을 다시 골라 같은 응답을 받는다.
     * 이미 쉬는 중이면 더 늦은 시각만 반영한다. 짧은 지시로 대기를 앞당기면 앞선 지시를 어기는 것이 된다.
     */
    private fun holdIfRateLimited(failure: LlmFailure) {
        if (failure.code != LlmFailureCode.LLM_RATE_LIMITED) {
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

    /**
     * 한 건으로 확정되는 구성상 장애는 재탐색 시각까지 후보에서 뺀다.
     *
     * 모델 탓이면 이 모델만, 제공자 탓이면 그 제공자의 모든 모델을 뺀다. 갱신은 더 늦은 시각만 반영한다.
     */
    private fun holdIfBroken(failure: LlmFailure) {
        val until = Instant.now(clock).plus(hold.reprobeAfter)

        if (failure.holdsModel) {
            val applied = modelHold.updateAndGet { current ->
                if (current == null || until.isAfter(current.until)) LlmHold(failure.code, until) else current
            }
            log.warn("LLM model is on hold: model={}, code={}, until={}", model.qualifiedCode, failure.code.code, applied?.until)
        }
        if (failure.holdsProvider) {
            val applied = providerHolds.hold(model.provider, failure.code, until)
            log.warn(
                "LLM provider is on hold: provider={}, model={}, code={}, until={}",
                model.provider.code,
                model.qualifiedCode,
                failure.code.code,
                applied.until
            )
        }
    }

    private fun notPermitted(reason: String): LlmProviderException {
        return LlmProviderException(
            LlmFailure(
                code = LlmFailureCode.LLM_NOT_PERMITTED,
                provider = model.provider,
                message = "${LlmFailureCode.LLM_NOT_PERMITTED.defaultMessage}: $reason"
            )
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(GuardedLlmModel::class.java)
        const val NOT_PERMITTED = "not-permitted"
    }
}
