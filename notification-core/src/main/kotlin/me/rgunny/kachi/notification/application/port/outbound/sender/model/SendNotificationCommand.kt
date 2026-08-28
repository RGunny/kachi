package me.rgunny.kachi.notification.application.port.outbound.sender.model

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationId

/**
 * 외부 채널 발송 입력 모델.
 *
 * application service가 claim한 알림, 조회한 수신 주소, vendor idempotency key를 조합해 sender adapter로 전달한다.
 * sender는 "어디로"만 알면 되므로 수신자 식별자는 싣지 않는다.
 */
data class SendNotificationCommand(
    val notificationId: NotificationId,
    val channel: NotificationChannel,
    /** 채널별 형식의 수신 주소 문자열 하나(webhook URL, chat id). */
    val address: String,
    val message: String,
    val idempotencyKey: String,
)
