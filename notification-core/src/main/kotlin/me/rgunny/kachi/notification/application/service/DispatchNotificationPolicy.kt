package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.domain.retry.RetryPolicy
import java.time.Duration

/**
 * 알림 발송 실행 정책.
 */
data class DispatchNotificationPolicy(
    val workerId: String,
    val dedupeTtl: Duration,
    val idempotencyKeyTtl: Duration,
    val retryPolicy: RetryPolicy,
    val processingVisibilityTimeout: Duration,
    val recoveryBatchSize: Int,
)
