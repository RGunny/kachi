package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.domain.NotificationChannel

/**
 * 알림 라우팅 정책.
 *
 * adminRecipients의 값은 주소가 아니라 수신처 참조다. 비어 있으면 관리자 알림은 대상 없이 완료된다.
 */
data class RoutingPolicy(
    val requester: String,
    val adminRecipients: Map<NotificationChannel, String>,
) {
    init {
        require(requester.isNotBlank()) { "requester must not be blank" }
        adminRecipients.forEach { (channel, recipient) ->
            require(recipient.isNotBlank()) { "admin recipient for $channel must not be blank" }
        }
    }
}
