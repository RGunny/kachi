package me.rgunny.kachi.notification.routing.application.port.outbound.messaging.model

import me.rgunny.kachi.notification.contract.NotificationChannel

/**
 * 라우팅이 만든 알림 요청 1건. 주소는 싣지 않고 수신처 참조만 가진다.
 */
data class NotificationRequest(
    val requestId: String,
    val requester: String,
    val channel: NotificationChannel,
    val recipientRef: String,
    val message: String,
    val origin: NotificationRequestOrigin,
) {
    init {
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        require(requester.isNotBlank()) { "requester must not be blank" }
        require(recipientRef.isNotBlank()) { "recipientRef must not be blank" }
        require(message.isNotBlank()) { "message must not be blank" }
    }
}
