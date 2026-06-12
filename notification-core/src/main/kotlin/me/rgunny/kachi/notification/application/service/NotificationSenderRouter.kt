package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.outbound.NotificationSender
import me.rgunny.kachi.notification.domain.NotificationChannel

/**
 * 채널별 NotificationSender 선택기.
 */
class NotificationSenderRouter(
    private val senders: List<NotificationSender>,
) {

    fun route(channel: NotificationChannel): NotificationSender {
        val matchedSenders = senders.filter { it.supports(channel) }

        return when (matchedSenders.size) {
            1 -> matchedSenders.first()
            0 -> throw IllegalArgumentException("notification sender not found. channel=$channel")
            else -> throw IllegalStateException("multiple notification senders found. channel=$channel")
        }
    }
}
