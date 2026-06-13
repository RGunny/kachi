package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.retry.RetryPolicy
import java.time.Duration

/**
 * outbox 발행 정책.
 */
data class OutboxPublishPolicy(
    val batchSize: Int,
    val publisherId: String,
    val retryPolicy: RetryPolicy,
    val publishingVisibilityTimeout: Duration,
)
