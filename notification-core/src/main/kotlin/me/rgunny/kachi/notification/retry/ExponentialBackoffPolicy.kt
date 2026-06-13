package me.rgunny.kachi.notification.retry

import java.time.Duration
import kotlin.math.pow
import kotlin.random.Random

/**
 * 재시도 간격을 지수적으로 늘리는 backoff 정책.
 *
 * 같은 장애에 여러 알림이 동시에 재시도되는 일을 줄이기 위해 jitter를 선택적으로 적용한다.
 */
class ExponentialBackoffPolicy(
    val baseDelay: Duration,
    val maxDelay: Duration,
    val multiplier: Double = DEFAULT_MULTIPLIER,
    val jitterRatio: Double = DEFAULT_JITTER_RATIO,
    private val random: Random = Random.Default,
) : BackoffPolicy {

    init {
        require(!baseDelay.isZero && !baseDelay.isNegative) { "baseDelay must be positive" }
        require(!maxDelay.isZero && !maxDelay.isNegative) { "maxDelay must be positive" }
        require(multiplier >= 1.0) { "multiplier must be greater than or equal to 1" }
        require(jitterRatio in 0.0..1.0) { "jitterRatio must be between 0 and 1" }
    }

    /**
     * 현재 시도 횟수에 해당하는 대기 시간을 반환한다.
     */
    override fun delay(attempts: Int): Duration {
        require(attempts > 0) { "attempts must be positive" }

        // attempts=1이면 baseDelay, 이후 multiplier 배수로 증가한다.
        val exponent = (attempts - 1).coerceAtMost(MAX_EXPONENT)
        val multiplied = baseDelay.toMillis() * multiplier.pow(exponent)
        val capped = multiplied.toLong().coerceAtMost(maxDelay.toMillis())
        val jittered = applyJitter(capped)
        return Duration.ofMillis(jittered.coerceAtLeast(MIN_DELAY_MILLIS))
    }

    /**
     * 계산된 대기 시간에 임의 분산값을 적용한다.
     */
    private fun applyJitter(delayMillis: Long): Long {
        if (jitterRatio == 0.0) {
            return delayMillis
        }

        // delayMillis 기준으로 +/- jitterRatio 범위 안에서 분산한다.
        val jitter = (delayMillis * jitterRatio).toLong()
        if (jitter == 0L) {
            return delayMillis
        }

        return delayMillis + random.nextLong(-jitter, jitter + 1)
    }

    private companion object {
        const val DEFAULT_MULTIPLIER = 2.0
        const val DEFAULT_JITTER_RATIO = 0.0
        // pow 연산과 Duration 변환이 비정상적으로 커지는 것을 막는다.
        const val MAX_EXPONENT = 30
        const val MIN_DELAY_MILLIS = 1L
    }
}
