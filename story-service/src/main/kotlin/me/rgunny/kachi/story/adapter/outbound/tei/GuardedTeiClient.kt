package me.rgunny.kachi.story.adapter.outbound.tei

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiInfoResponse
import me.rgunny.kachi.story.application.exception.InferenceException
import me.rgunny.kachi.story.domain.inference.InferenceFailure
import me.rgunny.kachi.story.domain.inference.InferenceFailureCode
import me.rgunny.kachi.story.domain.inference.InferenceTarget
import org.slf4j.LoggerFactory

/**
 * 추론 서버 하나의 호출 가능 여부를 서킷 브레이커로 관리하는 데코레이터.
 *
 * 회로가 열려 있으면 위임 대상을 부르지 않고 [InferenceFailureCode.INFERENCE_NOT_PERMITTED]로 실패한다.
 * 실패 분류와 재시도는 하지 않는다.
 * [info]는 회로를 거치지 않는다.
 */
class GuardedTeiClient(
    private val delegate: TeiClient,
    val target: InferenceTarget,
    private val circuitBreaker: CircuitBreaker
) : TeiClient {

    init {
        circuitBreaker.eventPublisher.onStateTransition { event ->
            val transition = "${event.stateTransition.fromState} -> ${event.stateTransition.toState}"

            if (event.stateTransition.toState == CircuitBreaker.State.OPEN) {
                log.warn("TEI circuit breaker opened: target={}, transition={}", target, transition)
            } else {
                log.info("TEI circuit breaker changed: target={}, transition={}", target, transition)
            }
        }
    }

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        return guarded { delegate.embed(texts) }
    }

    override suspend fun rerank(query: String, texts: List<String>): List<Double> {
        return guarded { delegate.rerank(query, texts) }
    }

    override suspend fun info(): TeiInfoResponse = delegate.info()

    fun status(): TeiClientStatus {
        val metrics = circuitBreaker.metrics

        return TeiClientStatus(
            target = target,
            circuitBreakerState = circuitBreaker.state.name,
            failureRate = metrics.failureRate,
            slowCallRate = metrics.slowCallRate,
            bufferedCalls = metrics.numberOfBufferedCalls,
            successfulCalls = metrics.numberOfSuccessfulCalls,
            failedCalls = metrics.numberOfFailedCalls,
            notPermittedCalls = metrics.numberOfNotPermittedCalls
        )
    }

    /** 운영자가 원인 해소를 확인한 뒤 회로를 닫는다. */
    fun reset() {
        if (circuitBreaker.state != CircuitBreaker.State.CLOSED) {
            circuitBreaker.transitionToClosedState()
        }
    }

    /**
     * 호출을 막는 이유.
     *
     * 막을 이유가 없으면 null이다.
     */
    fun blockedReason(): String? {
        return when (circuitBreaker.state) {
            CircuitBreaker.State.OPEN -> "open"
            CircuitBreaker.State.FORCED_OPEN -> "forced-open"
            else -> null
        }
    }

    /** 취소는 성공도 실패도 아니라서 permission을 돌려주고 집계에서 뺀다. */
    private suspend fun <T : Any> guarded(call: suspend () -> T): T {
        // permission 획득이 OPEN에서 HALF_OPEN으로 넘어가는 계기다.
        if (!circuitBreaker.tryAcquirePermission()) {
            throw notPermitted(blockedReason() ?: NOT_PERMITTED)
        }

        val start = circuitBreaker.currentTimestamp
        val timestampUnit = circuitBreaker.timestampUnit

        return try {
            val result = call()
            circuitBreaker.onResult(circuitBreaker.currentTimestamp - start, timestampUnit, result)
            result
        } catch (cancellation: CancellationException) {
            circuitBreaker.releasePermission()
            throw cancellation
        } catch (error: Throwable) {
            circuitBreaker.onError(circuitBreaker.currentTimestamp - start, timestampUnit, error)
            throw error
        }
    }

    private fun notPermitted(reason: String): InferenceException {
        return InferenceException(
            InferenceFailure(
                code = InferenceFailureCode.INFERENCE_NOT_PERMITTED,
                target = target,
                message = "${InferenceFailureCode.INFERENCE_NOT_PERMITTED.defaultMessage}: $reason"
            )
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(GuardedTeiClient::class.java)
        const val NOT_PERMITTED = "not-permitted"
    }
}
