package me.rgunny.kachi.notification.application.port.dto

import java.time.Instant

/**
 * 알림 dispatch 이벤트 발행 결과.
 */
data class PublishNotificationDispatchResult(
    val processed: Int,
    val published: Int,
    val failed: Int,
    val occurredAt: Instant
)
