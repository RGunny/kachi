package me.rgunny.kachi.notification.application.service

import java.time.Duration

/**
 * 알림 요청 접수 정책.
 */
data class RequestNotificationPolicy(
    val dedupeTtl: Duration,
    val dispatchTopic: String,
)
