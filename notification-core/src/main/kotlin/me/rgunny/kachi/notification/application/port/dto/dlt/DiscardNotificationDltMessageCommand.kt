package me.rgunny.kachi.notification.application.port.dto.dlt

import me.rgunny.kachi.notification.domain.NotificationDltMessageId

/**
 * 운영자가 재처리하지 않을 DLT 메시지를 DISCARDED로 폐기하기 위한 command.
 */
data class DiscardNotificationDltMessageCommand(
    val messageId: NotificationDltMessageId,
    val reason: String,
)
