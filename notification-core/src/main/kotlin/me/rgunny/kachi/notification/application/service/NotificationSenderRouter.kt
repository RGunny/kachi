package me.rgunny.kachi.notification.application.service

import me.rgunny.kachi.notification.application.port.outbound.sender.NotificationSender
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.exception.sender.MultipleNotificationSendersFoundException
import me.rgunny.kachi.notification.exception.sender.NotificationSenderNotFoundException

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
            0 -> throw NotificationSenderNotFoundException(channel)
            else -> throw MultipleNotificationSendersFoundException(channel)
        }
    }
}
