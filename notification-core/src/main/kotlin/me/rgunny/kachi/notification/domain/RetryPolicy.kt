package me.rgunny.kachi.notification.domain

import java.time.Duration

/**
 * 재시도 횟수와 backoff 계산 정책.
 */
class RetryPolicy(
    val maxAttempts: Int,
    val baseDelay: Duration,
    val maxDelay: Duration,
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(!baseDelay.isZero && !baseDelay.isNegative) { "baseDelay must be positive" }
        require(!maxDelay.isZero && !maxDelay.isNegative) { "maxDelay must be positive" }
    }

    fun exhausted(attempts: Int): Boolean {
        return attempts >= maxAttempts
    }

    fun backoff(attempts: Int): Duration {
        val multiplier = 1L shl (attempts - 1).coerceAtMost(MAX_SHIFT)
        val delay = baseDelay.multipliedBy(multiplier)
        return if (delay > maxDelay) maxDelay else delay
    }

    private companion object {
        const val MAX_SHIFT = 30
    }
}
