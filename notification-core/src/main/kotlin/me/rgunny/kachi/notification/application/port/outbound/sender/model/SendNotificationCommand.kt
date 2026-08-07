package me.rgunny.kachi.notification.application.port.outbound.sender.model

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId

/**
 * 외부 채널 발송 입력 모델.
 *
 * application service가 Notification aggregate와 vendor idempotency key를 조합해
 * sender adapter로 전달한다.
 */
data class SendNotificationCommand(
    val notificationId: NotificationId,
    val channel: NotificationChannel,
    val recipient: String,
    val message: String,
    val idempotencyKey: String,
)
