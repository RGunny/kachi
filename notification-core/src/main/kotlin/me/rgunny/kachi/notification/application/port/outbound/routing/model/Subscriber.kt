package me.rgunny.kachi.notification.application.port.outbound.routing.model

import me.rgunny.kachi.notification.domain.NotificationChannel

/**
 * 키워드 구독자 1명의 채널 1개. recipientRef는 주소가 아니라 주소를 가리키는 참조다.
 */
data class Subscriber(
    val userId: String,
    val channel: NotificationChannel,
    val recipientRef: String,
) {
    init {
        require(userId.isNotBlank()) { "userId must not be blank" }
        require(recipientRef.isNotBlank()) { "recipientRef must not be blank" }
    }
}
