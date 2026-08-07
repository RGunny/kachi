package me.rgunny.kachi.notification.application.port.inbound.dlt.model

import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus

/**
 * DLT 메시지 영속화 결과.
 */
data class PersistNotificationDltMessageResult(
    val messageId: NotificationDltMessageId,
    val status: NotificationDltMessageStatus,
)
