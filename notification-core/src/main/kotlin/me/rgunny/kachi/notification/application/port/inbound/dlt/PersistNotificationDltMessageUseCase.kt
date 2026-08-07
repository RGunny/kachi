package me.rgunny.kachi.notification.application.port.inbound.dlt

import me.rgunny.kachi.notification.application.port.inbound.dlt.model.PersistNotificationDltMessageCommand
import me.rgunny.kachi.notification.application.port.inbound.dlt.model.PersistNotificationDltMessageResult

/**
 * notification dispatch DLT 메시지 영속화 use case.
 */
interface PersistNotificationDltMessageUseCase {

    suspend fun persist(command: PersistNotificationDltMessageCommand): PersistNotificationDltMessageResult
}
