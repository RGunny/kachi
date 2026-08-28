package me.rgunny.kachi.notification.service.adapter.inbound.web

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import me.rgunny.kachi.notification.application.port.inbound.request.model.RequestNotificationCommand
import me.rgunny.kachi.notification.domain.NotificationChannel
import me.rgunny.kachi.notification.domain.NotificationOrigin

data class NotificationRequest(
    @field:NotBlank(message = "requestId는 필수입니다")
    @field:Size(max = 100, message = "requestId는 100자 이하여야 합니다")
    val requestId: String,

    @field:NotBlank(message = "requester는 필수입니다")
    @field:Size(max = 100, message = "requester는 100자 이하여야 합니다")
    val requester: String,

    val channel: NotificationChannel,

    @field:NotBlank(message = "recipientId는 필수입니다")
    @field:Size(max = 64, message = "recipientId는 64자 이하여야 합니다")
    val recipientId: String,

    @field:NotBlank(message = "message는 필수입니다")
    @field:Size(max = 4000, message = "message는 4000자 이하여야 합니다")
    val message: String,
) {
    fun toCommand(): RequestNotificationCommand {
        return RequestNotificationCommand(
            requestId = requestId,
            requester = requester,
            channel = channel,
            recipientId = recipientId,
            message = message,
            origin = NotificationOrigin.NONE,
        )
    }
}
