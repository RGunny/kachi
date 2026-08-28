package me.rgunny.kachi.notification.application.port.inbound.request.model

import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationOrigin

/**
 * 외부 알림 요청 접수 입력 모델.
 *
 * HTTP request body 또는 Kafka `notification.requested` event를
 * notification-service adapter에서 변환해 application layer로 전달한다.
 */
data class RequestNotificationCommand(
    val requestId: String,
    val requester: String,
    val channel: NotificationChannel,
    val recipientId: String,
    val message: String,
    val origin: NotificationOrigin,
)
