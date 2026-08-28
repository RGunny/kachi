package me.rgunny.kachi.notification.routing.application.port.outbound.recipient.model

import me.rgunny.kachi.notification.contract.NotificationChannel

/**
 * 수신자 1명의 채널 1개. recipientId는 사용자 id이며 주소는 갖지 않는다.
 */
data class Recipient(
    val recipientId: String,
    val channel: NotificationChannel,
) {
    init {
        require(recipientId.isNotBlank()) { "recipientId must not be blank" }
    }
}
