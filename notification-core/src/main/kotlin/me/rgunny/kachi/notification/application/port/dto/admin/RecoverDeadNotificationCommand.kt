package me.rgunny.kachi.notification.application.port.dto.admin

import me.rgunny.kachi.notification.domain.NotificationId

/**
 * 운영자가 DEAD notification을 다시 dispatch 흐름에 올리기 위한 command.
 */
data class RecoverDeadNotificationCommand(
    val notificationId: NotificationId,
    val reason: String,
)
