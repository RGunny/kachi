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
    /**
     * 외부 채널 sender 호출까지 진행했는지 여부.
     *
     * 중복 메시지나 이미 종결된 알림처럼 상태 확인 후 skip한 경우에는 false다.
     */
    val dispatchAttempted: Boolean,
    /**
     * dispatch command 처리를 마치고 이 결과를 만든 시각.
     */
    val handledAt: Instant
)
