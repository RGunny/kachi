package me.rgunny.kachi.notification.application.port.inbound

import me.rgunny.kachi.notification.application.port.dto.dlt.DiscardNotificationDltMessageCommand
import me.rgunny.kachi.notification.application.port.dto.dlt.DiscardNotificationDltMessageResult
import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageDetail
import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageAdminResult
import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageQuery
import me.rgunny.kachi.notification.domain.NotificationDltMessageId

/**
 * Notification dispatch DLT 메시지 운영 use case.
 */
interface NotificationDltMessageAdminUseCase {

    suspend fun find(query: NotificationDltMessageQuery): NotificationDltMessageAdminResult

    suspend fun get(messageId: NotificationDltMessageId): NotificationDltMessageDetail

    suspend fun discard(command: DiscardNotificationDltMessageCommand): DiscardNotificationDltMessageResult
}
