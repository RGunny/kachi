package me.rgunny.kachi.notification.routing.application.service

import me.rgunny.kachi.notification.contract.NotificationChannel

/**
 * 알림 라우팅 정책.
 *
 * adminRecipientRefs가 비어 있으면 관리자 알림은 대상 없이 완료된다.
 */
data class RoutingPolicy(
    val requester: String,
    val adminRecipientRefs: Map<NotificationChannel, String>,
) {
    init {
        require(requester.isNotBlank()) { "requester must not be blank" }
        adminRecipientRefs.forEach { (channel, recipientRef) ->
            require(recipientRef.isNotBlank()) { "admin recipientRef for $channel must not be blank" }
        }
    }
}
