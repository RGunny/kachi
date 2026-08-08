package me.rgunny.kachi.notification.application.port.inbound.dlt

import me.rgunny.kachi.notification.application.port.inbound.dlt.model.DiscardNotificationDltMessageCommand
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.DiscardNotificationDltMessageResult
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageDetail
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageAdminResult
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.NotificationDltMessageQuery
import me.rgunny.kachi.notification.domain.NotificationDltMessageId

/**
 * Notification dispatch DLT 메시지 운영 use case.
 */
interface NotificationDltMessageAdminUseCase {

    suspend fun find(query: NotificationDltMessageQuery): NotificationDltMessageAdminResult

    suspend fun get(messageId: NotificationDltMessageId): NotificationDltMessageDetail

    suspend fun discard(command: DiscardNotificationDltMessageCommand): DiscardNotificationDltMessageResult
}
