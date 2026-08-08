package me.rgunny.kachi.notification.application.port.outbound.sender

import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.outbound.sender.model.SendNotificationResult
import me.rgunny.kachi.notification.domain.NotificationChannel

/**
 * 채널별 알림 발송 port.
 */
interface NotificationSender {

    fun supports(channel: NotificationChannel): Boolean

    suspend fun send(command: SendNotificationCommand): SendNotificationResult
}
