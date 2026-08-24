package me.rgunny.kachi.ai.adapter.outbound.llm

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.config.LlmFailoverProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailure
import me.rgunny.kachi.ai.domain.llm.LlmFailureCategory
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * provider 하나의 호출 가능 여부를 관리하는 데코레이터.
 *
 * 차단 장치는 둘이다. 연속 실패로 열리는 circuit breaker와, rate limit 응답이 지시한 cooldown이다.
 * 하나라도 걸리면 위임 대상을 호출하지 않고 [LlmFailureCode.LLM_PROVIDER_UNAVAILABLE]로 실패한다.
 *
 * 실패 분류는 위임 대상이 끝냈으므로 여기서 다시 하지 않고 [LlmFailure]를 그대로 소비한다.
 * 재시도도 하지 않는다. 재시도 구동은 다음 tick의 몫이다(ADR 021).
 */
class GuardedLlmProvider(
    private val delegate: LlmProviderPort,
    override val provider: LlmProviderName,
    private val circuitBreaker: CircuitBreaker,
    private val failover: LlmFailoverProperties,
    private val clock: Clock
) : LlmProviderCandidate {

    /** scheduler와 internal API가 동시에 들어올 수 있어 갱신을 원자적으로 처리한다. */
    private val cooldownUntil = AtomicReference<Instant?>(null)

    init {
        // 상태 전이를 남기지 않으면 특정 provider가 한동안 호출되지 않은 이유를 운영자가 알 수 없다.
        circuitBreaker.eventPublisher.onStateTransition { event ->
            val transition = "${event.stateTransition.fromState} -> ${event.stateTransition.toState}"

            if (event.stateTransition.toState == CircuitBreaker.State.OPEN) {
                log.warn("LLM provider circuit breaker opened: provider={}, transition={}", provider.value, transition)
            } else {
                log.info("LLM provider circuit breaker changed: provider={}, transition={}", provider.value, transition)
            }
        }
    }

    /**
     * 위임 대상의 실행 단위를 그대로 넘기면 그 객체의 호출이 차단을 우회하므로 감싼 것으로 바꿔 돌려준다.
     */
    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        return GuardedPreparedNewsSummary(plan = delegate.prepareNewsSummary().plan, provider = this)
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
     * 호출을 circuit breaker 집계와 cooldown 판정으로 감싼다.
     *
     * 취소는 성공도 실패도 아니라서 permission을 돌려주고 집계에서 뺀다. 라이브러리 wrapper는 취소를
     * 성공으로 세고 그 동작에 끼어들 지점이 없어, 상태 전이 API를 직접 호출한다.
     */
    private suspend fun <T : Any> guarded(call: suspend () -> T): T {
        val now = Instant.now(clock)

        // 1. 쉬는 중인 provider는 회로에 닿기 전에 막는다. 이 차단은 집계에 넣지 않는다.
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
     * rate limit 응답을 받으면 provider가 지시한 시간만큼 쉰다.
     *
     * 헤더가 없어도 기본값만큼은 쉰다. 0으로 두면 같은 실행에서 같은 provider를 다시 골라 같은 응답을 받는다.
     * 이미 쉬는 중이면 더 늦은 시각만 반영한다. 짧은 지시로 대기를 앞당기면 앞선 지시를 어기는 것이 된다.
     */
    private fun holdIfRateLimited(failure: LlmFailure) {
        if (failure.category != LlmFailureCategory.RATE_LIMITED) {
            return
        }

        val cooldown = failure.retryAfterMillis
            ?.let { minOf(Duration.ofMillis(it), failover.maxCooldown) }
            ?: failover.defaultCooldown
        val until = Instant.now(clock).plus(cooldown)
        val applied = cooldownUntil.updateAndGet { current ->
            if (current == null || until.isAfter(current)) until else current
        }

        log.warn(
            "LLM provider is cooling down after rate limit: provider={}, until={}, source={}",
            provider.value,
            applied,
            if (failure.retryAfterMillis == null) "default" else "retry-after"
        )
    }

    private fun unavailable(reason: String): LlmProviderException {
        return LlmProviderException(
            LlmFailure(
                code = LlmFailureCode.LLM_PROVIDER_UNAVAILABLE,
                provider = provider,
                message = "${LlmFailureCode.LLM_PROVIDER_UNAVAILABLE.defaultMessage}: $reason"
            )
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(GuardedLlmProvider::class.java)
        const val NOT_PERMITTED = "not-permitted"
    }
}
