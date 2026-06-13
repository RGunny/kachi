package me.rgunny.kachi.notification.retry

import java.time.Duration

/**
 * 실패를 재시도할지 결정하고 다음 대기 시간을 계산한다.
 *
 * retryable 여부는 error code가 아니라 실패 원천과 분류를 기준으로 판단한다.
 */
class RetryPolicy(
    val maxAttempts: Int,
    private val backoffPolicy: BackoffPolicy,
    private val retryableCategories: Set<FailureCategory> = DEFAULT_RETRYABLE_CATEGORIES,
    private val retryableSources: Set<FailureSource> = DEFAULT_RETRYABLE_SOURCES,
) {

    constructor(
        maxAttempts: Int,
        baseDelay: Duration,
        maxDelay: Duration,
    ) : this(
        maxAttempts = maxAttempts,
        backoffPolicy = ExponentialBackoffPolicy(
            baseDelay = baseDelay,
            maxDelay = maxDelay,
        ),
    )

    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(retryableCategories.isNotEmpty()) { "retryableCategories must not be empty" }
        require(retryableSources.isNotEmpty()) { "retryableSources must not be empty" }
    }

    /**
     * 실패 정보와 현재 시도 횟수로 다음 처리 방향을 결정한다.
     */
    fun decide(failure: RetryFailure, attempts: Int): RetryDecision {
        require(attempts > 0) { "attempts must be positive" }

        // 재시도 대상이 아니면 한도와 무관하게 즉시 포기한다.
        if (!retryable(failure)) {
            return RetryDecision.GiveUp(failure = failure, exhausted = false)
        }
        // 재시도 대상이어도 한도를 넘으면 DEAD 처리 후보가 된다.
        if (exhausted(attempts)) {
            return RetryDecision.GiveUp(failure = failure, exhausted = true)
        }
        return RetryDecision.Retry(
            delay = backoff(attempts),
            failure = failure,
        )
    }

    /**
     * 정책상 자동 재시도 대상인지 판단한다.
     */
    fun retryable(failure: RetryFailure): Boolean {
        return failure.category in retryableCategories && failure.source in retryableSources
    }

    /**
     * 재시도 한도에 도달했는지 판단한다.
     */
    fun exhausted(attempts: Int): Boolean {
        return attempts >= maxAttempts
    }

    /**
     * 현재 시도 횟수에 대한 backoff 대기 시간을 계산한다.
     */
    fun backoff(attempts: Int): Duration {
        return backoffPolicy.delay(attempts)
    }

    private companion object {
        val DEFAULT_RETRYABLE_CATEGORIES = setOf(
            FailureCategory.TIMEOUT,
            FailureCategory.RATE_LIMITED,
            FailureCategory.TRANSIENT_ERROR,
            FailureCategory.CONFLICT,
            FailureCategory.UNKNOWN,
        )
        val DEFAULT_RETRYABLE_SOURCES = setOf(
            FailureSource.VENDOR,
            FailureSource.BROKER,
            FailureSource.DATABASE,
            FailureSource.NETWORK,
            FailureSource.APPLICATION,
        )
    }
}
