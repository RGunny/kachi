package me.rgunny.kachi.notification.application.port.inbound

import me.rgunny.kachi.notification.application.port.dto.dlt.PersistNotificationDltMessageCommand
import me.rgunny.kachi.notification.application.port.dto.dlt.PersistNotificationDltMessageResult

/**
 * notification dispatch DLT 메시지 영속화 use case.
 */
interface PersistNotificationDltMessageUseCase {

    suspend fun persist(command: PersistNotificationDltMessageCommand): PersistNotificationDltMessageResult
}
