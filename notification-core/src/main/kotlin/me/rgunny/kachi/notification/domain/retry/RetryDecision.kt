package me.rgunny.kachi.notification.domain.retry

import java.time.Duration

sealed interface RetryDecision {

    data class Retry(
        val delay: Duration,
        val failure: RetryFailure,
    ) : RetryDecision

    data class GiveUp(
        val failure: RetryFailure,
        val exhausted: Boolean,
    ) : RetryDecision
}
