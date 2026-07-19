package me.rgunny.kachi.notification.application.port.dto.dlt

import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import java.time.Instant

/**
 * DLT 메시지 폐기 처리 결과.
 */
data class DiscardNotificationDltMessageResult(
    val messageId: NotificationDltMessageId,
    val status: NotificationDltMessageStatus,
    val discardedAt: Instant,
    val discardReason: String,
)
