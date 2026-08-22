package me.rgunny.kachi.ai.domain.outbox

import java.time.Duration
import kotlin.math.pow
import kotlin.random.Random

/**
 * outbox 발행 재시도 한도와 backoff 간격.
 *
 * 재시도 구동은 relay tick이 맡고 이 정책은 "다음에 언제 다시 볼지"만 계산한다. in-process 재시도 루프는 두지 않는다.
 * 같은 장애로 한 tick에 실패한 행들이 다음 tick에 한꺼번에 몰리지 않도록 jitter를 적용한다.
 */
class AiOutboxRetryPolicy(
    val maxAttempts: Int,
    val baseDelay: Duration,
    val maxDelay: Duration,
    val multiplier: Double = DEFAULT_MULTIPLIER,
    val jitterRatio: Double = DEFAULT_JITTER_RATIO,
    private val random: Random = Random.Default
) {
    init {
        require(maxAttempts >= 1) { "outbox maxAttempts는 1 이상이어야 합니다" }
        require(!baseDelay.isNegative && !baseDelay.isZero) { "outbox baseDelay는 양수여야 합니다" }
        require(!maxDelay.isNegative && !maxDelay.isZero) { "outbox maxDelay는 양수여야 합니다" }
        require(maxDelay >= baseDelay) { "outbox maxDelay는 baseDelay 이상이어야 합니다" }
        require(multiplier >= 1.0) { "outbox multiplier는 1 이상이어야 합니다" }
        require(jitterRatio in 0.0..1.0) { "outbox jitterRatio는 0과 1 사이여야 합니다" }
    }

    fun exhausted(attempts: Int): Boolean {
        return attempts >= maxAttempts
    }

    /**
     * n번째 시도 뒤의 대기 시간. 지수는 overflow를 피하려고 30에서 자르고, 결과는 maxDelay로 막은 뒤 분산시킨다.
     */
    fun backoff(attempts: Int): Duration {
        require(attempts >= 1) { "outbox backoff는 1회 이상 시도한 뒤에만 계산할 수 있습니다" }

        val exponent = (attempts - 1).coerceAtMost(MAX_EXPONENT)
        val multiplied = baseDelay.toMillis() * multiplier.pow(exponent)
        val capped = multiplied.toLong().coerceAtMost(maxDelay.toMillis())

        return Duration.ofMillis(applyJitter(capped).coerceAtLeast(MIN_DELAY_MILLIS))
    }

    /**
     * 계산된 대기 시간을 기준으로 ± jitterRatio 범위 안에서 분산한다.
     */
    private fun applyJitter(delayMillis: Long): Long {
        if (jitterRatio == 0.0) {
            return delayMillis
        }

        val jitter = (delayMillis * jitterRatio).toLong()
        if (jitter == 0L) {
            return delayMillis
        }

        return delayMillis + random.nextLong(-jitter, jitter + 1)
    }

    private companion object {
        const val DEFAULT_MULTIPLIER = 2.0
        const val DEFAULT_JITTER_RATIO = 0.2
        // pow 연산과 Duration 변환이 비정상적으로 커지는 것을 막는다.
        const val MAX_EXPONENT = 30
        const val MIN_DELAY_MILLIS = 1L
    }
}
