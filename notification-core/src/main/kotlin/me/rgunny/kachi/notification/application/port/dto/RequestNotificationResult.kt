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
    /**
     * 알림 요청이 REQUESTED 상태로 최초 접수된 시각.
     *
     * 중복 요청이면 기존 알림의 최초 접수 시각을 반환한다.
     */
    val acceptedAt: Instant
)
