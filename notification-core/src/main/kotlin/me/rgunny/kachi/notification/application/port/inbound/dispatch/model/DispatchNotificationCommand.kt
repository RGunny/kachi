package me.rgunny.kachi.notification.application.port.inbound.dispatch.model

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId

/**
 * 알림 발송 실행 입력 모델.
 *
 * Kafka `notification.dispatch` event를 notification-worker adapter에서 변환해
 * application layer로 전달한다.
 */
data class DispatchNotificationCommand(
    val notificationId: NotificationId,
    val requestId: String,
    val channel: NotificationChannel,
    val recipient: String,
    val message: String
)
