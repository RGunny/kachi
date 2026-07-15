package me.rgunny.kachi.notification.application.port.inbound

import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageAdminResult
import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageQuery

/**
 * Notification dispatch DLT 메시지 운영 use case.
 */
interface NotificationDltMessageAdminUseCase {

    suspend fun find(query: NotificationDltMessageQuery): NotificationDltMessageAdminResult
}
