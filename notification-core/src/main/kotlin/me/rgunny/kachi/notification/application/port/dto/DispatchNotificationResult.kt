package me.rgunny.kachi.notification.application.port.dto

import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import java.time.Instant

/**
 * 알림 발송 실행 결과.
 */
data class DispatchNotificationResult(
    val notificationId: NotificationId,
    val status: NotificationStatus,
    val duplicated: Boolean,
    val attempted: Boolean,
    val occurredAt: Instant
)
