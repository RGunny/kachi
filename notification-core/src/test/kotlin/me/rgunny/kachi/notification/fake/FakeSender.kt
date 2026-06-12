package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.dto.SendNotificationCommand
import me.rgunny.kachi.notification.application.port.dto.SendNotificationResult
import me.rgunny.kachi.notification.application.port.outbound.NotificationSender
import me.rgunny.kachi.notification.domain.NotificationChannel

class FakeSender(
    private val channel: NotificationChannel = NotificationChannel.SLACK,
    private val result: SendNotificationResult = SendNotificationResult.Success(),
    private val failure: RuntimeException? = null,
) : NotificationSender {
    val commands = mutableListOf<SendNotificationCommand>()
    var sendCount = 0

    override fun supports(channel: NotificationChannel): Boolean {
        return this.channel == channel
    }

    override suspend fun send(command: SendNotificationCommand): SendNotificationResult {
        sendCount += 1
        commands += command
        failure?.let { throw it }
        return result
    }
}
