package me.rgunny.kachi.notification.worker.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * notification-worker 인스턴스 식별 설정.
 */
@ConfigurationProperties(prefix = "kachi.notification.worker")
data class NotificationWorkerProperties(
    /**
     * DB claim의 claimedBy에 기록되는 worker 식별자.
     */
    val workerId: String,
)
