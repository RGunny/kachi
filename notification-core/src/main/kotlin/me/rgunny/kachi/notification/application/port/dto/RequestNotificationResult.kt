package me.rgunny.kachi.notification.application.port.dto

import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import java.time.Instant

/**
 * 외부 알림 요청 접수 결과.
 */
data class RequestNotificationResult(
    val notificationId: NotificationId,
    val status: NotificationStatus,
    val duplicated: Boolean,
    val acceptedAt: Instant
)
